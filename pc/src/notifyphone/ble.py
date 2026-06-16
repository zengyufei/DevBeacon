from __future__ import annotations

import platform


def ble_server_capability() -> tuple[bool, str]:
    if platform.system() != "Windows":
        return False, "BLE GATT server mode is planned for Windows only in v1."
    return (
        False,
        "BLE GATT server transport needs the Windows Runtime bridge implementation. "
        "Wi-Fi low-power mode is available now; BLE is exposed as a capability-checked fallback stub.",
    )


def send_via_ble() -> tuple[bool, str]:
    ok, reason = ble_server_capability()
    if not ok:
        return False, reason
    return False, "BLE transport is not implemented in this build."
