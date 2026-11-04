from typing import List, Optional
from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from app.config.database import get_db
from app.schemas.search_schema import EventSearchSchema, ActivitySearchSchema
from app.services.search_service import SearchService

router = APIRouter(prefix="/api/search", tags=["Search & Catalog (RF04)"])


@router.get("/health")
def health():
    return {
        "status": "UP",
        "service": "search-service",
        "stack": "Python 3.12 / FastAPI",
        "port": 8083,
        "requirements": ["RF04", "RQ02", "RQ05"],
    }


@router.get("/events", response_model=List[EventSearchSchema])
def search_published_events(
    keyword: Optional[str] = Query(default=None, description="Busca por título, descrição ou organização"),
    organization: Optional[str] = Query(default=None, description="Filtro por organização"),
    onlyFree: Optional[bool] = Query(default=None, description="Filtrar atividades gratuitas (true) ou pagas (false)"),
    onlyAvailable: Optional[bool] = Query(default=None, description="Apenas atividades com vagas imediatas"),
    db: Session = Depends(get_db),
):
    service = SearchService(db)
    return service.list_published_events(
        keyword=keyword,
        organization=organization,
        only_free=onlyFree,
        only_available=onlyAvailable,
    )


@router.get("/events/{event_id}", response_model=EventSearchSchema)
def get_published_event(
    event_id: int,
    onlyFree: Optional[bool] = Query(default=None),
    onlyAvailable: Optional[bool] = Query(default=None),
    db: Session = Depends(get_db),
):
    service = SearchService(db)
    return service.get_published_event_details(
        event_id,
        only_free=onlyFree,
        only_available=onlyAvailable,
    )


@router.get("/activities", response_model=List[ActivitySearchSchema])
def search_activities(
    keyword: Optional[str] = Query(default=None, description="Busca por atividade, palestrante ou local"),
    onlyFree: Optional[bool] = Query(default=None),
    onlyAvailable: Optional[bool] = Query(default=None),
    db: Session = Depends(get_db),
):
    service = SearchService(db)
    return service.search_activities(
        keyword=keyword,
        only_free=onlyFree,
        only_available=onlyAvailable,
    )


@router.get("/activities/{activity_id}", response_model=ActivitySearchSchema)
def get_activity_details(activity_id: int, db: Session = Depends(get_db)):
    service = SearchService(db)
    return service.get_activity_details(activity_id)
