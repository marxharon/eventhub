from typing import List, Optional
from fastapi import HTTPException
from sqlalchemy.orm import Session
from app.models.event_read_model import EventReadModel, ActivityReadModel
from app.repositories.search_repository import SearchRepository
from app.schemas.search_schema import EventSearchSchema, ActivitySearchSchema


class SearchService:
    def __init__(self, db: Session):
        self.repository = SearchRepository(db)

    @staticmethod
    def _to_activity_schema(act: ActivityReadModel) -> ActivitySearchSchema:
        price_val = float(act.price or 0.0)
        available = max(0, int(act.capacity) - int(act.occupied_slots))
        return ActivitySearchSchema(
            id=act.id,
            eventId=act.event_id,
            title=act.title,
            description=act.description,
            speaker=act.speaker,
            location=act.location,
            startTime=act.start_time,
            endTime=act.end_time,
            capacity=act.capacity,
            occupiedSlots=act.occupied_slots,
            availableSlots=available,
            price=price_val,
            paid=price_val > 0.0,
            waitlistEnabled=bool(act.waitlist_enabled),
            completed=bool(act.completed),
        )

    def _to_event_schema(
        self,
        ev: EventReadModel,
        only_free: Optional[bool] = None,
        only_available: Optional[bool] = None,
    ) -> EventSearchSchema:
        activities = self.repository.find_activities_by_event(
            ev.id,
            only_free=only_free,
            only_available=only_available,
        )
        return EventSearchSchema(
            id=ev.id,
            title=ev.title,
            description=ev.description,
            organization=ev.organization,
            organizerId=ev.organizer_id,
            startDate=ev.start_date,
            endDate=ev.end_date,
            status=ev.status,
            activities=[self._to_activity_schema(a) for a in activities],
        )

    def list_published_events(
        self,
        keyword: Optional[str] = None,
        organization: Optional[str] = None,
        only_free: Optional[bool] = None,
        only_available: Optional[bool] = None,
    ) -> List[EventSearchSchema]:
        events = self.repository.find_published_events(keyword=keyword, organization=organization)
        result = [
            self._to_event_schema(ev, only_free=only_free, only_available=only_available)
            for ev in events
        ]
        if only_free is not None or only_available is True:
            result = [ev for ev in result if len(ev.activities) > 0]
        return result

    def get_published_event_details(
        self,
        event_id: int,
        only_free: Optional[bool] = None,
        only_available: Optional[bool] = None,
    ) -> EventSearchSchema:
        ev = self.repository.find_published_event_by_id(event_id)
        if not ev:
            raise HTTPException(status_code=404, detail="Evento publicado não encontrado.")
        return self._to_event_schema(ev, only_free=only_free, only_available=only_available)

    def search_activities(
        self,
        keyword: Optional[str] = None,
        only_free: Optional[bool] = None,
        only_available: Optional[bool] = None,
    ) -> List[ActivitySearchSchema]:
        activities = self.repository.search_activities(
            keyword=keyword,
            only_free=only_free,
            only_available=only_available,
        )
        return [self._to_activity_schema(a) for a in activities]

    def get_activity_details(self, activity_id: int) -> ActivitySearchSchema:
        act = self.repository.find_activity_by_id(activity_id)
        if not act:
            raise HTTPException(status_code=404, detail="Atividade não encontrada.")
        return self._to_activity_schema(act)
