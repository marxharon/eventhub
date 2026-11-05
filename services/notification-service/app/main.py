from fastapi import FastAPI
from app.controllers.notification_controller import router as notification_router

app = FastAPI(
    title="EventHub — 6. Serviço de Notificação (notification-service)",
    description="Microsserviço em Python 3.12 / FastAPI responsável por comunicar inscrições, confirmações, cancelamentos e pagamentos, com suporte a simulação de falha (RN05 / RQ03).",
    version="1.0.0",
    docs_url="/api/notifications/docs",
    openapi_url="/api/notifications/openapi.json",
    servers=[{"url": "http://localhost:8080", "description": "API Gateway (:8080)"}],
)

app.include_router(notification_router)
