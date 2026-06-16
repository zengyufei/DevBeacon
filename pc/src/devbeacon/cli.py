from __future__ import annotations

import argparse
import json
import sys
import uuid
from typing import Any

from .ble import ble_server_capability, send_via_ble
from .config import CONFIG_PATH, load_config, save_config
from .direct import post_json, send_udp
from .protocol import SUPPORTED_STATUS_STATES, make_payload, signed_envelope
from .server import run_server


DIRECT_WARNING = (
    "Direct IP/broadcast requires Android Direct Receive mode. "
    "It is intentionally not used by default because it costs more battery."
)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="devbeacon")
    subparsers = parser.add_subparsers(dest="command", required=True)

    send_parser = subparsers.add_parser("send", help="Send one notification")
    send_parser.add_argument("--title", required=True)
    send_parser.add_argument("--body", required=True)
    send_parser.add_argument("--level", default="info", choices=["info", "warn", "error"])
    send_parser.add_argument("--source", default="cli")
    send_parser.add_argument("--ttl", type=int, default=300)
    send_parser.add_argument("--dedupe-key")
    send_parser.add_argument("--target", default="server", choices=["server", "ip", "broadcast", "ble"])
    send_parser.add_argument("--ip", help="Android phone IP for --target ip")
    send_parser.add_argument("--port", type=int, default=8766, help="Android direct receive port")

    event_parser = subparsers.add_parser("event", help="Send a Claude Code state event")
    event_parser.add_argument("--state", required=True, choices=sorted(SUPPORTED_STATUS_STATES))
    event_parser.add_argument("--title", required=True)
    event_parser.add_argument("--body", required=True)
    event_parser.add_argument("--source", default="claude-code")
    event_parser.add_argument("--ttl", type=int, default=300)
    event_parser.add_argument("--dedupe-key")
    event_parser.add_argument("--run-id")
    event_parser.add_argument("--target", default="server", choices=["server", "ip", "broadcast", "ble"])
    event_parser.add_argument("--ip", help="Android phone IP for --target ip")
    event_parser.add_argument("--port", type=int, default=8766, help="Android direct receive port")

    serve_parser = subparsers.add_parser("serve", help="Run the low-power PC server")
    serve_parser.add_argument("--host", default="0.0.0.0")
    serve_parser.add_argument("--port", type=int, default=8765)
    serve_parser.add_argument("--power-policy", default="low", choices=["low", "balanced", "ha"])

    pair_parser = subparsers.add_parser("pair", help="Save Android pairing values")
    pair_parser.add_argument("--client-id")
    pair_parser.add_argument("--secret")
    pair_parser.add_argument("--ip")
    pair_parser.add_argument("--server-host")
    pair_parser.add_argument("--server-port", type=int)
    pair_parser.add_argument("--show", action="store_true")

    subparsers.add_parser("ble-check", help="Check PC BLE GATT server capability")

    args = parser.parse_args(argv)
    config = load_config()

    if args.command == "send":
        return _send(args, config)
    if args.command == "event":
        return _event(args, config)
    if args.command == "serve":
        config.server_host = "127.0.0.1" if args.host == "0.0.0.0" else args.host
        config.server_port = args.port
        save_config(config)
        run_server(config, args.host, args.port, args.power_policy)
        return 0
    if args.command == "pair":
        return _pair(args, config)
    if args.command == "ble-check":
        ok, reason = ble_server_capability()
        print(json.dumps({"ok": ok, "message": reason}, ensure_ascii=False, indent=2))
        return 0 if ok else 2
    return 1


def _send(args: argparse.Namespace, config: Any) -> int:
    payload = make_payload(
        title=args.title,
        body=args.body,
        level=args.level,
        source=args.source,
        ttl_seconds=args.ttl,
        dedupe_key=args.dedupe_key,
    )
    envelope = signed_envelope(payload, config.shared_secret)

    if args.target == "server":
        ok, reason = post_json(f"{config.server_url}/api/notify", envelope)
        if ok:
            print(f"queued via local server: {reason}")
            return 0
        print(f"local server unavailable: {reason}", file=sys.stderr)
        print("Start it with: devbeacon serve --power-policy low", file=sys.stderr)
        print("No direct IP, broadcast, or BLE fallback was attempted by default.", file=sys.stderr)
        return 2

    if args.target == "ip":
        target_ip = args.ip or config.direct_ip
        if not target_ip:
            print("--target ip requires --ip or a paired direct_ip", file=sys.stderr)
            return 2
        print(DIRECT_WARNING, file=sys.stderr)
        ok, reason = post_json(f"http://{target_ip}:{args.port}/notify", envelope)
        print(reason)
        return 0 if ok else 2

    if args.target == "broadcast":
        print(DIRECT_WARNING, file=sys.stderr)
        ok, reason = send_udp("255.255.255.255", args.port, envelope, broadcast=True)
        print(reason)
        return 0 if ok else 2

    if args.target == "ble":
        ok, reason = send_via_ble()
        print(reason)
        return 0 if ok else 2

    return 1


def _event(args: argparse.Namespace, config: Any) -> int:
    if args.state == "running":
        run_id = args.run_id or config.active_run_id or str(uuid.uuid4())
        config.active_run_id = run_id
    else:
        run_id = args.run_id or config.active_run_id or str(uuid.uuid4())
        if args.state in {"done", "idle"}:
            config.active_run_id = ""

    payload = make_payload(
        title=args.title,
        body=args.body,
        level="info",
        source=args.source,
        ttl_seconds=args.ttl,
        dedupe_key=args.dedupe_key,
        event_type="status",
        state=args.state,
        run_id=run_id,
    )
    envelope = signed_envelope(payload, config.shared_secret)
    save_config(config)

    if args.target == "server":
        ok, reason = post_json(f"{config.server_url}/api/notify", envelope)
        if ok:
            print(json.dumps({"ok": True, "state": args.state, "runId": run_id, "transport": "server"}, ensure_ascii=False))
            return 0
        print(f"local server unavailable: {reason}", file=sys.stderr)
        print("Start it with: devbeacon serve --power-policy low", file=sys.stderr)
        return 2

    if args.target == "ip":
        target_ip = args.ip or config.direct_ip
        if not target_ip:
            print("--target ip requires --ip or a paired direct_ip", file=sys.stderr)
            return 2
        print(DIRECT_WARNING, file=sys.stderr)
        ok, reason = post_json(f"http://{target_ip}:{args.port}/notify", envelope)
        print(reason)
        return 0 if ok else 2

    if args.target == "broadcast":
        print(DIRECT_WARNING, file=sys.stderr)
        ok, reason = send_udp("255.255.255.255", args.port, envelope, broadcast=True)
        print(reason)
        return 0 if ok else 2

    if args.target == "ble":
        ok, reason = send_via_ble()
        print(reason)
        return 0 if ok else 2

    return 1


def _pair(args: argparse.Namespace, config: Any) -> int:
    if args.client_id:
        config.client_id = args.client_id
    if args.secret:
        config.shared_secret = args.secret
    if args.ip:
        config.direct_ip = args.ip
    if args.server_host:
        config.server_host = args.server_host
    if args.server_port:
        config.server_port = args.server_port
    save_config(config)
    if args.show:
        print(json.dumps(config.__dict__, ensure_ascii=False, indent=2))
    else:
        print(f"saved pairing config at {CONFIG_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
