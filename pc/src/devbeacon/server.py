from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import parse_qs, urlparse

from .config import Config
from .protocol import parse_envelope, signed_envelope
from .queue import NotificationQueue


class NotifyServer(ThreadingHTTPServer):
    def __init__(
        self,
        server_address: tuple[str, int],
        config: Config,
        power_policy: str,
    ):
        super().__init__(server_address, NotifyHandler)
        self.config = config
        self.power_policy = power_policy
        self.queue = NotificationQueue()


class NotifyHandler(BaseHTTPRequestHandler):
    server: NotifyServer

    def log_message(self, format: str, *args: Any) -> None:
        print(f"{self.address_string()} - {format % args}")

    def _send_json(self, status: int, data: dict[str, Any]) -> None:
        raw = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self._send_json(
                200,
                {
                    "ok": True,
                    "powerPolicy": self.server.power_policy,
                    "clients": self.server.queue.client_snapshot(),
                },
            )
            return
        if parsed.path == "/api/poll":
            params = parse_qs(parsed.query)
            client_id = params.get("clientId", [self.server.config.client_id])[0]
            after = int(params.get("after", ["0"])[0])
            timeout = int(params.get("timeout", ["25"])[0])
            timeout = max(5, min(timeout, 55))
            print(f"client poll: clientId={client_id} after={after} timeout={timeout}s")
            items = self.server.queue.wait_for_items(client_id, after, timeout)
            envelopes = [signed_envelope(_payload_from_dict(item), self.server.config.shared_secret) for item in items]
            print(f"client poll result: clientId={client_id} messages={len(items)}")
            self._send_json(
                200,
                {
                    "messages": envelopes,
                    "powerPolicy": self.server.power_policy,
                    "recommendedNextPollSeconds": _recommended_next_poll(self.server.power_policy, bool(items)),
                },
            )
            return
        self._send_json(404, {"ok": False, "error": "not found"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path != "/api/notify":
            self._send_json(404, {"ok": False, "error": "not found"})
            return

        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length)
        payload, error = parse_envelope(raw, self.server.config.shared_secret)
        if error:
            print(f"notify rejected: {error}")
            self._send_json(401, {"ok": False, "error": error})
            return
        queued = self.server.queue.enqueue(payload)
        print(
            "notify accepted: "
            f"title={payload.get('title', '')!r} "
            f"eventType={payload.get('eventType', '')!r} "
            f"state={payload.get('state', '')!r} queued={queued}"
        )
        self._send_json(202, {"ok": True, "queued": queued, "deduped": not queued})


def _recommended_next_poll(power_policy: str, had_message: bool) -> int:
    return 1


def run_server(config: Config, host: str, port: int, power_policy: str) -> None:
    httpd = NotifyServer((host, port), config, power_policy)
    print(f"DevBeacon server listening on http://{host}:{port}")
    print(f"power policy: {power_policy}; Android direct receive is not required in low mode")
    httpd.serve_forever()


def _payload_from_dict(data: dict[str, Any]):
    from .protocol import NotificationPayload

    return NotificationPayload(
        id=str(data.get("id", "")),
        timestamp=int(data.get("timestamp", 0)),
        title=str(data.get("title", "")),
        body=str(data.get("body", "")),
        level=str(data.get("level", "info")),
        source=str(data.get("source", "devbeacon")),
        ttlSeconds=int(data.get("ttlSeconds", 300)),
        dedupeKey=str(data.get("dedupeKey", data.get("id", ""))),
        eventType=data.get("eventType"),
        state=data.get("state"),
        runId=data.get("runId"),
    )
