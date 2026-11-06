from fastapi import FastAPI
import asyncio
from contextlib import asynccontextmanager
from app.controllers.payment_controller import router as payment_router
from app.services.reconciliation import ensure_delivery_schema, reconciliation_loop


@asynccontextmanager
async def lifespan(app: FastAPI):
    await asyncio.to_thread(ensure_delivery_schema)
    stop = asyncio.Event()
    worker = asyncio.create_task(reconciliation_loop(stop))
    try:
        yield
    finally:
        stop.set()
        await worker

app = FastAPI(
    lifespan=lifespan,
    title="EventHub — 5. Serviço de Pagamento (payment-service)",
    description="Microsserviço em Python 3.12 / FastAPI responsável por iniciar, confirmar, recusar e expirar pagamentos simulados (RF07, RN02, RN03).",
    version="1.0.0",
    docs_url="/api/payments/docs",
    openapi_url="/api/payments/openapi.json",
    servers=[{"url": "http://localhost:8080", "description": "API Gateway (:8080)"}],
)

app.include_router(payment_router)
