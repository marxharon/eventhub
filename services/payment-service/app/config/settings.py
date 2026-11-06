import os
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    service_name: str = "payment-service"
    service_port: int = 8085
    postgres_host: str = os.getenv("POSTGRES_HOST", "postgres")
    postgres_port: int = int(os.getenv("POSTGRES_PORT", "5432"))
    postgres_db: str = os.getenv("POSTGRES_DB", "eventhub_db")
    postgres_user: str = os.getenv("POSTGRES_USER", "eventhub")
    postgres_password: str = os.getenv("POSTGRES_PASSWORD", "eventhub_secret_2026")
    jwt_secret: str = os.getenv(
        "JWT_SECRET",
        "EventHubSuperSecretKeyForJwtAuthentication2026MicroservicesArchitecture",
    )
    registration_service_url: str = os.getenv("REGISTRATION_SERVICE_URL", "http://registration-service:8084")
    notification_service_url: str = os.getenv("NOTIFICATION_SERVICE_URL", "http://notification-service:8086")
    downstream_timeout_seconds: float = 1.5
    rabbitmq_host: str = os.getenv("RABBITMQ_HOST", "rabbitmq")
    rabbitmq_port: int = int(os.getenv("RABBITMQ_PORT", "5672"))
    rabbitmq_user: str = os.getenv("RABBITMQ_USER", "eventhub")
    rabbitmq_password: str = os.getenv("RABBITMQ_PASSWORD", "eventhub_secret_2026")
    messaging_enabled: bool = os.getenv("MESSAGING_ENABLED", "true").lower() == "true"

    @property
    def database_url(self) -> str:
        return (
            f"postgresql://{self.postgres_user}:{self.postgres_password}"
            f"@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"
        )


settings = Settings()
