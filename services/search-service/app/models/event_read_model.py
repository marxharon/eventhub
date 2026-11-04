from sqlalchemy import Column, BigInteger, String, Text, Integer, Numeric, Boolean, DateTime, ForeignKey
from app.config.database import Base


class EventReadModel(Base):
    __tablename__ = "events"

    id = Column(BigInteger, primary_key=True, index=True)
    title = Column(String(200), nullable=False)
    description = Column(Text)
    organization = Column(String(150), nullable=False)
    organizer_id = Column(BigInteger, nullable=False)
    start_date = Column(DateTime, nullable=False)
    end_date = Column(DateTime, nullable=False)
    status = Column(String(30), nullable=False)
    created_at = Column(DateTime, nullable=False)


class ActivityReadModel(Base):
    __tablename__ = "activities"

    id = Column(BigInteger, primary_key=True, index=True)
    event_id = Column(BigInteger, ForeignKey("events.id"), nullable=False)
    title = Column(String(200), nullable=False)
    description = Column(Text)
    speaker = Column(String(150), nullable=False)
    location = Column(String(150), nullable=False)
    start_time = Column(DateTime, nullable=False)
    end_time = Column(DateTime, nullable=False)
    capacity = Column(Integer, nullable=False)
    occupied_slots = Column(Integer, nullable=False)
    price = Column(Numeric(10, 2), nullable=False)
    waitlist_enabled = Column(Boolean, nullable=False)
    completed = Column(Boolean, nullable=False)
    created_at = Column(DateTime, nullable=False)
