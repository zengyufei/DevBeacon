package com.devbeacon.app

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LowPowerClient(
    private val configStore: ConfigStore,
    private val onMessage: (NotifyMessage) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onDrop: (String) -> Unit
) {
    @Volatile
    private var running = false
    private val seen = LinkedHashSet<String>()

    fun start() {
        running = true
        Thread({ loop() }, "devbeacon-low-power-client").start()
    }

    fun stop() {
        running = false
    }

    private fun loop() {
        var after = 0L
        var failures = 0
        while (running) {
            val config = configStore.load()
            try {
                onStatus("${config.powerMode.displayName}: polling ${config.serverUrl}")
                val response = poll(config, after)
                failures = 0
                val messages = response.optJSONArray("messages")
                if (messages != null) {
                    for (index in 0 until messages.length()) {
                        val message = Protocol.parseSignedMessage(messages.getJSONObject(index), config.sharedSecret)
                        if (message == null) {
                            onDrop("收到消息但无法解析；若已填写 Shared secret，请检查 PC 和 App 是否一致")
                            continue
                        }
                        if (seen.add(message.dedupeKey)) {
                            after = maxOf(after, message.timestamp + 1)
                            onMessage(message)
                        }
                    }
                }
                val next = response.optInt("recommendedNextPollSeconds", 1)
                sleepSeconds(next.coerceAtMost(1))
            } catch (error: Exception) {
                failures += 1
                Log.w(TAG, "poll failed: ${error.message}")
                onStatus("连接失败：${error.message ?: "未知错误"}；低耗电退避重试中")
                sleepSeconds(PowerPolicy.reconnectBackoffSeconds(configStore.load().powerMode, failures))
            }
        }
    }

    private fun poll(config: NotifyConfig, after: Long): JSONObject {
        val timeout = PowerPolicy.initialPollSeconds(config.powerMode).coerceAtLeast(5).coerceAtMost(55)
        val endpoint = "${config.serverUrl.trimEnd('/')}/api/poll?clientId=${config.clientId}&after=$after&timeout=$timeout"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = (timeout + 5) * 1000
        connection.useCaches = false
        connection.inputStream.bufferedReader().use { reader ->
            return JSONObject(reader.readText())
        }
    }

    private fun sleepSeconds(seconds: Int) {
        Thread.sleep(seconds.coerceAtLeast(1) * 1000L)
    }

    companion object {
        private const val TAG = "LowPowerClient"
    }
}
