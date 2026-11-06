import asyncio
import logging
from sqlalchemy import text
from app.config.database import SessionLocal, engine
from app.config.settings import settings
from app.services.payment_service import PaymentService

logger = logging.getLogger("payment-service")


def ensure_delivery_schema():
    # init.sql only runs for a new PostgreSQL volume. Preserve existing data on upgrades.
    with engine.begin() as connection:
        connection.execute(text(
            "ALTER TABLE payments ADD COLUMN IF NOT EXISTS "
            "registration_sync_pending BOOLEAN NOT NULL DEFAULT FALSE"
        ))
        connection.execute(text(
            "UPDATE payments SET registration_sync_pending = TRUE "
            "WHERE status IN ('APPROVED', 'REFUSED', 'EXPIRED') "
            "AND EXISTS (SELECT 1 FROM registrations r "
            "WHERE r.id = payments.registration_id AND r.status = 'PENDING_PAYMENT')"
        ))


def reconcile_once():
    with SessionLocal() as db:
        service = PaymentService(db)
        ids = service.repository.find_pending_delivery_ids()
    for payment_id in ids:
        try:
            # Fresh session per item prevents stale reads and isolates failed transactions.
            with SessionLocal() as db:
                PaymentService(db).deliver_result(payment_id)
        except Exception:
            logger.exception("Falha ao reconciliar pagamento %s; será tentado novamente", payment_id)


async def reconciliation_loop(stop: asyncio.Event):
    while not stop.is_set():
        try:
            await asyncio.to_thread(reconcile_once)
        except Exception:
            logger.exception("Falha no ciclo de reconciliação; será tentado novamente")
        try:
            await asyncio.wait_for(stop.wait(), timeout=max(0.1, settings.reconciliation_interval_seconds))
        except asyncio.TimeoutError:
            pass
