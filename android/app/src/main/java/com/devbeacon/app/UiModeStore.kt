package com.devbeacon.app

import android.content.Context

enum class UiMode {
    PRODUCTION,
    DEBUG
}

class UiModeStore(context: Context) {
    private val prefs = context.getSharedPreferences("devbeacon_ui", Context.MODE_PRIVATE)

    fun load(): UiMode {
        return runCatching {
            UiMode.valueOf(prefs.getString("uiMode", UiMode.PRODUCTION.name) ?: UiMode.PRODUCTION.name)
        }.getOrDefault(UiMode.PRODUCTION)
    }

    fun save(mode: UiMode) {
        prefs.edit().putString("uiMode", mode.name).apply()
    }
}
