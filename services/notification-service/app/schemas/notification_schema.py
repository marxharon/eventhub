from datetime import datetime
from pydantic import BaseModel, Field


class NotificationSendRequest(BaseModel):
    userId: int = Field(..., description="ID do usuário destinatário")
    recipientEmail: str = Field(..., description="E-mail do destinatário")
    eventType: str = Field(..., description="Tipo de evento (ex.: REGISTRATION_CONFIRMED, PAYMENT_APPROVED, WAITLIST_PROMOTED)")
    message: str = Field(..., description="Conteúdo da mensagem enviada ao participante")


class NotificationResponse(BaseModel):
    id: int
    userId: int
    recipientEmail: str
    eventType: str
    message: str
    status: str
    createdAt: datetime


class FailureSimulationState(BaseModel):
    simulateFailure: bool
    message: str
