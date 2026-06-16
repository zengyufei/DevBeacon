package com.devbeacon.app

enum class LampState {
    RUNNING_GREEN,
    ATTENTION_YELLOW,
    IDLE_RED
}

data class LampSnapshot(
    val state: LampState = LampState.IDLE_RED,
    val runId: String = "",
    val startMillis: Long = 0L,
    val elapsedMillis: Long = 0L,
    val lastTitle: String = "",
    val lastBody: String = "",
    val lastStateLabel: String = "idle",
    val updatedAtMillis: Long = 0L
)
