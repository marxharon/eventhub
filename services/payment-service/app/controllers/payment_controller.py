from typing import Any, Dict, List
from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session
from app.config.auth import get_current_user
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
        "requirements": ["RF07", "RN02", "RN03", "RN05", "RN07", "RQ04", "RQ07"],
    }


@router.post("/initiate", response_model=PaymentResponse, status_code=status.HTTP_201_CREATED)
def initiate_payment(payload: PaymentInitiateRequest, db: Session = Depends(get_db)):
    return PaymentService(db).initiate(payload)


@router.post("/{payment_id}/confirm", response_model=PaymentResponse)
def confirm_payment(
    payment_id: int,
    current_user: Dict[str, Any] = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    return PaymentService(db).confirm(payment_id, current_user=current_user)


@router.post("/{payment_id}/refuse", response_model=PaymentResponse)
def refuse_payment(
    payment_id: int,
    current_user: Dict[str, Any] = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    return PaymentService(db).refuse(payment_id, current_user=current_user)


@router.post("/{payment_id}/expire", response_model=PaymentResponse)
def expire_payment(
    payment_id: int,
    current_user: Dict[str, Any] = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    return PaymentService(db).expire(payment_id, current_user=current_user)


@router.get("/registration/{registration_id}", response_model=List[PaymentResponse])
def get_payments_by_registration(
    registration_id: int,
    current_user: Dict[str, Any] = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    return PaymentService(db).list_by_registration(registration_id, current_user=current_user)


@router.post("/registration/{registration_id}/cancel-pending")
def cancel_pending_by_registration(
    registration_id: int,
    status: str = Query(default="EXPIRED"),
    db: Session = Depends(get_db),
):
    updated = PaymentService(db).cancel_pending_by_registration(registration_id, status)
    return {"registrationId": registration_id, "updatedPayments": updated}
