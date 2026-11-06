from typing import Any, Dict, List
from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session
from app.config.auth import get_current_user
from app.config.database import get_db
from app.schemas.notification_schema import (
    NotificationSendRequest,
    NotificationResponse,
    FailureSimulationState,
    DeletedCountResponse,
)
from app.services.notification_service import NotificationService

router = APIRouter(prefix="/api/notifications", tags=["Notifications (RF08, RN05)"])


@router.get("/health")
def health():
    return {
        "status": "UP",
        "service": "notification-service",
        "stack": "Python 3.12 / FastAPI",
        "port": 8086,
        "simulateFailure": NotificationService.get_failure_state(),
        "requirements": ["RF08", "RN05", "RN07", "RQ01", "RQ03", "RQ07"],
    }


@router.post("/send", response_model=NotificationResponse, status_code=status.HTTP_201_CREATED)
def send_notification(
    payload: NotificationSendRequest,
    db: Session = Depends(get_db),
):
    service = NotificationService(db)
    return service.send_notification(payload)


@router.get("/user/{user_id}", response_model=List[NotificationResponse])
def get_user_notifications(
    user_id: int,
    current_user: Dict[str, Any] = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    service = NotificationService(db)
    return service.list_user_notifications(user_id, current_user=current_user)


@router.get("", response_model=List[NotificationResponse])
def list_all_notifications(
    current_user: Dict[str, Any] = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    service = NotificationService(db)
    return service.list_all_notifications(current_user=current_user)


@router.patch("/{notification_id}/read", response_model=NotificationResponse)
def mark_notification_as_read(
    notification_id: int,
    current_user: Dict[str, Any] = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    service = NotificationService(db)
    return service.mark_as_read(notification_id, current_user=current_user)


@router.delete("/{notification_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_notification(
    notification_id: int,
    current_user: Dict[str, Any] = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    service = NotificationService(db)
    service.delete_notification(notification_id, current_user=current_user)


@router.delete("/user/{user_id}/read", response_model=DeletedCountResponse)
def delete_read_notifications(
    user_id: int,
    current_user: Dict[str, Any] = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    service = NotificationService(db)
    return service.delete_read_notifications(user_id, current_user=current_user)


@router.get("/simulate-failure", response_model=FailureSimulationState)
def get_failure_simulation_state():
    fail = NotificationService.get_failure_state()
    return NotificationService.set_failure_state(fail)


@router.post("/simulate-failure", response_model=FailureSimulationState)
def toggle_failure_simulation(
    fail: bool = Query(..., description="Define se o serviço deve simular falha HTTP 503 (true/false)"),
):
    return NotificationService.set_failure_state(fail)
