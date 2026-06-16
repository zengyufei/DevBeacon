package com.devbeacon.app

object LampStateMachine {
    fun apply(current: LampSnapshot, message: NotifyMessage, nowMillis: Long): LampSnapshot {
        val state = message.state?.lowercase()
        return when (state) {
            "running" -> LampSnapshot(
                state = LampState.RUNNING_GREEN,
                runId = message.runId.ifBlank { message.id },
                startMillis = nowMillis,
                elapsedMillis = 0L,
                lastTitle = message.title,
                lastBody = message.body,
                lastStateLabel = "running",
                updatedAtMillis = nowMillis
            )
            "attention" -> current.copy(
                state = LampState.ATTENTION_YELLOW,
                elapsedMillis = elapsed(current, nowMillis),
                lastTitle = message.title,
                lastBody = message.body,
                lastStateLabel = "attention",
                updatedAtMillis = nowMillis
            )
            "done", "idle" -> current.copy(
                state = LampState.IDLE_RED,
                elapsedMillis = elapsed(current, nowMillis),
                lastTitle = message.title,
                lastBody = message.body,
                lastStateLabel = state ?: "idle",
                updatedAtMillis = nowMillis
            )
            else -> current.copy(
                lastTitle = message.title,
                lastBody = message.body,
                lastStateLabel = message.state ?: current.lastStateLabel,
                updatedAtMillis = nowMillis
            )
        }
    }

    fun runningMillis(snapshot: LampSnapshot, nowMillis: Long): Long {
        return when (snapshot.state) {
            LampState.RUNNING_GREEN -> elapsed(snapshot, nowMillis)
            else -> snapshot.elapsedMillis
        }
    }

    private fun elapsed(snapshot: LampSnapshot, nowMillis: Long): Long {
        if (snapshot.startMillis <= 0L) return snapshot.elapsedMillis
        return maxOf(snapshot.elapsedMillis, nowMillis - snapshot.startMillis)
    }
}
