from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time
import uuid
from dataclasses import dataclass
from typing import Any


SUPPORTED_LEVELS = {"info", "warn", "error"}
SUPPORTED_STATUS_STATES = {"running", "attention", "idle", "done"}


@dataclass(frozen=True)
class NotificationPayload:
    id: str
    timestamp: int
    title: str
    body: str
    level: str
    source: str
    ttlSeconds: int
    dedupeKey: str
    eventType: str | None = None
    state: str | None = None
    runId: str | None = None


def canonical_json(data: dict[str, Any]) -> str:
    return json.dumps(data, ensure_ascii=False, separators=(",", ":"), sort_keys=True)


def make_payload(
    title: str,
    body: str,
    level: str = "info",
    source: str = "notifyphone",
    ttl_seconds: int = 300,
    dedupe_key: str | None = None,
    event_type: str | None = None,
    state: str | None = None,
    run_id: str | None = None,
) -> NotificationPayload:
    if level not in SUPPORTED_LEVELS:
        raise ValueError(f"level must be one of {sorted(SUPPORTED_LEVELS)}")
    if state is not None and state not in SUPPORTED_STATUS_STATES:
        raise ValueError(f"state must be one of {sorted(SUPPORTED_STATUS_STATES)}")
    message_id = str(uuid.uuid4())
    return NotificationPayload(
        id=message_id,
        timestamp=int(time.time()),
        title=title,
        body=body,
        level=level,
        source=source,
        ttlSeconds=ttl_seconds,
        dedupeKey=dedupe_key or message_id,
        eventType=event_type,
        state=state,
        runId=run_id,
    )


def payload_to_dict(payload: NotificationPayload) -> dict[str, Any]:
    return {
        "id": payload.id,
        "timestamp": payload.timestamp,
        "title": payload.title,
        "body": payload.body,
        "level": payload.level,
        "source": payload.source,
        "ttlSeconds": payload.ttlSeconds,
        "dedupeKey": payload.dedupeKey,
        **({"eventType": payload.eventType} if payload.eventType is not None else {}),
        **({"state": payload.state} if payload.state is not None else {}),
        **({"runId": payload.runId} if payload.runId is not None else {}),
    }


def sign_payload(payload: dict[str, Any], shared_secret: str) -> str:
    digest = hmac.new(
        shared_secret.encode("utf-8"),
        canonical_json(payload).encode("utf-8"),
        hashlib.sha256,
    ).digest()
    return base64.urlsafe_b64encode(digest).decode("ascii").rstrip("=")


def verify_signature(payload: dict[str, Any], signature: str, shared_secret: str) -> bool:
    expected = sign_payload(payload, shared_secret)
    return hmac.compare_digest(expected, signature)


def signed_envelope(payload: NotificationPayload, shared_secret: str) -> dict[str, Any]:
    data = payload_to_dict(payload)
    envelope: dict[str, Any] = {"payload": data}
    if shared_secret.strip():
        envelope["signature"] = sign_payload(data, shared_secret)
    return envelope


def parse_envelope(raw: bytes, shared_secret: str) -> tuple[dict[str, Any], str | None]:
    try:
        envelope = json.loads(raw.decode("utf-8"))
    except json.JSONDecodeError as exc:
        return {}, f"invalid json: {exc}"
    if not isinstance(envelope, dict):
        return {}, "envelope must be an object"
    payload = envelope.get("payload")
    signature = envelope.get("signature", "")
    if not isinstance(payload, dict):
        return {}, "envelope requires payload object"
    if shared_secret.strip() and not isinstance(signature, str):
        return {}, "signed mode requires signature string"
    if shared_secret.strip() and not verify_signature(payload, signature, shared_secret):
        return {}, "invalid signature"
    return payload, None
