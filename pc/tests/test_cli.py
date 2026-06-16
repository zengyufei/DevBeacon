from notifyphone.config import Config
from notifyphone.cli import main


def test_default_send_does_not_fallback_to_direct_transports(capsys, monkeypatch):
    monkeypatch.setattr("notifyphone.cli.load_config", lambda: Config(server_port=9))
    code = main(["send", "--title", "t", "--body", "b"])
    captured = capsys.readouterr()
    assert code == 2
    assert "No direct IP, broadcast, or BLE fallback was attempted by default." in captured.err


def test_event_allows_non_running_without_run_id(capsys, monkeypatch, tmp_path):
    monkeypatch.setattr("notifyphone.config.APP_DIR", tmp_path)
    monkeypatch.setattr("notifyphone.config.CONFIG_PATH", tmp_path / "config.json")
    code = main(["event", "--state", "attention", "--title", "pick", "--body", "needs input"])
    captured = capsys.readouterr()
    assert code == 2
    assert "No active run id" not in captured.err
    assert "local server unavailable" in captured.err
