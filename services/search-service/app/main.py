from fastapi import FastAPI
from app.controllers.search_controller import router as search_router

app = FastAPI(
    title="EventHub — 3. Serviço de Busca e Catálogo (search-service)",
    description="Microsserviço em Python 3.12 / FastAPI para consulta rápida de eventos publicados, programação detalhada e disponibilidade de vagas em tempo real.",
    version="1.0.0",
    docs_url="/api/search/docs",
    openapi_url="/api/search/openapi.json",
    servers=[{"url": "http://localhost:8080", "description": "API Gateway (:8080)"}],
)

app.include_router(search_router)
