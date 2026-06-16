package com.notifyphone.app

import android.content.Context
import java.util.UUID

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("notifyphone", Context.MODE_PRIVATE)

    fun load(): NotifyConfig {
        val secret = prefs.getString("sharedSecret", "") ?: ""
        val clientId = prefs.getString("clientId", null) ?: "android-${UUID.randomUUID()}".also {
            prefs.edit().putString("clientId", it).apply()
        }
        val mode = runCatching {
            PowerMode.valueOf(prefs.getString("powerMode", PowerMode.LOW.name) ?: PowerMode.LOW.name)
        }.getOrDefault(PowerMode.LOW)

        return NotifyConfig(
            serverUrl = prefs.getString("serverUrl", "http://192.168.1.2:8765") ?: "http://192.168.1.2:8765",
            clientId = clientId,
            sharedSecret = secret,
            powerMode = mode,
            directReceiveEnabled = prefs.getBoolean("directReceiveEnabled", false),
            bleHighAvailabilityEnabled = prefs.getBoolean("bleHighAvailabilityEnabled", false),
            lampScalePercent = prefs.getInt("lampScalePercent", 100).coerceIn(45, 145)
        )
    }

    fun save(config: NotifyConfig) {
        prefs.edit()
            .putString("serverUrl", config.serverUrl.trimEnd('/'))
            .putString("clientId", config.clientId)
            .putString("sharedSecret", config.sharedSecret)
            .putString("powerMode", config.powerMode.name)
            .putBoolean("directReceiveEnabled", config.directReceiveEnabled)
            .putBoolean("bleHighAvailabilityEnabled", config.bleHighAvailabilityEnabled)
            .putInt("lampScalePercent", config.lampScalePercent.coerceIn(45, 145))
            .apply()
    }
}
