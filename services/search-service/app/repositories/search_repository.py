from typing import List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import or_
from app.models.event_read_model import EventReadModel, ActivityReadModel


class SearchRepository:
    def __init__(self, db: Session):
        self.db = db

    def find_published_events(
        self,
        keyword: Optional[str] = None,
        organization: Optional[str] = None,
    ) -> List[EventReadModel]:
        query = self.db.query(EventReadModel).filter(EventReadModel.status == "PUBLISHED")

        if keyword and keyword.strip():
            term = f"%{keyword.strip()}%"
            query = query.filter(
                or_(
                    EventReadModel.title.ilike(term),
                    EventReadModel.description.ilike(term),
                    EventReadModel.organization.ilike(term),
                )
            )

        if organization and organization.strip():
            query = query.filter(EventReadModel.organization.ilike(f"%{organization.strip()}%"))

        return query.order_by(EventReadModel.start_date.asc()).all()

    def find_published_event_by_id(self, event_id: int) -> Optional[EventReadModel]:
        return (
            self.db.query(EventReadModel)
            .filter(EventReadModel.id == event_id, EventReadModel.status == "PUBLISHED")
            .first()
        )

    def find_activities_by_event(
        self,
        event_id: int,
        only_free: Optional[bool] = None,
        only_available: Optional[bool] = None,
    ) -> List[ActivityReadModel]:
        query = self.db.query(ActivityReadModel).filter(ActivityReadModel.event_id == event_id)

        if only_free is True:
            query = query.filter(ActivityReadModel.price == 0)
        elif only_free is False:
            query = query.filter(ActivityReadModel.price > 0)

        if only_available is True:
            query = query.filter(ActivityReadModel.occupied_slots < ActivityReadModel.capacity)

        return query.order_by(ActivityReadModel.start_time.asc()).all()

    def find_activity_by_id(self, activity_id: int) -> Optional[ActivityReadModel]:
        return self.db.query(ActivityReadModel).filter(ActivityReadModel.id == activity_id).first()

    def search_activities(
        self,
        keyword: Optional[str] = None,
        only_free: Optional[bool] = None,
        only_available: Optional[bool] = None,
    ) -> List[ActivityReadModel]:
        query = (
            self.db.query(ActivityReadModel)
            .join(EventReadModel, EventReadModel.id == ActivityReadModel.event_id)
            .filter(EventReadModel.status == "PUBLISHED")
        )

        if keyword and keyword.strip():
            term = f"%{keyword.strip()}%"
            query = query.filter(
                or_(
                    ActivityReadModel.title.ilike(term),
                    ActivityReadModel.speaker.ilike(term),
                    ActivityReadModel.location.ilike(term),
                )
            )

        if only_free is True:
            query = query.filter(ActivityReadModel.price == 0)
        elif only_free is False:
            query = query.filter(ActivityReadModel.price > 0)

        if only_available is True:
            query = query.filter(ActivityReadModel.occupied_slots < ActivityReadModel.capacity)

        return query.order_by(ActivityReadModel.start_time.asc()).all()
