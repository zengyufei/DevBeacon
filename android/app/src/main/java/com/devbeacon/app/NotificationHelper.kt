package com.devbeacon.app

import android.app.NotificationChannel
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class NotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH)
            )
            manager.createNotificationChannel(
                NotificationChannel(SERVICE_CHANNEL, context.getString(R.string.service_channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    fun serviceNotification(status: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, SERVICE_CHANNEL)
        } else {
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("DevBeacon")
            .setContentText(status)
            .setOngoing(true)
            .build()
    }

    fun showMessage(message: NotifyMessage) {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, ALERT_CHANNEL)
        } else {
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(Notification.BigTextStyle().bigText(message.body))
            .setAutoCancel(true)
            .build()
        manager.notify(message.dedupeKey.hashCode(), notification)
    }

    companion object {
        const val ALERT_CHANNEL = "devbeacon_alerts"
        const val SERVICE_CHANNEL = "devbeacon_service"
    }
}
