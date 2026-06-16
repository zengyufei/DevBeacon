package com.notifyphone.app

import android.content.Context

data class RuntimeStatus(
    val serviceStatus: String = "客户端未启动",
    val lastReceived: String = "尚未收到 PC 消息",
    val lastError: String = "",
    val receivedCount: Int = 0,
    val droppedCount: Int = 0,
    val updatedAtMillis: Long = 0L
)

class RuntimeStatusStore(context: Context) {
    private val prefs = context.getSharedPreferences("notifyphone_runtime", Context.MODE_PRIVATE)

    fun load(): RuntimeStatus {
        return RuntimeStatus(
            serviceStatus = prefs.getString("serviceStatus", "客户端未启动") ?: "客户端未启动",
            lastReceived = prefs.getString("lastReceived", "尚未收到 PC 消息") ?: "尚未收到 PC 消息",
            lastError = prefs.getString("lastError", "") ?: "",
            receivedCount = prefs.getInt("receivedCount", 0),
            droppedCount = prefs.getInt("droppedCount", 0),
            updatedAtMillis = prefs.getLong("updatedAtMillis", 0L)
        )
    }

    fun setStatus(status: String) {
        prefs.edit()
            .putString("serviceStatus", status)
            .putLong("updatedAtMillis", System.currentTimeMillis())
            .apply()
    }

    fun recordReceived(message: NotifyMessage) {
        val current = load()
        prefs.edit()
            .putString("lastReceived", "${message.title} ${message.body}".trim())
            .putString("lastError", "")
            .putInt("receivedCount", current.receivedCount + 1)
            .putLong("updatedAtMillis", System.currentTimeMillis())
            .apply()
    }

    fun recordDrop(reason: String) {
        val current = load()
        prefs.edit()
            .putString("lastError", reason)
            .putInt("droppedCount", current.droppedCount + 1)
            .putLong("updatedAtMillis", System.currentTimeMillis())
            .apply()
    }
}
