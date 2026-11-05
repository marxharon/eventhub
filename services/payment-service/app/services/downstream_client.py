import logging
from typing import Any, Dict
import httpx
from app.config.settings import settings

logger = logging.getLogger("payment-service")


class DownstreamClient:
    """Chamadas HTTP de saída com timeout curto; falhas são registradas e não propagadas (RN05)."""

    @staticmethod
    def _post(url: str, body: Dict[str, Any]) -> bool:
        try:
            with httpx.Client(timeout=settings.downstream_timeout_seconds) as client:
                response = client.post(url, json=body)
                response.raise_for_status()
                return True
        except Exception as exc:  # noqa: BLE001 - degradação graciosa intencional
            logger.warning("Falha ao chamar %s: %s", url, exc)
            return False

    @classmethod
    def notify_registration(cls, registration_id: int, payment_id: int, status: str) -> bool:
        return cls._post(
            f"{settings.registration_service_url}/api/registrations/payment-webhook",
            {"registrationId": registration_id, "paymentId": payment_id, "paymentStatus": status},
        )

    @classmethod
    def send_notification(cls, user_id: int, recipient_email: str, event_type: str, message: str) -> bool:
        return cls._post(
            f"{settings.notification_service_url}/api/notifications/send",
            {
                "userId": user_id,
                "recipientEmail": recipient_email,
                "eventType": event_type,
                "message": message,
            },
        )
