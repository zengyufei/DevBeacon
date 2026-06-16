package com.devbeacon.app

import android.content.Context

class StatusStore(context: Context) {
    private val prefs = context.getSharedPreferences("devbeacon_status", Context.MODE_PRIVATE)

    fun load(): LampSnapshot {
        return LampSnapshot(
            state = runCatching {
                LampState.valueOf(prefs.getString("state", LampState.IDLE_RED.name) ?: LampState.IDLE_RED.name)
            }.getOrDefault(LampState.IDLE_RED),
            runId = prefs.getString("runId", "") ?: "",
            startMillis = prefs.getLong("startMillis", 0L),
            elapsedMillis = prefs.getLong("elapsedMillis", 0L),
            lastTitle = prefs.getString("lastTitle", "") ?: "",
            lastBody = prefs.getString("lastBody", "") ?: "",
            lastStateLabel = prefs.getString("lastStateLabel", "idle") ?: "idle",
            updatedAtMillis = prefs.getLong("updatedAtMillis", 0L)
        )
    }

    fun save(snapshot: LampSnapshot) {
        prefs.edit()
            .putString("state", snapshot.state.name)
            .putString("runId", snapshot.runId)
            .putLong("startMillis", snapshot.startMillis)
            .putLong("elapsedMillis", snapshot.elapsedMillis)
            .putString("lastTitle", snapshot.lastTitle)
            .putString("lastBody", snapshot.lastBody)
            .putString("lastStateLabel", snapshot.lastStateLabel)
            .putLong("updatedAtMillis", snapshot.updatedAtMillis)
            .apply()
    }
}
