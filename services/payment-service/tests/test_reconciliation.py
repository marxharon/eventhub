"""Real service/repository and SQLite persistence; HTTP is mocked.

Run from payment-service: python -m unittest discover -s tests -v
PostgreSQL row-lock concurrency still requires an integration environment.
"""
import tempfile
import unittest
from datetime import datetime, timedelta
from pathlib import Path
from unittest.mock import patch
from fastapi import HTTPException
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
from app.config.database import Base
from app.models.payment_model import PaymentModel
from app.repositories.payment_repository import PaymentRepository
from app.services.payment_service import PaymentService
from app.services.reconciliation import reconcile_once


class ReconciliationTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.engine = create_engine(f"sqlite:///{Path(self.tmp.name) / 'payments.db'}")
        Base.metadata.create_all(self.engine)
        self.sessions = sessionmaker(bind=self.engine)
        with self.engine.begin() as conn:
            conn.execute(text("CREATE TABLE activities (id BIGINT PRIMARY KEY)"))
            conn.execute(text("CREATE TABLE registrations (id BIGINT PRIMARY KEY, activity_id BIGINT, status TEXT, expires_at TIMESTAMP)"))
            conn.execute(text("INSERT INTO activities VALUES (1)"))
            conn.execute(text("INSERT INTO registrations VALUES (1, 1, 'PENDING_PAYMENT', :expiry)"),
                         {'expiry': datetime.now() + timedelta(minutes=15)})
        with self.sessions() as db:
            db.add(PaymentModel(id=1, registration_id=1, user_id=2, amount=50, status='PENDING', provider_ref='SIM-TEST'))
            db.commit()
        # SQLite has no FOR UPDATE. Only substitute the lock acquisition, not persistence/delivery.
        self.lock = patch.object(PaymentRepository, 'lock_for_transition', self.sqlite_lock)
        self.lock.start()
        self.notification = patch('app.services.payment_service.DownstreamClient.send_notification', return_value=True)
        self.notification.start()
        self.email = patch.object(PaymentRepository, 'find_user_email', return_value=None)
        self.email.start()

    @staticmethod
    def sqlite_lock(repository, payment_id):
        payment = repository.find_by_id(payment_id)
        if payment is None:
            return None, None
        row = repository.db.execute(text("SELECT status, expires_at FROM registrations WHERE id = :id"),
                                    {'id': payment.registration_id}).first()
        from types import SimpleNamespace
        expiry = datetime.fromisoformat(row.expires_at) if isinstance(row.expires_at, str) else row.expires_at
        return payment, SimpleNamespace(status=row.status, expires_at=expiry)

    def tearDown(self):
        self.email.stop()
        self.notification.stop()
        self.lock.stop()
        self.engine.dispose()
        self.tmp.cleanup()

    def callback(self, registration_id, payment_id, status):
        target = {'APPROVED': 'CONFIRMED', 'REFUSED': 'REFUSED', 'EXPIRED': 'EXPIRED'}[status]
        with self.engine.begin() as conn:
            conn.execute(text("UPDATE registrations SET status = :status WHERE id = :id"),
                         {'status': target, 'id': registration_id})
        return True

    def states(self):
        with self.sessions() as db:
            payment = db.get(PaymentModel, 1)
            reg = db.execute(text('SELECT status FROM registrations WHERE id = 1')).scalar()
            return payment.status, payment.registration_sync_pending, reg

    def confirm(self):
        with self.sessions() as db:
            return PaymentService(db).confirm(1, {'role': 'PARTICIPANT', 'userId': 2})

    def test_lost_response_preserves_approval_and_recovers_after_restart(self):
        def lost_response(*args):
            self.callback(*args)  # remote commit succeeds but no response reaches the caller
            return False
        with patch('app.services.payment_service.DownstreamClient.notify_registration', side_effect=lost_response):
            response = self.confirm()
        self.assertEqual(response.status, 'APPROVED')
        self.assertTrue(response.registrationSyncPending)
        self.assertEqual(self.states(), ('APPROVED', True, 'CONFIRMED'))
        # New sessions in the real worker represent process restart; durable intent survives.
        with patch('app.services.reconciliation.SessionLocal', self.sessions), patch(
            'app.services.payment_service.DownstreamClient.notify_registration', side_effect=self.callback
        ):
            reconcile_once()
        self.assertEqual(self.states(), ('APPROVED', False, 'CONFIRMED'))

    def test_unavailable_before_delivery_is_retried(self):
        with patch('app.services.payment_service.DownstreamClient.notify_registration', return_value=False):
            self.confirm()
        self.assertEqual(self.states(), ('APPROVED', True, 'PENDING_PAYMENT'))
        with patch('app.services.reconciliation.SessionLocal', self.sessions), patch(
            'app.services.payment_service.DownstreamClient.notify_registration', side_effect=self.callback
        ):
            reconcile_once()
        self.assertEqual(self.states(), ('APPROVED', False, 'CONFIRMED'))

    def test_repeated_confirmation_is_safe_and_refusal_is_rejected(self):
        with patch('app.services.payment_service.DownstreamClient.notify_registration', side_effect=self.callback) as callback:
            self.confirm()
            self.confirm()
        self.assertEqual(callback.call_count, 1)
        with self.sessions() as db, self.assertRaises(HTTPException) as exc:
            PaymentService(db).refuse(1)
        self.assertEqual(exc.exception.status_code, 409)
        self.assertEqual(self.states(), ('APPROVED', False, 'CONFIRMED'))

    def test_pending_cleanup_cannot_overwrite_accepted_result(self):
        with patch('app.services.payment_service.DownstreamClient.notify_registration', return_value=False):
            self.confirm()
        with self.sessions() as db:
            self.assertEqual(PaymentRepository(db).cancel_pending_by_registration(1), 0)
        self.assertEqual(self.states()[0], 'APPROVED')

    def test_overdue_reservation_cannot_be_approved(self):
        with self.engine.begin() as conn:
            conn.execute(text('UPDATE registrations SET expires_at = :expiry'),
                         {'expiry': datetime.now() - timedelta(minutes=1)})
        with self.assertRaises(HTTPException) as exc:
            self.confirm()
        self.assertEqual(exc.exception.status_code, 409)
        self.assertEqual(self.states()[0], 'PENDING')

    def test_other_participant_cannot_confirm(self):
        with self.sessions() as db, self.assertRaises(HTTPException) as exc:
            PaymentService(db).confirm(1, {'role': 'PARTICIPANT', 'userId': 3})
        self.assertEqual(exc.exception.status_code, 403)
        self.assertEqual(self.states()[0], 'PENDING')

    def test_cancelled_registration_is_not_resurrected(self):
        with patch('app.services.payment_service.DownstreamClient.notify_registration', return_value=False):
            self.confirm()
        with self.engine.begin() as conn:
            conn.execute(text("UPDATE registrations SET status = 'CANCELLED'"))
        with self.sessions() as db, patch('app.services.payment_service.DownstreamClient.notify_registration', return_value=False):
            PaymentService(db).deliver_result(1)
        self.assertEqual(self.states(), ('APPROVED', False, 'CANCELLED'))

    def test_refusal_and_expiry_are_also_durable(self):
        for operation, expected in [('refuse', 'REFUSED'), ('expire', 'EXPIRED')]:
            with self.subTest(operation=operation):
                with self.sessions() as db:
                    p = db.get(PaymentModel, 1)
                    p.status = 'PENDING'
                    p.registration_sync_pending = False
                    db.commit()
                with self.sessions() as db, patch('app.services.payment_service.DownstreamClient.notify_registration', return_value=False):
                    getattr(PaymentService(db), operation)(1)
                self.assertEqual(self.states()[:2], (expected, True))


if __name__ == '__main__':
    unittest.main()
