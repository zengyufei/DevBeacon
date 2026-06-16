package com.notifyphone.app

import android.content.Context

class BleFallback(private val context: Context) {
    fun status(manualRequest: Boolean = false): String {
        val config = ConfigStore(context).load()
        return if (PowerPolicy.allowsBleScan(config.powerMode, manualRequest)) {
            "BLE fallback is allowed by policy, but continuous scanning is not started by default."
        } else {
            "BLE scan is blocked in low-power mode unless manually requested."
        }
    }
}
