package com.devbeacon.app

import android.app.Service
import android.content.Intent
import android.os.IBinder

class NotifyClientService : Service() {
    private lateinit var helper: NotificationHelper
    private lateinit var client: LowPowerClient
    private lateinit var directReceive: DirectReceiveService
    private lateinit var statusStore: StatusStore
    private lateinit var runtimeStore: RuntimeStatusStore

    override fun onCreate() {
        super.onCreate()
        helper = NotificationHelper(this)
        helper.ensureChannels()
        val configStore = ConfigStore(this)
        statusStore = StatusStore(this)
        runtimeStore = RuntimeStatusStore(this)
        client = LowPowerClient(
            configStore = configStore,
            onMessage = { handleMessage(it) },
            onStatus = { status ->
                runtimeStore.setStatus(status)
                StatusBroadcaster.broadcast(this)
                startForeground(SERVICE_ID, helper.serviceNotification(status))
            },
            onDrop = { reason ->
                runtimeStore.recordDrop(reason)
                StatusBroadcaster.broadcast(this)
            },
        )
        directReceive = DirectReceiveService(
            context = this,
            onMessage = { handleMessage(it) },
            onStatus = { status ->
                runtimeStore.setStatus(status)
                StatusBroadcaster.broadcast(this)
            },
            onDrop = { reason ->
                runtimeStore.recordDrop(reason)
                StatusBroadcaster.broadcast(this)
            }
        )
        startForeground(SERVICE_ID, helper.serviceNotification("Low-power client starting"))
        client.start()
        runtimeStore.setStatus(directReceive.startIfEnabled())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        client.stop()
        directReceive.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleMessage(message: NotifyMessage) {
        runtimeStore.recordReceived(message)
        if (message.eventType == "status" && message.state != null) {
            val updated = LampStateMachine.apply(statusStore.load(), message, System.currentTimeMillis())
            statusStore.save(updated)
        }
        StatusBroadcaster.broadcast(this)
        helper.showMessage(message)
    }

    companion object {
        private const val SERVICE_ID = 1001
    }
}
