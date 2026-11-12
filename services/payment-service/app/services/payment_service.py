import uuid
from datetime import datetime
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
            registrationSyncPending=model.registration_sync_pending,
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
            payment_id, "APPROVED",
            current_user=current_user,
        )

    def refuse(self, payment_id: int, current_user: dict | None = None) -> PaymentResponse:
        return self._transition(
            payment_id, "REFUSED",
            current_user=current_user,
        )

    def expire(self, payment_id: int, current_user: dict | None = None) -> PaymentResponse:
        return self._transition(
            payment_id, "EXPIRED",
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
        current_user: dict | None = None,
    ) -> PaymentResponse:
        payment, registration = self.repository.lock_for_transition(payment_id)
        if payment is None:
            self.repository.finish_read()
            raise HTTPException(status_code=404, detail="Pagamento não encontrado.")
        if (
            current_user is not None
            and current_user.get("role") != "ORGANIZER"
            and int(payment.user_id) != int(current_user["userId"])
        ):
            self.repository.finish_read()
            raise HTTPException(
                status_code=403,
                detail="RN07 Violada: Participantes podem operar apenas seus próprios pagamentos.",
            )
        if payment.status != "PENDING":
            previous = payment.status
            self.repository.finish_read()
            if previous != new_status:
                raise HTTPException(status_code=409, detail=f"Transição inválida: pagamento já está em {previous}.")
            # Same result is safe to repeat, including after a lost response.
            self.deliver_result(payment_id)
            return self._to_schema(self.repository.find_by_id(payment_id))

        if registration.status != "PENDING_PAYMENT" or (
            new_status == "APPROVED" and registration.expires_at is not None
            and registration.expires_at <= datetime.now()
        ):
            self.repository.finish_read()
            raise HTTPException(status_code=409, detail="Inscrição encerrada ou reserva vencida.")

        payment = self.repository.save_result_for_delivery(payment, new_status)
        self.deliver_result(payment_id)

        return self._to_schema(self.repository.find_by_id(payment_id))

    def deliver_result(self, payment_id: int) -> bool:
        payment = self.repository.find_by_id(payment_id)
        if payment is None or not payment.registration_sync_pending:
            return True
        delivered = DownstreamClient.notify_registration(payment.registration_id, payment.id, payment.status)
        # A user cancellation/expiry must not be resurrected by a delayed callback.
        # Preserve the payment result for accounting; only stop delivery to the closed registration.
        if delivered or self.repository.registration_is_closed(payment.registration_id):
            self.repository.mark_delivered(payment.id, payment.status)
            if delivered:
                # Do not announce a confirmed registration before its callback is acknowledged.
                messages = {
                    "APPROVED": "Pagamento aprovado. Sua inscrição foi confirmada.",
                    "REFUSED": "Pagamento recusado. A vaga reservada foi liberada.",
                    "EXPIRED": "Prazo de pagamento expirado. A vaga reservada foi liberada.",
                }
                email = self.repository.find_user_email(payment.user_id)
                if email:
                    DownstreamClient.send_notification(payment.user_id, email,
                                                       "PAYMENT_" + payment.status, messages[payment.status])
            return True
        return False
