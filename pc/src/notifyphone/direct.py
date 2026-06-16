from __future__ import annotations

import json
import socket
import urllib.error
import urllib.request
from typing import Any


def post_json(url: str, data: dict[str, Any], timeout: float = 2.0) -> tuple[bool, str]:
    body = json.dumps(data, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return 200 <= response.status < 300, f"HTTP {response.status}"
    except urllib.error.URLError as exc:
        return False, str(exc.reason)
    except TimeoutError:
        return False, "timeout"


def send_udp(host: str, port: int, data: dict[str, Any], broadcast: bool = False) -> tuple[bool, str]:
    raw = json.dumps(data, ensure_ascii=False).encode("utf-8")
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.settimeout(2.0)
            if broadcast:
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            sock.sendto(raw, (host, port))
        return True, "udp sent"
    except OSError as exc:
        return False, str(exc)
