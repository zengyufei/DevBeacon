from __future__ import annotations

import json
import threading
import time
import urllib.request

from devbeacon.config import Config
from devbeacon.protocol import make_payload, signed_envelope, verify_signature
from devbeacon.server import NotifyServer


def test_server_accepts_notify_and_poll_returns_signed_envelope():
    config = Config(shared_secret="secret", client_id="android-test")
    server = NotifyServer(("127.0.0.1", 0), config, "low")
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    host, port = server.server_address

    try:
        envelope = signed_envelope(make_payload("title", "body"), config.shared_secret)
        request = urllib.request.Request(
            f"http://{host}:{port}/api/notify",
            data=json.dumps(envelope).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=2) as response:
            assert response.status == 202

        with urllib.request.urlopen(
            f"http://{host}:{port}/api/poll?clientId=android-test&after=0&timeout=5",
            timeout=7,
        ) as response:
            data = json.loads(response.read().decode("utf-8"))
            assert data["powerPolicy"] == "low"
            assert data["messages"]
            returned = data["messages"][0]
            assert verify_signature(returned["payload"], returned["signature"], config.shared_secret)
    finally:
        server.shutdown()
        thread.join(timeout=3)
