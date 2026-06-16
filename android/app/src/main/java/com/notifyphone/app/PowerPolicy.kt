package com.notifyphone.app

object PowerPolicy {
    fun initialPollSeconds(mode: PowerMode): Int {
        return when (mode) {
            PowerMode.LOW -> 45
            PowerMode.BALANCED -> 15
            PowerMode.HIGH_AVAILABILITY -> 5
        }
    }

    fun reconnectBackoffSeconds(mode: PowerMode, failures: Int): Int {
        val capped = failures.coerceAtMost(5)
        return when (mode) {
            PowerMode.LOW -> 8 * (capped + 1)
            PowerMode.BALANCED -> 5 * (capped + 1)
            PowerMode.HIGH_AVAILABILITY -> 5 * (capped + 1)
        }
    }

    fun allowsBleScan(mode: PowerMode, manualRequest: Boolean): Boolean {
        return manualRequest || mode == PowerMode.HIGH_AVAILABILITY
    }
}
