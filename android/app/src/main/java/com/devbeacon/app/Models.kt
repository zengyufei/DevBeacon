package com.devbeacon.app

enum class PowerMode(val displayName: String) {
    LOW("低耗电模式"),
    BALANCED("均衡模式"),
    HIGH_AVAILABILITY("高可用模式")
}

data class NotifyConfig(
    val serverUrl: String,
    val clientId: String,
    val sharedSecret: String,
    val powerMode: PowerMode,
    val directReceiveEnabled: Boolean,
    val bleHighAvailabilityEnabled: Boolean,
    val lampScalePercent: Int = 100
)

data class NotifyMessage(
    val id: String,
    val timestamp: Long,
    val title: String,
    val body: String,
    val level: String,
    val source: String,
    val ttlSeconds: Int,
    val dedupeKey: String,
    val eventType: String? = null,
    val state: String? = null,
    val runId: String = ""
)
