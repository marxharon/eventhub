from typing import List, Optional
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session
from app.models.payment_model import PaymentModel


class PaymentRepository:
    def __init__(self, db: Session):
        self.db = db

    def create(self, registration_id: int, user_id: int, amount: float, provider_ref: str) -> Optional[PaymentModel]:
        payment = PaymentModel(
            registration_id=registration_id,
            user_id=user_id,
            amount=amount,
            status="PENDING",
            provider_ref=provider_ref,
        )
        self.db.add(payment)
        try:
            self.db.commit()
        except IntegrityError:
            self.db.rollback()
            return None
        self.db.refresh(payment)
        return payment

    def find_by_id(self, payment_id: int) -> Optional[PaymentModel]:
        return self.db.query(PaymentModel).filter(PaymentModel.id == payment_id).first()

    def lock_for_transition(self, payment_id: int):
        """Use the same lock order as registration-service: activity, registration, payment."""
        ids = self.db.execute(text(
            "SELECT r.id, r.activity_id FROM registrations r "
            "JOIN payments p ON p.registration_id = r.id WHERE p.id = :id"
        ), {"id": payment_id}).first()
        if ids is None:
            return None, None
        self.db.execute(text("SELECT id FROM activities WHERE id = :id FOR UPDATE"), {"id": ids.activity_id})
        registration = self.db.execute(text(
            "SELECT status, expires_at FROM registrations WHERE id = :id FOR UPDATE"
        ), {"id": ids.id}).first()
        payment = (self.db.query(PaymentModel).filter(PaymentModel.id == payment_id)
                   .populate_existing().with_for_update().first())
        return payment, registration

    def save_result_for_delivery(self, payment: PaymentModel, status: str) -> PaymentModel:
        # Result and delivery intent must be committed atomically before any network call.
        payment.status = status
        payment.registration_sync_pending = True
        self.db.commit()
        self.db.refresh(payment)
        return payment

    def finish_read(self):
        self.db.rollback()  # release locks for an idempotent repeat or rejected request

    def mark_delivered(self, payment_id: int, status: str):
        self.db.query(PaymentModel).filter(
            PaymentModel.id == payment_id, PaymentModel.status == status,
        ).update({PaymentModel.registration_sync_pending: False}, synchronize_session=False)
        self.db.commit()

    def find_pending_delivery_ids(self, limit: int = 100):
        return [row[0] for row in self.db.query(PaymentModel.id)
                .filter(PaymentModel.registration_sync_pending.is_(True))
                .order_by(PaymentModel.updated_at, PaymentModel.id).limit(limit).all()]

    def registration_is_closed(self, registration_id: int) -> bool:
        status = self.db.execute(text("SELECT status FROM registrations WHERE id = :id"),
                                 {"id": registration_id}).scalar()
        return status in ("CANCELLED", "EXPIRED")

    def find_by_registration(self, registration_id: int) -> List[PaymentModel]:
        return (
            self.db.query(PaymentModel)
            .filter(PaymentModel.registration_id == registration_id)
            .order_by(PaymentModel.created_at.desc())
            .all()
        )

    def update_status(self, payment: PaymentModel, status: str) -> PaymentModel:
        payment.status = status
        self.db.commit()
        self.db.refresh(payment)
        return payment

    def cancel_pending_by_registration(self, registration_id: int, new_status: str = "EXPIRED") -> int:
        # Conditional UPDATE cannot overwrite an accepted terminal result after waiting for a lock.
        count = self.db.query(PaymentModel).filter(
            PaymentModel.registration_id == registration_id,
            PaymentModel.status == "PENDING",
        ).update({PaymentModel.status: new_status}, synchronize_session=False)
        self.db.commit()
        return count

    def find_registration_user_id(self, registration_id: int) -> Optional[int]:
        val = self.db.execute(
            text("SELECT user_id FROM registrations WHERE id = :reg_id"),
            {"reg_id": registration_id},
        ).scalar()
        return int(val) if val is not None else None

    def find_user_email(self, user_id: int) -> Optional[str]:
        return self.db.execute(
            text("SELECT email FROM users WHERE id = :user_id"), {"user_id": user_id}
        ).scalar()
