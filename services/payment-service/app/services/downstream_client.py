import json
import logging
import uuid
from datetime import datetime, timezone
from typing import Any, Dict
import httpx
import pika
import pybreaker
from tenacity import retry, stop_after_attempt, wait_exponential
from app.config.settings import settings

logger = logging.getLogger("payment-service")

EXCHANGE_NAME = "eventhub.domain.events"

registration_cb = pybreaker.CircuitBreaker(
    fail_max=4,
    reset_timeout=10,
    name="registration_webhook_cb",
)

notification_cb = pybreaker.CircuitBreaker(
    fail_max=3,
    reset_timeout=15,
    name="notification_cb",
)


class DownstreamClient:
    """Chamadas de saída com Publish/Subscribe Assíncrono (RabbitMQ) e Fallback HTTP protegido por Circuit Breaker (PyBreaker) + Retry (Tenacity)."""

    @staticmethod
    def _post_once(url: str, body: Dict[str, Any]) -> None:
        with httpx.Client(timeout=settings.downstream_timeout_seconds) as client:
            response = client.post(url, json=body)
            response.raise_for_status()

    @classmethod
    @retry(
        stop=stop_after_attempt(2),
        wait=wait_exponential(multiplier=0.1, min=0.1, max=0.3),
        reraise=True,
    )
    def _post_with_retry(cls, url: str, body: Dict[str, Any]) -> None:
        cls._post_once(url, body)

    @staticmethod
    def _publish_amqp(routing_key: str, payload: Dict[str, Any]) -> None:
        credentials = pika.PlainCredentials(settings.rabbitmq_user, settings.rabbitmq_password)
        params = pika.ConnectionParameters(
            host=settings.rabbitmq_host,
            port=settings.rabbitmq_port,
            credentials=credentials,
            socket_timeout=1.0,
            blocked_connection_timeout=1.0,
        )
        connection = pika.BlockingConnection(params)
        try:
            channel = connection.channel()
            channel.exchange_declare(exchange=EXCHANGE_NAME, exchange_type="topic", durable=True)
            envelope = {
                "eventId": str(uuid.uuid4()),
                "occurredAt": datetime.now(timezone.utc).isoformat(),
                "sourceService": settings.service_name,
                "routingKey": routing_key,
                "payload": payload,
            }
            channel.basic_publish(
                exchange=EXCHANGE_NAME,
                routing_key=routing_key,
                body=json.dumps(envelope).encode("utf-8"),
                properties=pika.BasicProperties(
                    content_type="application/json",
                    delivery_mode=2,
                ),
            )
        finally:
            if connection.is_open:
                connection.close()

    @classmethod
    def notify_registration(cls, registration_id: int, payment_id: int, status: str) -> bool:
        url = f"{settings.registration_service_url}/api/registrations/payment-webhook"
        payload = {"registrationId": registration_id, "paymentId": payment_id, "paymentStatus": status}
        try:
            registration_cb.call(cls._post_with_retry, url, payload)
            return True
        except Exception as exc:  # noqa: BLE001 - inclui CircuitBreakerError e HTTPError
            logger.warning("Falha/CircuitBreaker ao chamar webhook %s: %s", url, exc)
            return False

    @classmethod
    def send_notification(cls, user_id: int, recipient_email: str, event_type: str, message: str) -> bool:
        payload = {
            "userId": user_id,
            "recipientEmail": recipient_email,
            "eventType": event_type,
            "message": message,
        }
        if settings.messaging_enabled:
            routing_key = f"notification.payment.{event_type.lower()}"
            try:
                cls._publish_amqp(routing_key, payload)
                logger.info("Evento %s publicado via RabbitMQ (%s) para userId=%s", event_type, routing_key, user_id)
                return True
            except Exception as amqp_exc:  # noqa: BLE001 - fallback automático para HTTP + Circuit Breaker
                logger.warning("Broker AMQP indisponível (%s); acionando fallback HTTP para %s", amqp_exc, event_type)

        url = f"{settings.notification_service_url}/api/notifications/send"
        try:
            notification_cb.call(cls._post_once, url, payload)
            return True
        except Exception as exc:  # noqa: BLE001 - degradação graciosa intencional (RN05)
            logger.warning("Falha/CircuitBreaker ao enviar notificação %s (%s): %s", event_type, url, exc)
            return False
