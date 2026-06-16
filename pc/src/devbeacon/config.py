from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path


APP_DIR = Path.home() / ".devbeacon"
CONFIG_PATH = APP_DIR / "config.json"


@dataclass
class Config:
    server_host: str = "127.0.0.1"
    server_port: int = 8765
    shared_secret: str = ""
    client_id: str = "android-default"
    active_run_id: str = ""
    direct_ip: str = ""
    ble_service_uuid: str = "8d8f3f0a-0d7c-4f9f-ae75-9aef7a2d5f10"

    @property
    def server_url(self) -> str:
        return f"http://{self.server_host}:{self.server_port}"


def load_config() -> Config:
    if not CONFIG_PATH.exists():
        config = Config()
        save_config(config)
        return config

    data = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    config = Config()
    for key in config.__dataclass_fields__:
        if key in data:
            setattr(config, key, data[key])
    return config


def save_config(config: Config) -> None:
    APP_DIR.mkdir(parents=True, exist_ok=True)
    CONFIG_PATH.write_text(
        json.dumps(config.__dict__, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
