package com.notifyphone.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayDeque
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL

class MainActivity : Activity() {
    private lateinit var configStore: ConfigStore
    private lateinit var statusStore: StatusStore
    private lateinit var runtimeStore: RuntimeStatusStore
    private lateinit var uiModeStore: UiModeStore
    private lateinit var lampView: StatusLampView
    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private lateinit var lastEventText: TextView
    private lateinit var connectionText: TextView
    private lateinit var actionText: TextView
    private lateinit var permissionText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val actionLog = ArrayDeque<String>()
    private var statusReceiverRegistered = false
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatusUi()
            handler.postDelayed(this, 500L)
        }
    }
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshStatusUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configStore = ConfigStore(this)
        statusStore = StatusStore(this)
        runtimeStore = RuntimeStatusStore(this)
        uiModeStore = UiModeStore(this)
        NotificationHelper(this).ensureChannels()
        render()
        requestNotificationPermission()
        if (uiModeStore.load() == UiMode.PRODUCTION) {
            startClient("生产模式自动启动客户端", silent = true)
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(StatusBroadcaster.ACTION_STATUS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
        statusReceiverRegistered = true
        handler.post(refreshRunnable)
        if (::uiModeStore.isInitialized && uiModeStore.load() == UiMode.PRODUCTION) {
            startClient("生产模式恢复自动启动客户端", silent = true)
        }
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        if (statusReceiverRegistered) {
            unregisterReceiver(statusReceiver)
            statusReceiverRegistered = false
        }
        super.onPause()
    }

    private fun render() {
        if (uiModeStore.load() == UiMode.PRODUCTION) {
            renderProduction()
        } else {
            renderDebug()
        }
    }

    private fun renderProduction() {
        val snapshot = statusStore.load()
        val config = configStore.load()
        val frame = FrameLayout(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        val menuButton = Button(this).apply {
            text = "菜单"
            setOnClickListener { showProductionMenu(this) }
        }
        frame.addView(
            menuButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.END
            )
        )
        lampView = StatusLampView(this).apply {
            setScalePercent(config.lampScalePercent)
            update(snapshot)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        timerText = TextView(this).apply {
            textSize = 24f
            gravity = android.view.Gravity.CENTER
        }
        lastEventText = TextView(this).apply {
            textSize = 15f
            gravity = android.view.Gravity.CENTER
        }
        connectionText = TextView(this).apply {
            textSize = 13f
            gravity = android.view.Gravity.CENTER
        }
        permissionText = TextView(this).apply {
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            text = notificationPermissionSummary()
        }
        actionText = TextView(this).apply {
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            text = "生产模式"
        }
        statusText = TextView(this).apply {
            text = ""
        }

        root.addView(lampView)
        root.addView(timerText)
        frame.addView(
            root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        frame.addView(
            makeLampScaleSeekBar(),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM
            )
        )
        setContentView(frame)
        refreshStatusUi()
    }

    private fun renderDebug() {
        val config = configStore.load()
        val snapshot = statusStore.load()
        val frame = FrameLayout(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val menuButton = Button(this).apply {
            text = "菜单"
            setOnClickListener { showDebugMenu(this) }
        }
        frame.addView(
            menuButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.END
            )
        )
        lampView = StatusLampView(this).apply {
            setScalePercent(config.lampScalePercent)
            update(snapshot)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                420
            )
        }
        timerText = TextView(this).apply {
            textSize = 16f
        }
        lastEventText = TextView(this).apply {
            textSize = 13f
        }
        connectionText = TextView(this).apply {
            textSize = 14f
        }
        permissionText = TextView(this).apply {
            textSize = 13f
            text = notificationPermissionSummary()
        }
        actionText = TextView(this).apply {
            textSize = 15f
            text = "等待操作"
        }

        val serverInput = EditText(this).apply {
            hint = "PC server URL"
            setText(config.serverUrl)
        }
        val secretInput = EditText(this).apply {
            hint = "Shared secret"
            setText(config.sharedSecret)
        }
        val directSwitch = Switch(this).apply {
            text = "直接接收模式：允许 CLI 直发 IP/广播（更耗电，默认关闭）"
            isChecked = config.directReceiveEnabled
        }
        val haSwitch = Switch(this).apply {
            text = "高可用模式：允许更频繁轮询和 BLE 兜底（更耗电）"
            isChecked = config.powerMode == PowerMode.HIGH_AVAILABILITY
        }
        statusText = TextView(this).apply {
            text = buildStatus(config)
            textSize = 14f
        }

        val saveButton = Button(this).apply {
            text = "保存配置"
            setOnClickListener {
                val serverUrl = serverInput.text.toString().trim()
                val sharedSecret = secretInput.text.toString().trim()
                if (serverUrl.isBlank()) {
                    announce("保存失败：PC server URL 不能为空")
                    return@setOnClickListener
                }
                val mode = if (haSwitch.isChecked) PowerMode.HIGH_AVAILABILITY else PowerMode.LOW
                configStore.save(
                    config.copy(
                        serverUrl = serverUrl,
                        sharedSecret = sharedSecret,
                        powerMode = mode,
                        directReceiveEnabled = directSwitch.isChecked,
                        bleHighAvailabilityEnabled = haSwitch.isChecked
                    )
                )
                statusText.text = buildStatus(configStore.load())
                announce("配置已保存")
            }
        }
        val startButton = Button(this).apply {
            text = "启动低耗电客户端"
            setOnClickListener {
                startClient("手动启动低耗电客户端")
            }
        }
        val testButton = Button(this).apply {
            text = "显示本机测试通知"
            setOnClickListener {
                if (!notificationPermissionGranted()) {
                    requestNotificationPermission()
                    announce("通知权限未开启，已请求授权")
                    return@setOnClickListener
                }
                try {
                    NotificationHelper(this@MainActivity).showMessage(
                        NotifyMessage(
                            id = "local-test",
                            timestamp = System.currentTimeMillis() / 1000,
                            title = "notifyPhone test",
                            body = "Android notification rendering works.",
                            level = "info",
                            source = "android",
                            ttlSeconds = 60,
                            dedupeKey = "local-test-${System.currentTimeMillis()}"
                        )
                    )
                    announce("测试通知已发送")
                } catch (error: Exception) {
                    announce("测试通知失败：${error.message ?: "未知错误"}")
                }
            }
        }
        val healthButton = Button(this).apply {
            text = "测试 PC 连接"
            setOnClickListener {
                val serverUrl = serverInput.text.toString().trim()
                if (serverUrl.isBlank()) {
                    announce("连接测试失败：PC server URL 为空")
                    return@setOnClickListener
                }
                Thread {
                    val result = testHealth(serverUrl)
                    runOnUiThread {
                        announce(result)
                        refreshStatusUi()
                    }
                }.start()
            }
        }

        root.addView(TextView(this).apply {
            text = "notifyPhone"
            textSize = 26f
        })
        root.addView(lampView)
        root.addView(TextView(this).apply {
            text = "Claude Code 状态灯"
            textSize = 20f
        })
        root.addView(timerText)
        root.addView(lastEventText)
        root.addView(connectionText)
        root.addView(permissionText)
        root.addView(actionText)
        root.addView(TextView(this).apply {
            text = "默认低耗电：不开 HTTP/UDP 监听，不持续 BLE 扫描；Android 主动轮询 PC server。"
        })
        root.addView(serverInput)
        root.addView(secretInput)
        root.addView(directSwitch)
        root.addView(haSwitch)
        root.addView(saveButton)
        root.addView(startButton)
        root.addView(testButton)
        root.addView(healthButton)
        root.addView(statusText)

        val scrollView = ScrollView(this).apply { addView(root) }
        frame.addView(
            scrollView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(frame)
        refreshStatusUi()
    }

    private fun showProductionMenu(anchor: android.view.View) {
        PopupMenu(this, anchor).apply {
            menu.add("修改配置")
            menu.add("切换调试模式")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "修改配置" -> {
                        showProductionConfigDialog()
                        true
                    }
                    "切换调试模式" -> {
                        uiModeStore.save(UiMode.DEBUG)
                        announce("已切换调试模式")
                        render()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showDebugMenu(anchor: android.view.View) {
        PopupMenu(this, anchor).apply {
            menu.add("切换生产模式")
            setOnMenuItemClickListener { item ->
                if (item.title.toString() == "切换生产模式") {
                    uiModeStore.save(UiMode.PRODUCTION)
                    announce("已切换生产模式")
                    render()
                    startClient("切换生产模式自动启动客户端", silent = true)
                    true
                } else {
                    false
                }
            }
            show()
        }
    }

    private fun showProductionConfigDialog() {
        val config = configStore.load()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }
        val serverInput = EditText(this).apply {
            hint = "PC server URL"
            setText(config.serverUrl)
        }
        val secretInput = EditText(this).apply {
            hint = "Shared secret（可留空）"
            setText(config.sharedSecret)
        }
        val directSwitch = Switch(this).apply {
            text = "直接接收模式（用于无 server 的 CLI 直发，默认关闭）"
            isChecked = config.directReceiveEnabled
        }
        val localIpText = TextView(this).apply {
            textSize = 13f
            text = localIpSummary()
            visibility = if (directSwitch.isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }
        directSwitch.setOnCheckedChangeListener { _, isChecked ->
            localIpText.text = localIpSummary()
            localIpText.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }
        layout.addView(serverInput)
        layout.addView(secretInput)
        layout.addView(directSwitch)
        layout.addView(localIpText)
        AlertDialog.Builder(this)
            .setTitle("修改配置")
            .setView(layout)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val serverUrl = serverInput.text.toString().trim()
                if (serverUrl.isBlank()) {
                    announce("保存失败：PC server URL 不能为空")
                    return@setPositiveButton
                }
                configStore.save(
                    config.copy(
                        serverUrl = serverUrl,
                        sharedSecret = secretInput.text.toString().trim(),
                        powerMode = PowerMode.LOW,
                        directReceiveEnabled = directSwitch.isChecked,
                        bleHighAvailabilityEnabled = false
                    )
                )
                announce("配置已保存，正在重启客户端")
                restartClient("配置变更后重启客户端")
            }
            .show()
    }

    private fun makeLampScaleSeekBar(): SeekBar {
        return SeekBar(this).apply {
            max = LAMP_MAX_SCALE - LAMP_MIN_SCALE
            progress = configStore.load().lampScalePercent - LAMP_MIN_SCALE
            setPadding(48, 24, 48, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val scale = LAMP_MIN_SCALE + progress
                    lampView.setScalePercent(scale)
                    if (fromUser) {
                        saveLampScale(scale)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val scale = LAMP_MIN_SCALE + (seekBar?.progress ?: 0)
                    saveLampScale(scale)
                }
            })
        }
    }

    private fun saveLampScale(scale: Int) {
        val config = configStore.load()
        configStore.save(config.copy(lampScalePercent = scale.coerceIn(LAMP_MIN_SCALE, LAMP_MAX_SCALE)))
    }

    private fun buildStatus(config: NotifyConfig): String {
        return """
            Client ID: ${config.clientId}
            Mode: ${config.powerMode.displayName}
            Direct receive: ${if (config.directReceiveEnabled) "enabled" else "off"}
            BLE policy: ${BleFallback(this).status(false)}
            Direct receive service: ${if (config.directReceiveEnabled) "HTTP listener will run on port ${DirectReceiveService.PORT}" else "off"}
        """.trimIndent()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
        if (::permissionText.isInitialized) {
            permissionText.text = notificationPermissionSummary()
        }
    }

    private fun notificationPermissionGranted(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshStatusUi() {
        val snapshot = statusStore.load()
        val runtime = runtimeStore.load()
        val elapsed = LampStateMachine.runningMillis(snapshot, System.currentTimeMillis())
        lampView.update(snapshot)
        timerText.text = if (::uiModeStore.isInitialized && uiModeStore.load() == UiMode.PRODUCTION) {
            formatDuration(elapsed)
        } else {
            "${labelFor(snapshot.state)} · ${formatDuration(elapsed)}"
        }
        lastEventText.text = "Last: ${snapshot.lastStateLabel} ${snapshot.lastTitle} ${snapshot.lastBody}".trim()
        connectionText.text = """
            连接状态：${runtime.serviceStatus}
            最后收到：${runtime.lastReceived}
            收到/丢弃：${runtime.receivedCount}/${runtime.droppedCount}
            错误：${runtime.lastError.ifBlank { "无" }}
        """.trimIndent()
        permissionText.text = notificationPermissionSummary()
    }

    private fun notificationPermissionSummary(): String {
        return if (notificationPermissionGranted()) {
            "通知权限：已开启"
        } else {
            "通知权限：未开启，测试通知和前台服务提醒可能不可见"
        }
    }

    private fun announce(message: String) {
        val stamped = "${formatTime(System.currentTimeMillis())}  $message"
        actionLog.addFirst(stamped)
        while (actionLog.size > 4) {
            actionLog.removeLast()
        }
        actionText.text = actionLog.joinToString("\n")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun startClient(reason: String, silent: Boolean = false) {
        if (!notificationPermissionGranted()) {
            requestNotificationPermission()
            if (!silent) {
                announce("需要通知权限，已请求授权")
            }
            return
        }
        val intent = Intent(this, NotifyClientService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            RuntimeStatusStore(this).setStatus(reason)
            if (!silent) {
                announce(reason)
            }
            refreshStatusUi()
        } catch (error: Exception) {
            announce("启动失败：${error.message ?: "未知错误"}")
        }
    }

    private fun restartClient(reason: String) {
        try {
            stopService(Intent(this, NotifyClientService::class.java))
        } catch (_: Exception) {
        }
        startClient(reason)
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000L
        val seconds = totalSeconds % 60L
        val minutes = (totalSeconds / 60L) % 60L
        val hours = totalSeconds / 3600L
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun testHealth(serverUrl: String): String {
        return try {
            val endpoint = "${serverUrl.trimEnd('/')}/health"
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 2500
            connection.readTimeout = 2500
            val code = connection.responseCode
            if (code in 200..299) {
                "PC 连接成功：HTTP $code"
            } else {
                "PC 连接异常：HTTP $code"
            }
        } catch (error: Exception) {
            "PC 连接失败：${error.message ?: "未知错误"}"
        }
    }

    private fun localIpSummary(): String {
        val ips = localIpv4Addresses()
        return if (ips.isEmpty()) {
            "本机 IP：未检测到局域网 IPv4"
        } else {
            "本机 IP：${ips.joinToString(", ")}\nCLI 示例：--target ip --ip ${ips.first()}"
        }
    }

    private fun localIpv4Addresses(): List<String> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { networkInterface -> networkInterface.inetAddresses.toList() }
                .map { it.hostAddress ?: "" }
                .filter { it.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) && !it.startsWith("127.") }
        }.getOrDefault(emptyList())
    }

    private fun labelFor(state: LampState): String {
        return when (state) {
            LampState.RUNNING_GREEN -> "绿灯常亮：运行中"
            LampState.ATTENTION_YELLOW -> "黄灯频闪：等待人工选择"
            LampState.IDLE_RED -> "红灯慢闪：完成或空闲"
        }
    }

    private fun formatDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        private const val LAMP_MIN_SCALE = 45
        private const val LAMP_MAX_SCALE = 145
    }
}
