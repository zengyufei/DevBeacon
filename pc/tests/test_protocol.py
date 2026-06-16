import pytest

from devbeacon.protocol import make_payload, parse_envelope, payload_to_dict, sign_payload, signed_envelope, verify_signature


def test_signature_roundtrip():
    payload = payload_to_dict(make_payload("hello", "world"))
    signature = sign_payload(payload, "secret")
    assert verify_signature(payload, signature, "secret")
    assert not verify_signature({**payload, "title": "changed"}, signature, "secret")


def test_payload_defaults_to_unique_dedupe_key():
    payload = make_payload("hello", "world")
    assert payload.id
    assert payload.dedupeKey == payload.id
    assert payload.level == "info"


def test_status_payload_adds_optional_fields():
    payload = make_payload(
        "running",
        "started",
        event_type="status",
        state="running",
        run_id="run-1",
    )
    data = payload_to_dict(payload)
    assert data["eventType"] == "status"
    assert data["state"] == "running"
    assert data["runId"] == "run-1"


def test_invalid_status_state_is_rejected():
    with pytest.raises(ValueError):
        make_payload("bad", "bad", event_type="status", state="green")


def test_empty_secret_uses_unsigned_envelope():
    envelope = signed_envelope(make_payload("hello", "world"), "")
    assert "signature" not in envelope
    payload, error = parse_envelope(__import__("json").dumps(envelope).encode("utf-8"), "")
    assert error is None
    assert payload["title"] == "hello"


def test_secret_requires_valid_signature():
    envelope = signed_envelope(make_payload("hello", "world"), "secret")
    assert "signature" in envelope
    payload, error = parse_envelope(__import__("json").dumps(envelope).encode("utf-8"), "secret")
    assert error is None
    assert payload["title"] == "hello"
    _, error = parse_envelope(__import__("json").dumps(envelope).encode("utf-8"), "wrong")
    assert error == "invalid signature"
