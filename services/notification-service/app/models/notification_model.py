from datetime import datetime
from sqlalchemy import Column, BigInteger, String, Text, DateTime
from app.config.database import Base


class NotificationModel(Base):
    __tablename__ = "notifications"

    id = Column(BigInteger, primary_key=True, index=True)
    user_id = Column(BigInteger, nullable=False, index=True)
    recipient_email = Column(String(150), nullable=False)
    event_type = Column(String(60), nullable=False)
    message = Column(Text, nullable=False)
    status = Column(String(30), nullable=False, default="SENT")
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow)
