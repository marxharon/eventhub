from fastapi import FastAPI
from app.controllers.search_controller import router as search_router

app = FastAPI(
    title="EventHub - Search Service",
    description="Microsserviço 3: Consulta rápida de eventos publicados, programação e vagas (RF04, RQ02)",
    version="1.0.0",
)

app.include_router(search_router)
