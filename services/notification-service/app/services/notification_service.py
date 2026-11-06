from typing import List
from fastapi import HTTPException
from sqlalchemy.orm import Session
from app.models.notification_model import NotificationModel
from app.repositories.notification_repository import NotificationRepository
from app.schemas.notification_schema import (
    NotificationSendRequest,
    NotificationResponse,
    FailureSimulationState,
    DeletedCountResponse,
)


class NotificationService:
    # Estado em memória para simulação de indisponibilidade (RN05 / RQ03)
    _simulate_failure: bool = False

    def __init__(self, db: Session):
        self.repository = NotificationRepository(db)

    @classmethod
    def get_failure_state(cls) -> bool:
        return cls._simulate_failure

    @classmethod
    def set_failure_state(cls, fail: bool) -> FailureSimulationState:
        cls._simulate_failure = fail
        msg = (
            "Modo de falha ATIVADO: O serviço retornará HTTP 503 em /api/notifications/send para validar RN05."
            if fail
            else "Modo de falha DESATIVADO: O serviço de notificações voltou a operar normalmente."
        )
        return FailureSimulationState(simulateFailure=cls._simulate_failure, message=msg)

    @staticmethod
    def _to_schema(model: NotificationModel) -> NotificationResponse:
        return NotificationResponse(
            id=model.id,
            userId=model.user_id,
            recipientEmail=model.recipient_email,
            eventType=model.event_type,
            message=model.message,
            status=model.status,
            createdAt=model.created_at,
            read=model.is_read,
        )

    def send_notification(self, payload: NotificationSendRequest) -> NotificationResponse:
        if self.get_failure_state():
            raise HTTPException(
                status_code=503,
                detail="Simulação de indisponibilidade ativa no notification-service (RN05 / RQ03).",
            )

        saved = self.repository.create(
            user_id=payload.userId,
            recipient_email=payload.recipientEmail,
            event_type=payload.eventType,
            message=payload.message,
            status="SENT",
        )
        return self._to_schema(saved)

    def list_user_notifications(self, user_id: int, current_user: dict | None = None) -> List[NotificationResponse]:
        if (
            current_user is not None
            and current_user.get("role") != "ORGANIZER"
            and int(user_id) != int(current_user["userId"])
        ):
            raise HTTPException(
                status_code=403,
                detail="RN07 Violada: Participantes podem consultar apenas suas próprias notificações.",
            )
        items = self.repository.find_by_user(user_id)
        return [self._to_schema(item) for item in items]

    def list_all_notifications(self, current_user: dict | None = None) -> List[NotificationResponse]:
        if current_user is not None and current_user.get("role") != "ORGANIZER":
            raise HTTPException(
                status_code=403,
                detail="RN07 Violada: Apenas organizadores podem listar todas as notificações.",
            )
        items = self.repository.find_all()
        return [self._to_schema(item) for item in items]

    def _get_or_404(self, notification_id: int) -> NotificationModel:
        notification = self.repository.find_by_id(notification_id)
        if notification is None:
            raise HTTPException(status_code=404, detail=f"Notificação {notification_id} não encontrada.")
        return notification

    def mark_as_read(self, notification_id: int, current_user: dict | None = None) -> NotificationResponse:
        notification = self._get_or_404(notification_id)
        if (
            current_user is not None
            and current_user.get("role") != "ORGANIZER"
            and int(notification.user_id) != int(current_user["userId"])
        ):
            raise HTTPException(
                status_code=403,
                detail="RN07 Violada: Participantes podem alterar apenas suas próprias notificações.",
            )
        if not notification.is_read:
            notification = self.repository.mark_as_read(notification)
        return self._to_schema(notification)

    def delete_notification(self, notification_id: int, current_user: dict | None = None) -> None:
        notification = self._get_or_404(notification_id)
        if (
            current_user is not None
            and current_user.get("role") != "ORGANIZER"
            and int(notification.user_id) != int(current_user["userId"])
        ):
            raise HTTPException(
                status_code=403,
                detail="RN07 Violada: Participantes podem excluir apenas suas próprias notificações.",
            )
        if not notification.is_read:
            raise HTTPException(
                status_code=409,
                detail="Só é possível apagar notificações já lidas. Marque-a como lida antes.",
            )
        self.repository.delete(notification)

    def delete_read_notifications(self, user_id: int, current_user: dict | None = None) -> DeletedCountResponse:
        if (
            current_user is not None
            and current_user.get("role") != "ORGANIZER"
            and int(user_id) != int(current_user["userId"])
        ):
            raise HTTPException(
                status_code=403,
                detail="RN07 Violada: Participantes podem excluir apenas suas próprias notificações.",
            )
        return DeletedCountResponse(deleted=self.repository.delete_read_by_user(user_id))
