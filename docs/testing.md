# Testing notifyPhone

## Build

```powershell
cd /D D:\cache\notifyPhone
gradle -p android :app:assembleDebug
cd pc
python -m pytest
```

Install:

```text
D:\cache\notifyPhone\android\app\build\outputs\apk\debug\app-debug.apk
```

## Server mode test

Start the PC server:

```powershell
cd /D D:\cache\notifyPhone\pc
python -m notifyphone.cli serve --power-policy low
```

In Android production mode, set:

```text
PC server URL = http://<pc-lan-ip>:8765
Shared secret = empty
```

Send events from a second terminal:

```powershell
python -m notifyphone.cli event --state running --title "Claude Code" --body "Task started"
python -m notifyphone.cli event --state attention --title "Claude Code" --body "Need your choice"
python -m notifyphone.cli event --state done --title "Claude Code" --body "Task finished"
```

Expected UI:

- `running`: steady green lamp, timer counting.
- `attention`: fast flashing yellow lamp, timer paused.
- `done` or `idle`: slow flashing red lamp, final timer held.

## Direct receive test

In Android production mode, open `菜单 -> 修改配置`, enable `直接接收模式`, save, and copy the displayed phone IP.

No PC server is needed:

```powershell
python -m notifyphone.cli event --state running --title "Claude Code" --body "Task started" --target ip --ip <android-ip>
python -m notifyphone.cli event --state done --title "Claude Code" --body "Task finished" --target ip --ip <android-ip>
```

If this times out, check that the phone and PC are on the same LAN and that direct receive mode is enabled after installing the latest APK.
