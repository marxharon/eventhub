from datetime import datetime
from pydantic import BaseModel, Field


class PaymentInitiateRequest(BaseModel):
    registrationId: int = Field(..., description="ID da inscrição em atividade paga (PENDING_PAYMENT)")
    userId: int = Field(..., description="ID do participante pagador")
    amount: float = Field(..., gt=0, description="Valor da cobrança")


class PaymentResponse(BaseModel):
    id: int
    registrationId: int
    userId: int
    amount: float
    status: str
    providerRef: str
    createdAt: datetime
    updatedAt: datetime
