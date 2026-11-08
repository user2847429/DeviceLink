package org.devicelink.core.peer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps the peer component alive while the user explicitly enables it.
 *
 * This service currently manages its Android foreground-service lifecycle
 * only. It does not advertise, discover, or connect to other devices yet.
 */
class PeerService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }

            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DeviceLink",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Stato del servizio locale DeviceLink"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("DeviceLink attivo")
            .setContentText("Servizio locale attivo; connessione tra dispositivi non ancora disponibile.")
            .setOngoing(true)
            .build()

    companion object {
        const val ACTION_START = "org.devicelink.action.START_PEER_SERVICE"
        const val ACTION_STOP = "org.devicelink.action.STOP_PEER_SERVICE"

        private const val CHANNEL_ID = "devicelink_peer"
        private const val NOTIFICATION_ID = 1001
    }
}
