from typing import List, Optional
from sqlalchemy.orm import Session
from app.models.notification_model import NotificationModel


class NotificationRepository:
    def __init__(self, db: Session):
        self.db = db

    def create(
        self,
        user_id: int,
        recipient_email: str,
        event_type: str,
        message: str,
        status: str = "SENT",
    ) -> NotificationModel:
        notification = NotificationModel(
            user_id=user_id,
            recipient_email=recipient_email,
            event_type=event_type,
            message=message,
            status=status,
        )
        self.db.add(notification)
        self.db.commit()
        self.db.refresh(notification)
        return notification

    def find_by_user(self, user_id: int) -> List[NotificationModel]:
        return (
            self.db.query(NotificationModel)
            .filter(NotificationModel.user_id == user_id)
            .order_by(NotificationModel.created_at.desc())
            .limit(50)
            .all()
        )

    def find_all(self) -> List[NotificationModel]:
        return (
            self.db.query(NotificationModel)
            .order_by(NotificationModel.created_at.desc())
            .limit(100)
            .all()
        )

    def find_by_id(self, notification_id: int) -> Optional[NotificationModel]:
        return self.db.query(NotificationModel).filter(NotificationModel.id == notification_id).first()

    def mark_as_read(self, notification: NotificationModel) -> NotificationModel:
        notification.is_read = True
        self.db.commit()
        self.db.refresh(notification)
        return notification

    def delete(self, notification: NotificationModel) -> None:
        self.db.delete(notification)
        self.db.commit()

    def delete_read_by_user(self, user_id: int) -> int:
        deleted = (
            self.db.query(NotificationModel)
            .filter(NotificationModel.user_id == user_id, NotificationModel.is_read.is_(True))
            .delete(synchronize_session=False)
        )
        self.db.commit()
        return deleted
