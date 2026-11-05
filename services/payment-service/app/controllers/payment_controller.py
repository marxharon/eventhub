from typing import List
from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session
from app.config.database import get_db
from app.schemas.payment_schema import PaymentInitiateRequest, PaymentResponse
from app.services.payment_service import PaymentService

router = APIRouter(prefix="/api/payments", tags=["Payments (RF07, RN02, RN03)"])


@router.get("/health")
def health():
    return {
        "status": "UP",
        "service": "payment-service",
        "stack": "Python 3.12 / FastAPI",
        "port": 8085,
        "requirements": ["RF07", "RN02", "RN03", "RN05", "RQ04"],
    }


@router.post("/initiate", response_model=PaymentResponse, status_code=status.HTTP_201_CREATED)
def initiate_payment(payload: PaymentInitiateRequest, db: Session = Depends(get_db)):
    return PaymentService(db).initiate(payload)


@router.post("/{payment_id}/confirm", response_model=PaymentResponse)
def confirm_payment(payment_id: int, db: Session = Depends(get_db)):
    return PaymentService(db).confirm(payment_id)


@router.post("/{payment_id}/refuse", response_model=PaymentResponse)
def refuse_payment(payment_id: int, db: Session = Depends(get_db)):
    return PaymentService(db).refuse(payment_id)


@router.post("/{payment_id}/expire", response_model=PaymentResponse)
def expire_payment(payment_id: int, db: Session = Depends(get_db)):
    return PaymentService(db).expire(payment_id)


@router.get("/registration/{registration_id}", response_model=List[PaymentResponse])
def get_payments_by_registration(registration_id: int, db: Session = Depends(get_db)):
    return PaymentService(db).list_by_registration(registration_id)
