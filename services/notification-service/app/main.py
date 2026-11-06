from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.controllers.notification_controller import router as notification_router
from app.services.amqp_consumer import amqp_consumer


@asynccontextmanager
async def lifespan(_app: FastAPI):
    await amqp_consumer.start()
    try:
        yield
    finally:
        await amqp_consumer.stop()


app = FastAPI(
    title="EventHub — 6. Serviço de Notificação (notification-service)",
    description="Microsserviço em Python 3.12 / FastAPI responsável por comunicar inscrições, confirmações, cancelamentos e pagamentos, com suporte a Publish/Subscribe Assíncrono (RabbitMQ + DLQ) e simulação de falha (RN05 / RQ03).",
    version="1.0.0",
    docs_url="/api/notifications/docs",
    openapi_url="/api/notifications/openapi.json",
    servers=[{"url": "http://localhost:8080", "description": "API Gateway (:8080)"}],
    lifespan=lifespan,
)

app.include_router(notification_router)
