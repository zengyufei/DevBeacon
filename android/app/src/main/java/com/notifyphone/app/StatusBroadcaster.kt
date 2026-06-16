package com.notifyphone.app

import android.content.Context
import android.content.Intent

object StatusBroadcaster {
    const val ACTION_STATUS_CHANGED = "com.notifyphone.app.STATUS_CHANGED"

    fun broadcast(context: Context) {
        context.sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(context.packageName))
    }
}
