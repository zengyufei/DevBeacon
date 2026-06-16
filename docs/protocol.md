# DevBeacon Protocol

All transports carry the same envelope. `signature` is optional and only appears when `shared_secret` is configured:

```json
{
  "payload": {
    "id": "uuid",
    "timestamp": 1710000000,
    "title": "Build done",
    "body": "Task finished",
    "level": "info",
    "source": "claude-code",
    "ttlSeconds": 300,
    "dedupeKey": "uuid-or-stable-key"
  }
}
```

Signed mode:

```json
{
  "payload": {
    "id": "uuid",
    "timestamp": 1710000000,
    "title": "Build done",
    "body": "Task finished",
    "level": "info",
    "source": "claude-code",
    "ttlSeconds": 300,
    "dedupeKey": "uuid-or-stable-key"
  },
  "signature": "base64url-hmac-sha256"
}
```

The signature is HMAC-SHA256 over canonical JSON for `payload`. Empty `shared_secret` means unsigned mode.

Status events add:

```json
{
  "eventType": "status",
  "state": "running",
  "runId": "uuid"
}
```

Allowed states are `running`, `attention`, `done`, and `idle`.

## PC server endpoints

`POST /api/notify`

Accepts a signed envelope and queues the payload for Android clients.

`GET /api/poll?clientId=<id>&after=<timestamp>&timeout=<seconds>`

Android long-polls this endpoint. The response contains signed envelopes in `messages` and a `recommendedNextPollSeconds` hint. This keeps Android in a client role and avoids background UDP/HTTP listening in low-power mode.

`GET /health`

Returns server status, power policy, and connected client snapshots.

## Low-power invariant

The default path never starts Android direct receive listeners and never starts continuous BLE scanning. Direct receive and high availability BLE are user-enabled modes.

## Direct receive endpoint

When Android direct receive mode is enabled, the app listens on:

`POST http://<android-ip>:8766/notify`

The request body is the same envelope. In unsigned mode, no `signature` is required. This endpoint is for `devbeacon ... --target ip --ip <android-ip>` and does not require `devbeacon serve`.
