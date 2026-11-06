from datetime import datetime
from sqlalchemy import Column, BigInteger, String, Numeric, DateTime, Boolean
from app.config.database import Base


class PaymentModel(Base):
    __tablename__ = "payments"

    id = Column(BigInteger, primary_key=True, index=True)
    registration_id = Column(BigInteger, nullable=False, index=True)
    user_id = Column(BigInteger, nullable=False, index=True)
    amount = Column(Numeric(10, 2), nullable=False)
    status = Column(String(30), nullable=False, default="PENDING")
    registration_sync_pending = Column(Boolean, nullable=False, default=False, server_default="false")
    provider_ref = Column(String(100), nullable=False)
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow)
    updated_at = Column(DateTime, nullable=False, default=datetime.utcnow, onupdate=datetime.utcnow)
