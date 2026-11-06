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
        pending_items = (
            self.db.query(PaymentModel)
            .filter(
                PaymentModel.registration_id == registration_id,
                PaymentModel.status == "PENDING",
            )
            .all()
        )
        for item in pending_items:
            item.status = new_status
        if pending_items:
            self.db.commit()
        return len(pending_items)

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
