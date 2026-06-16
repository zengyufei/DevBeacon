package com.notifyphone.app

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class DirectReceiveService(
    private val context: Context,
    private val onMessage: (NotifyMessage) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onDrop: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)

    fun isEnabled(): Boolean = ConfigStore(context).load().directReceiveEnabled

    fun startIfEnabled(): String {
        if (!isEnabled()) {
            stop()
            return "Direct receive mode is off. No Android HTTP listener is running."
        }
        if (running.get()) {
            return "Direct receive HTTP listener is running on port $PORT."
        }
        return try {
            val socket = ServerSocket(PORT)
            serverSocket = socket
            running.set(true)
            Thread({ acceptLoop(socket) }, "notifyphone-direct-http").start()
            "Direct receive HTTP listener is running on port $PORT."
        } catch (error: Exception) {
            running.set(false)
            val message = "Direct receive failed: ${error.message ?: "unknown error"}"
            onDrop(message)
            message
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun acceptLoop(socket: ServerSocket) {
        onStatus("直接接收模式：监听 $PORT")
        while (running.get()) {
            try {
                socket.accept().use { client ->
                    handleClient(client)
                }
            } catch (error: Exception) {
                if (running.get()) {
                    onDrop("直接接收失败：${error.message ?: "unknown error"}")
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        val input = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val requestLine = input.readLine().orEmpty()
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            val index = line.indexOf(':')
            if (index > 0) {
                headers[line.substring(0, index).trim().lowercase()] = line.substring(index + 1).trim()
            }
        }
        if (!requestLine.startsWith("POST /notify ")) {
            writeResponse(client, 404, """{"ok":false,"error":"not found"}""")
            return
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = CharArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(body, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        val config = ConfigStore(context).load()
        val message = runCatching {
            Protocol.parseSignedMessage(JSONObject(String(body, 0, offset)), config.sharedSecret)
        }.getOrNull()
        if (message == null) {
            onDrop("直接接收消息解析失败；若填写了 Shared secret，请检查两端一致")
            writeResponse(client, 401, """{"ok":false,"error":"invalid message"}""")
            return
        }
        onMessage(message)
        writeResponse(client, 202, """{"ok":true}""")
    }

    private fun writeResponse(client: Socket, status: Int, body: String) {
        val reason = when (status) {
            202 -> "Accepted"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "OK"
        }
        val raw = body.toByteArray(Charsets.UTF_8)
        client.getOutputStream().write(
            "HTTP/1.1 $status $reason\r\nContent-Type: application/json\r\nContent-Length: ${raw.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8)
        )
        client.getOutputStream().write(raw)
        client.getOutputStream().flush()
    }

    companion object {
        const val PORT = 8766
    }
}
