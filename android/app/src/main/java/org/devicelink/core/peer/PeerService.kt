package org.devicelink.core.peer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Owns the foreground peer component and its explicitly requested LAN discovery.
 * Discovery does not establish a connection or exchange application data.
 */
class PeerService : Service() {

    private var discovery: NsdPeerDiscovery? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundServiceNotification()
            ACTION_DISCOVER -> {
                startForegroundServiceNotification()
                if (discovery != null) {
                    discovery?.stop()
                    discovery = null
                }
                startDiscovery()
            }
            ACTION_TCP_PROBE -> probeTcp(
                intent.getStringExtra(EXTRA_HOST).orEmpty(),
                intent.getIntExtra(EXTRA_PORT, 0),
                intent.getStringExtra(EXTRA_SERVICE_NAME).orEmpty()
            )
            ACTION_STOP -> {
                discovery?.stop()
                discovery = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun probeTcp(host: String, port: Int, peerName: String) {
        if (host.isBlank() || port !in 1..65535 || peerName.isBlank()) return
        Thread({
            val result = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), TCP_PROBE_TIMEOUT_MS)
                }
                "TCP raggiungibile con $peerName ($host:$port). Nessun dato applicativo inviato; sessione NON autenticata."
            } catch (e: Exception) {
                "Connessione TCP a $peerName non riuscita: ${e.message ?: e.javaClass.simpleName}."
            }
            sendPeerEvent(ACTION_TCP_PROBE_RESULT) {
                putExtra(EXTRA_SERVICE_NAME, peerName)
                putExtra(EXTRA_MESSAGE, result)
            }
        }, "DeviceLink-tcp-probe").apply {
            isDaemon = true
            start()
        }
    }

    private fun startForegroundServiceNotification() {
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

    private fun startDiscovery() {
        if (discovery != null) return
        discovery = NsdPeerDiscovery(
            applicationContext,
            onPeerFound = { peer ->
                sendPeerEvent(ACTION_PEER_FOUND) {
                    putExtra(EXTRA_SERVICE_NAME, peer.serviceName)
                    putExtra(EXTRA_HOST, peer.hostAddress)
                    putExtra(EXTRA_PORT, peer.port)
                }
            },
            onPeerLost = { name ->
                sendPeerEvent(ACTION_PEER_LOST) {
                    putExtra(EXTRA_SERVICE_NAME, name)
                }
            },
            onError = { message ->
                sendPeerEvent(ACTION_DISCOVERY_ERROR) {
                    putExtra(EXTRA_MESSAGE, message)
                }
            },
            onStatus = { message ->
                sendPeerEvent(ACTION_DISCOVERY_STATUS) {
                    putExtra(EXTRA_MESSAGE, message)
                }
            }
        ).also { it.start() }
    }

    private inline fun sendPeerEvent(action: String, extras: Intent.() -> Unit) {
        sendBroadcast(Intent(action).setPackage(packageName).apply(extras))
    }

    override fun onDestroy() {
        discovery?.stop()
        discovery = null
        super.onDestroy()
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
            .setContentText("Servizio locale attivo; connessione sicura non ancora disponibile.")
            .setOngoing(true)
            .build()

    companion object {
        const val ACTION_START = "org.devicelink.action.START_PEER_SERVICE"
        const val ACTION_DISCOVER = "org.devicelink.action.DISCOVER_PEERS"
        const val ACTION_STOP = "org.devicelink.action.STOP_PEER_SERVICE"
        const val ACTION_TCP_PROBE = "org.devicelink.action.TCP_PROBE"
        const val ACTION_TCP_PROBE_RESULT = "org.devicelink.action.TCP_PROBE_RESULT"

        const val ACTION_PEER_FOUND = "org.devicelink.action.PEER_FOUND"
        const val ACTION_PEER_LOST = "org.devicelink.action.PEER_LOST"
        const val ACTION_DISCOVERY_ERROR = "org.devicelink.action.DISCOVERY_ERROR"
        const val ACTION_DISCOVERY_STATUS = "org.devicelink.action.DISCOVERY_STATUS"
        const val EXTRA_SERVICE_NAME = "service_name"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_MESSAGE = "message"

        private const val CHANNEL_ID = "devicelink_peer"
        private const val NOTIFICATION_ID = 1001
        private const val TCP_PROBE_TIMEOUT_MS = 4000
    }
}
