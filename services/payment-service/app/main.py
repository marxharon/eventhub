from fastapi import FastAPI
from app.controllers.payment_controller import router as payment_router

app = FastAPI(
    title="EventHub — 5. Serviço de Pagamento (payment-service)",
    description="Microsserviço em Python 3.12 / FastAPI responsável por iniciar, confirmar, recusar e expirar pagamentos simulados (RF07, RN02, RN03).",
    version="1.0.0",
    docs_url="/api/payments/docs",
    openapi_url="/api/payments/openapi.json",
    servers=[{"url": "http://localhost:8080", "description": "API Gateway (:8080)"}],
)

app.include_router(payment_router)
