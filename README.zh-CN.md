# DevBeacon

简体中文 | [English](README.md)

DevBeacon 是一个完全本地化的 PC 到 Android 开发状态提醒工具，主要用于 Claude Code 这类长时间运行的开发流程。手机端用一个极简红黄绿状态灯展示当前状态：绿色表示正在运行，黄色表示需要人工选择，红色表示完成或空闲。

默认策略优先省电。生产模式下 Android 主动轮询 PC server，不默认开启 HTTP/UDP 后台监听，也不持续 BLE 扫描。需要不启动 PC server 的一次性 CLI 直发时，可以手动开启直接接收模式。

## 界面预览

生产模式只保留状态灯、计时器和底部灯大小滑杆，适合把手机放在键盘旁边，当成 Claude Code 的外置状态灯使用。

<table>
  <tr>
    <td align="center"><strong>运行中</strong></td>
    <td align="center"><strong>等待选择</strong></td>
    <td align="center"><strong>空闲/完成</strong></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/运行中.jpg" width="220" alt="DevBeacon 运行中状态" /></td>
    <td><img src="docs/screenshots/选择中.jpg" width="220" alt="DevBeacon 等待选择状态" /></td>
    <td><img src="docs/screenshots/空闲中.jpg" width="220" alt="DevBeacon 空闲状态" /></td>
  </tr>
</table>

配置和诊断入口放在菜单里：生产模式用于日常观察，调试模式用于测试连接、查看收包状态和排查配置问题。

<table>
  <tr>
    <td align="center"><strong>修改配置</strong></td>
    <td align="center"><strong>调试模式</strong></td>
    <td align="center"><strong>安装界面</strong></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/配置.jpg" width="220" alt="DevBeacon 配置弹窗" /></td>
    <td><img src="docs/screenshots/调试模式.jpg" width="220" alt="DevBeacon 调试模式" /></td>
    <td><img src="docs/screenshots/安装界面.jpg" width="220" alt="DevBeacon Android 安装界面" /></td>
  </tr>
</table>

## PC 快速开始

```powershell
cd /D D:\cache\DevBeacon\pc
python -m devbeacon.cli serve --power-policy low
```

另开一个终端发送普通通知：

```powershell
cd /D D:\cache\DevBeacon\pc
python -m devbeacon.cli send --title "Build done" --body "Claude Code task finished"
```

默认情况下，`send` 只会投递到本机常驻 PC server。如果 server 没运行，命令会给出启动提示，不会自动退化成 IP 直发、广播或 BLE，因为那些模式对 Android 后台耗电和系统限制更敏感。

## Android 模式

生产模式是默认启动界面。它只显示红黄绿状态灯、计时器、底部大小滑杆和右上角菜单。

生产模式菜单包含：

- `修改配置`：修改 PC server URL、可选 shared secret，以及直接接收模式开关。
- `切换调试模式`：进入诊断界面。

调试模式保留连接状态、最后收到的消息、收到/丢弃计数、本机测试通知、PC 连接测试和手动启动客户端等工具。调试模式右上角菜单只提供切回生产模式。

## 配置

`Shared secret` 是可选的。默认两端都留空，消息不签名；如果你手动填写，PC 和 Android 必须填写同一个值，消息会使用 HMAC 校验。

查看或设置 PC 配置：

```powershell
python -m devbeacon.cli pair --show
python -m devbeacon.cli pair --server-host 127.0.0.1 --server-port 8765 --secret ""
```

Android 生产模式里通过 `菜单 -> 修改配置` 设置 PC 地址。通常应该填写 PC 的局域网地址，例如：

```text
http://192.168.0.10:8765
```

## 省电策略

`low` 是默认且推荐的策略。Android 使用低频长轮询连接 PC server，不主动开启直接接收监听，除非你显式打开。

`balanced` 会使用更短的重试和轮询间隔。

`ha` 是高可用策略，允许更频繁的轮询和 BLE 兜底策略，用更多耗电换取更低延迟。

## 直接接收模式

直接接收模式用于不启动 `devbeacon serve` 的 CLI 直发。进入 Android 生产模式：`菜单 -> 修改配置 -> 直接接收模式`，打开后 App 会监听 `8766` 端口，并在开关下方显示手机本机 IP。

使用示例：

```powershell
python -m devbeacon.cli event --state running --title "Claude Code" --body "Task started" --target ip --ip 192.168.0.124
python -m devbeacon.cli event --state done --title "Claude Code" --body "Task finished" --target ip --ip 192.168.0.124
```

CLI 仍保留 broadcast 参数，但 Android v1 的可运行直接接收路径是 HTTP `/notify`。

## BLE 状态

```powershell
python -m devbeacon.cli ble-check
```

BLE 当前是能力检测和兜底预留。架构上保留 PC BLE GATT server 与 Android BLE client 角色，但 v1 的主路径是低耗电 Wi-Fi client/server。

## Claude Code 状态事件

状态灯使用 `event` 命令驱动：

```powershell
python -m devbeacon.cli event --state running --title "Claude Code" --body "Task started"
python -m devbeacon.cli event --state attention --title "Claude Code" --body "Needs your choice"
python -m devbeacon.cli event --state done --title "Claude Code" --body "Task finished"
python -m devbeacon.cli event --state idle --title "Claude Code" --body "Idle"
```

Android 显示规则：`running` 是绿灯常亮并开始计时；`attention` 是黄灯快速闪烁并暂停计时；`done` 或 `idle` 是红灯慢闪并保留最终耗时，直到下一次 `running` 才重置。

`attention`、`done` 和 `idle` 即使没有先收到 `running` 也可以发送，CLI 会自动生成 run id。

## 测试清单

1. 安装 `android/app/build/outputs/apk/debug/app-debug.apk`。
2. 打开 Android App，默认应进入生产模式。
3. 通过 `菜单 -> 修改配置` 设置 PC URL。除非需要签名，否则 `Shared secret` 留空。
4. PC 端启动 server：`python -m devbeacon.cli serve --power-policy low`。
5. 另开终端发送状态事件。
6. 如果要测试不启动 server 的直发模式，打开 Android `直接接收模式`，复制显示的手机 IP，然后使用 `--target ip --ip <phone-ip>`。
