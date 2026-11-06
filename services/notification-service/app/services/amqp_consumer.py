import asyncio
import json
import logging
from typing import Optional
import aio_pika
from aio_pika import ExchangeType
from aio_pika.abc import AbstractIncomingMessage, AbstractRobustConnection
from app.config.database import SessionLocal
from app.config.settings import settings
from app.schemas.notification_schema import NotificationSendRequest
from app.services.notification_service import NotificationService

logger = logging.getLogger("notification-service.amqp")

EXCHANGE_NAME = "eventhub.domain.events"
DLX_NAME = "eventhub.domain.dlx"
QUEUE_NAME = "eventhub.notifications.queue"
DLQ_NAME = "eventhub.notifications.dlq"


class NotificationAmqpConsumer:
    """Consumidor assíncrono RabbitMQ com suporte a Dead Letter Queue (DLQ) e simulação de falha (RN05/RQ03)."""

    def __init__(self) -> None:
        self._connection: Optional[AbstractRobustConnection] = None
        self._task: Optional[asyncio.Task] = None
        self._stopping = asyncio.Event()

    async def start(self) -> None:
        if not settings.messaging_enabled:
            logger.info("Mensageria AMQP desativada via MESSAGING_ENABLED=false.")
            return
        self._stopping.clear()
        self._task = asyncio.create_task(self._run_loop())

    async def stop(self) -> None:
        self._stopping.set()
        if self._task is not None:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        if self._connection is not None and not self._connection.is_closed:
            await self._connection.close()

    async def _run_loop(self) -> None:
        while not self._stopping.is_set():
            try:
                self._connection = await aio_pika.connect_robust(settings.amqp_url, timeout=5)
                channel = await self._connection.channel()
                await channel.set_qos(prefetch_count=10)

                domain_exchange = await channel.declare_exchange(
                    EXCHANGE_NAME, ExchangeType.TOPIC, durable=True
                )
                dlx_exchange = await channel.declare_exchange(
                    DLX_NAME, ExchangeType.TOPIC, durable=True
                )

                dlq = await channel.declare_queue(DLQ_NAME, durable=True)
                await dlq.bind(dlx_exchange, routing_key="#")

                queue = await channel.declare_queue(
                    QUEUE_NAME,
                    durable=True,
                    arguments={"x-dead-letter-exchange": DLX_NAME},
                )
                for key in ("notification.#", "event.#", "registration.#", "payment.#"):
                    await queue.bind(domain_exchange, routing_key=key)

                await queue.consume(self._on_message)
                logger.info(
                    "Consumidor AMQP ativo na fila '%s' (DLQ='%s').",
                    QUEUE_NAME,
                    DLQ_NAME,
                )
                await self._stopping.wait()
            except asyncio.CancelledError:
                break
            except Exception as exc:  # noqa: BLE001
                logger.warning("Conexão AMQP indisponível (%s). Nova tentativa em 3s...", exc)
                await asyncio.sleep(3)

    @staticmethod
    async def _on_message(message: AbstractIncomingMessage) -> None:
        if NotificationService.get_failure_state():
            logger.warning(
                "Simulação de falha ativa (RN05): rejeitando mensagem '%s' para a DLQ '%s'.",
                message.routing_key,
                DLQ_NAME,
            )
            await message.reject(requeue=False)
            return

        try:
            raw = json.loads(message.body.decode("utf-8"))
            payload_data = raw.get("payload") if isinstance(raw, dict) and "payload" in raw else raw
            req = NotificationSendRequest(**payload_data)

            db = SessionLocal()
            try:
                service = NotificationService(db)
                service.send_notification(req)
            finally:
                db.close()

            await message.ack()
            logger.info(
                "Notificação AMQP processada com sucesso: eventType=%s, userId=%s",
                req.eventType,
                req.userId,
            )
        except Exception as exc:  # noqa: BLE001
            logger.error("Erro ao processar mensagem AMQP (%s): enviando para DLQ.", exc)
            await message.reject(requeue=False)


amqp_consumer = NotificationAmqpConsumer()
