import base64
import hashlib
import hmac
import json
import time
from typing import Dict, Any, Optional
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from app.config.settings import settings

_bearer_scheme = HTTPBearer(scheme_name="bearerAuth", auto_error=False)

_ALG_MAP = {
    "HS256": hashlib.sha256,
    "HS384": hashlib.sha384,
    "HS512": hashlib.sha512,
}


def _b64url_decode(segment: str) -> bytes:
    padding = "=" * (-len(segment) % 4)
    return base64.urlsafe_b64decode(segment + padding)


def verify_jwt_token(token: str) -> Dict[str, Any]:
    parts = token.split(".")
    if len(parts) != 3:
        raise ValueError("Formato JWT inválido")

    header_b64, payload_b64, sig_b64 = parts
    header = json.loads(_b64url_decode(header_b64).decode("utf-8"))
    alg = header.get("alg", "HS512")
    digest_mod = _ALG_MAP.get(alg)
    if digest_mod is None:
        raise ValueError(f"Algoritmo JWT não suportado: {alg}")

    signing_input = f"{header_b64}.{payload_b64}".encode("ascii")
    expected_sig = hmac.new(
        settings.jwt_secret.encode("utf-8"),
        signing_input,
        digest_mod,
    ).digest()
    actual_sig = _b64url_decode(sig_b64)
    if not hmac.compare_digest(expected_sig, actual_sig):
        raise ValueError("Assinatura JWT inválida")

    payload = json.loads(_b64url_decode(payload_b64).decode("utf-8"))
    exp = payload.get("exp")
    if exp is not None and time.time() > float(exp):
        raise ValueError("Token JWT expirado")

    user_id = payload.get("userId")
    role = payload.get("role")
    if user_id is None or role is None:
        raise ValueError("Claims obrigatórias ausentes no JWT")

    return {"userId": int(user_id), "role": str(role), "email": payload.get("email")}


def get_current_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(_bearer_scheme),
) -> Dict[str, Any]:
    if credentials is None or credentials.scheme.lower() != "bearer" or not credentials.credentials:
        raise HTTPException(
            status_code=401,
            detail="Token JWT ausente ou inválido (RN07).",
        )
    token = credentials.credentials.strip()
    try:
        return verify_jwt_token(token)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=401,
            detail="Token JWT ausente ou inválido (RN07).",
        ) from exc
