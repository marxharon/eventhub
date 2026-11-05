import os
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    service_name: str = "notification-service"
    service_port: int = 8086
    postgres_host: str = os.getenv("POSTGRES_HOST", "postgres")
    postgres_port: int = int(os.getenv("POSTGRES_PORT", "5432"))
    postgres_db: str = os.getenv("POSTGRES_DB", "eventhub_db")
    postgres_user: str = os.getenv("POSTGRES_USER", "eventhub")
    postgres_password: str = os.getenv("POSTGRES_PASSWORD", "eventhub_secret_2026")

    @property
    def database_url(self) -> str:
        return (
            f"postgresql://{self.postgres_user}:{self.postgres_password}"
            f"@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"
        )


settings = Settings()
