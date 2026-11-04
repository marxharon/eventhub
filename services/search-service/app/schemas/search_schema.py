from datetime import datetime
from typing import List, Optional
from pydantic import BaseModel


class ActivitySearchSchema(BaseModel):
    id: int
    eventId: int
    title: str
    description: Optional[str] = None
    speaker: str
    location: str
    startTime: datetime
    endTime: datetime
    capacity: int
    occupiedSlots: int
    availableSlots: int
    price: float
    paid: bool
    waitlistEnabled: bool
    completed: bool


class EventSearchSchema(BaseModel):
    id: int
    title: str
    description: Optional[str] = None
    organization: str
    organizerId: int
    startDate: datetime
    endDate: datetime
    status: str
    activities: List[ActivitySearchSchema] = []
