import uuid
from typing import List
from fastapi import HTTPException
from sqlalchemy.orm import Session
from app.models.payment_model import PaymentModel
from app.repositories.payment_repository import PaymentRepository
from app.schemas.payment_schema import PaymentInitiateRequest, PaymentResponse
from app.services.downstream_client import DownstreamClient


class PaymentService:
    def __init__(self, db: Session):
        self.repository = PaymentRepository(db)

    @staticmethod
    def _to_schema(model: PaymentModel) -> PaymentResponse:
        return PaymentResponse(
            id=model.id,
            registrationId=model.registration_id,
            userId=model.user_id,
            amount=float(model.amount),
            status=model.status,
            providerRef=model.provider_ref,
            createdAt=model.created_at,
            updatedAt=model.updated_at,
        )

    def initiate(self, payload: PaymentInitiateRequest) -> PaymentResponse:
        provider_ref = f"SIM-{uuid.uuid4().hex[:12].upper()}"
        payment = self.repository.create(payload.registrationId, payload.userId, payload.amount, provider_ref)
        if payment is None:
            raise HTTPException(status_code=422, detail="Inscrição ou participante inexistente.")
        return self._to_schema(payment)

    def confirm(self, payment_id: int) -> PaymentResponse:
        return self._transition(
            payment_id, "APPROVED", "PAYMENT_APPROVED",
            "Pagamento aprovado. Sua inscrição foi confirmada.",
        )

    def refuse(self, payment_id: int) -> PaymentResponse:
        return self._transition(
            payment_id, "REFUSED", "PAYMENT_REFUSED",
            "Pagamento recusado. A vaga reservada foi liberada.",
        )

    def expire(self, payment_id: int) -> PaymentResponse:
        return self._transition(
            payment_id, "EXPIRED", "PAYMENT_EXPIRED",
            "Prazo de pagamento expirado. A vaga reservada foi liberada.",
        )

    def list_by_registration(self, registration_id: int) -> List[PaymentResponse]:
        return [self._to_schema(p) for p in self.repository.find_by_registration(registration_id)]

    def _transition(self, payment_id: int, new_status: str, event_type: str, message: str) -> PaymentResponse:
        payment = self.repository.find_by_id(payment_id)
        if payment is None:
            raise HTTPException(status_code=404, detail="Pagamento não encontrado.")
        if payment.status != "PENDING":
            raise HTTPException(
                status_code=409,
                detail=f"Transição inválida: pagamento já está em {payment.status}.",
            )

        payment = self.repository.update_status(payment, new_status)

        # Efeitos colaterais após persistir o estado: falhas não revertem o pagamento (RN05).
        DownstreamClient.notify_registration(payment.registration_id, payment.id, new_status)
        email = self.repository.find_user_email(payment.user_id)
        if email:
            DownstreamClient.send_notification(payment.user_id, email, event_type, message)

        return self._to_schema(payment)
