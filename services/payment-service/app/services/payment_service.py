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

    def confirm(self, payment_id: int, current_user: dict | None = None) -> PaymentResponse:
        return self._transition(
            payment_id, "APPROVED", "PAYMENT_APPROVED",
            "Pagamento aprovado. Sua inscrição foi confirmada.",
            current_user=current_user,
        )

    def refuse(self, payment_id: int, current_user: dict | None = None) -> PaymentResponse:
        return self._transition(
            payment_id, "REFUSED", "PAYMENT_REFUSED",
            "Pagamento recusado. A vaga reservada foi liberada.",
            current_user=current_user,
        )

    def expire(self, payment_id: int, current_user: dict | None = None) -> PaymentResponse:
        return self._transition(
            payment_id, "EXPIRED", "PAYMENT_EXPIRED",
            "Prazo de pagamento expirado. A vaga reservada foi liberada.",
            current_user=current_user,
        )

    def list_by_registration(self, registration_id: int, current_user: dict | None = None) -> List[PaymentResponse]:
        if current_user is not None and current_user.get("role") != "ORGANIZER":
            owner_id = self.repository.find_registration_user_id(registration_id)
            if owner_id is not None and owner_id != int(current_user["userId"]):
                raise HTTPException(
                    status_code=403,
                    detail="RN07 Violada: Participantes podem consultar apenas seus próprios pagamentos.",
                )
        return [self._to_schema(p) for p in self.repository.find_by_registration(registration_id)]

    def cancel_pending_by_registration(self, registration_id: int, new_status: str = "EXPIRED") -> int:
        target = new_status if new_status in ("EXPIRED", "REFUSED") else "EXPIRED"
        return self.repository.cancel_pending_by_registration(registration_id, target)

    def _transition(
        self,
        payment_id: int,
        new_status: str,
        event_type: str,
        message: str,
        current_user: dict | None = None,
    ) -> PaymentResponse:
        payment = self.repository.find_by_id(payment_id)
        if payment is None:
            raise HTTPException(status_code=404, detail="Pagamento não encontrado.")
        if (
            current_user is not None
            and current_user.get("role") != "ORGANIZER"
            and int(payment.user_id) != int(current_user["userId"])
        ):
            raise HTTPException(
                status_code=403,
                detail="RN07 Violada: Participantes podem operar apenas seus próprios pagamentos.",
            )
        if payment.status != "PENDING":
            raise HTTPException(
                status_code=409,
                detail=f"Transição inválida: pagamento já está em {payment.status}.",
            )

        previous_status = payment.status
        payment = self.repository.update_status(payment, new_status)

        # Consistência entre pagamento e inscrição (RN02/RN03): se o registration-service não confirmar,
        # reverte-se o pagamento para evitar que o pagamento fique aprovado com a inscrição pendente.
        if not DownstreamClient.notify_registration(payment.registration_id, payment.id, new_status):
            self.repository.update_status(payment, previous_status)
            raise HTTPException(
                status_code=424,
                detail="Serviço de inscrições indisponível para sincronizar o status do pagamento. Operação revertida.",
            )

        # Notificação ao usuário é não-bloqueante (RN05).
        email = self.repository.find_user_email(payment.user_id)
        if email:
            DownstreamClient.send_notification(payment.user_id, email, event_type, message)

        return self._to_schema(payment)
