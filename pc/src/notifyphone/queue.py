from __future__ import annotations

import threading
import time
from collections import deque
from dataclasses import dataclass, field
from typing import Any


@dataclass
class ClientSession:
    client_id: str
    connected_at: float = field(default_factory=time.time)
    last_seen_at: float = field(default_factory=time.time)
    delivered: int = 0


class NotificationQueue:
    def __init__(self, max_items: int = 200):
        self._condition = threading.Condition()
        self._items: deque[dict[str, Any]] = deque(maxlen=max_items)
        self._dedupe: set[str] = set()
        self._clients: dict[str, ClientSession] = {}

    def register_client(self, client_id: str) -> ClientSession:
        with self._condition:
            session = self._clients.get(client_id) or ClientSession(client_id=client_id)
            session.last_seen_at = time.time()
            self._clients[client_id] = session
            return session

    def client_snapshot(self) -> list[dict[str, Any]]:
        with self._condition:
            return [
                {
                    "clientId": session.client_id,
                    "connectedAt": session.connected_at,
                    "lastSeenAt": session.last_seen_at,
                    "delivered": session.delivered,
                }
                for session in self._clients.values()
            ]

    def enqueue(self, payload: dict[str, Any]) -> bool:
        key = str(payload.get("dedupeKey") or payload.get("id"))
        with self._condition:
            if key in self._dedupe:
                return False
            self._dedupe.add(key)
            self._items.append(payload)
            self._condition.notify_all()
            return True

    def wait_for_items(
        self,
        client_id: str,
        after_timestamp: int,
        timeout_seconds: int,
    ) -> list[dict[str, Any]]:
        deadline = time.monotonic() + timeout_seconds
        with self._condition:
            session = self._clients.get(client_id) or ClientSession(client_id=client_id)
            session.last_seen_at = time.time()
            self._clients[client_id] = session
            while True:
                items = [
                    item
                    for item in self._items
                    if int(item.get("timestamp", 0)) >= after_timestamp
                ]
                if items:
                    self._clients[client_id].delivered += len(items)
                    return items
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    return []
                self._condition.wait(timeout=remaining)
