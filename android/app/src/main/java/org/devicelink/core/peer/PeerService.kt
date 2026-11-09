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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Owns local discovery and inbound authenticated control sessions.
 * A discovered peer is not trusted: the TLS server checks its certificate key
 * against the encrypted trusted-peer registry before a control channel is opened.
 */
class PeerService : Service() {

    private var discovery: NsdPeerDiscovery? = null
    private var identityStore: DeviceIdentityStore? = null
    private var trustedTlsFactory: AndroidTlsSocketFactory? = null
    private val activeChannels = ConcurrentHashMap<String, AuthenticatedControlChannel>()
    private val pendingSockets = ConcurrentHashMap.newKeySet<Socket>()
    @Volatile private var stoppingConnections = false
    private val connectionExecutor = Executors.newCachedThreadPool(ThreadFactory { task ->
        Thread(task, "DeviceLink-inbound-session").apply { isDaemon = true }
    })

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
                stopSecureConnections()
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
        val tlsFactory = try {
            val store = DeviceIdentityStore()
            store.getOrCreateIdentity()
            val registry = TrustedPeerRegistry(
                AndroidEncryptedTrustedPeerRepository(applicationContext)
            )
            // Force a database read before advertising a secure listener. Corrupt or
            // unavailable trust data must not be treated as an empty trust database.
            registry.all()
            identityStore = store
            AndroidTlsSocketFactory.forTrustedPeers(registry, store)
        } catch (error: Exception) {
            trustedTlsFactory = null
            sendPeerEvent(ACTION_SECURE_SESSION_ERROR) {
                putExtra(EXTRA_MESSAGE, "Registro dei dispositivi fidati non disponibile: listener sicuro disattivato.")
            }
            null
        }
        trustedTlsFactory = tlsFactory
        stoppingConnections = false

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
            },
            onSocketAccepted = { socket ->
                if (tlsFactory == null || stoppingConnections) {
                    socket.close()
                } else {
                    acceptSecureConnection(socket, tlsFactory)
                }
            }
        ).also { it.start() }
    }

    private fun acceptSecureConnection(socket: Socket, tlsFactory: AndroidTlsSocketFactory) {
        if (!pendingSockets.add(socket)) {
            socket.close()
            return
        }
        try {
            connectionExecutor.execute {
                var channel: AuthenticatedControlChannel? = null
                var remotePeerId: String? = null
                try {
                    val localPeerId = requireNotNull(identityStore).getOrCreateIdentity().peerId
                    val session = AndroidTlsSession.acceptTrusted(
                        tlsFactory,
                        socket,
                        INBOUND_HANDSHAKE_TIMEOUT_MS
                    )
                    channel = AuthenticatedControlChannel.establish(
                        session,
                        localPeerId,
                        INBOUND_HELLO_TIMEOUT_MS
                    )
                    remotePeerId = channel.remotePeerId
                    val previous = activeChannels.put(channel.remotePeerId, channel)
                    if (previous != null && previous !== channel) previous.close()

                    sendPeerEvent(ACTION_SECURE_SESSION_ESTABLISHED) {
                        putExtra(EXTRA_PEER_ID, channel.remotePeerId)
                    }

                    while (!stoppingConnections) {
                        val message = channel.receive() ?: break
                        sendPeerEvent(ACTION_CONTROL_MESSAGE_RECEIVED) {
                            putExtra(EXTRA_PEER_ID, channel.remotePeerId)
                            putExtra(EXTRA_MESSAGE_KIND, message.kind.name)
                            putExtra(EXTRA_PAYLOAD, message.payload)
                        }
                    }
                } catch (_: Exception) {
                    if (!stoppingConnections) {
                        sendPeerEvent(ACTION_SECURE_SESSION_ERROR) {
                            putExtra(EXTRA_MESSAGE, "Connessione sicura in ingresso rifiutata o interrotta.")
                        }
                    }
                } finally {
                    val established = channel
                    val peerId = remotePeerId
                    if (established != null) {
                        if (peerId != null) activeChannels.remove(peerId, established)
                        try { established.close() } catch (_: Exception) { }
                        if (peerId != null && !stoppingConnections) {
                            sendPeerEvent(ACTION_SECURE_SESSION_CLOSED) {
                                putExtra(EXTRA_PEER_ID, peerId)
                            }
                        }
                    } else {
                        try { socket.close() } catch (_: Exception) { }
                    }
                    pendingSockets.remove(socket)
                }
            }
        } catch (error: Exception) {
            pendingSockets.remove(socket)
            try { socket.close() } catch (_: Exception) { }
            if (!stoppingConnections) {
                sendPeerEvent(ACTION_SECURE_SESSION_ERROR) {
                    putExtra(EXTRA_MESSAGE, "Impossibile avviare la gestione della connessione sicura.")
                }
            }
        }
    }

    private fun stopSecureConnections() {
        stoppingConnections = true
        activeChannels.values.forEach { try { it.close() } catch (_: Exception) { } }
        activeChannels.clear()
        pendingSockets.forEach { try { it.close() } catch (_: Exception) { } }
        pendingSockets.clear()
        connectionExecutor.shutdownNow()
        trustedTlsFactory = null
        identityStore = null
    }

    private inline fun sendPeerEvent(action: String, extras: Intent.() -> Unit) {
        sendBroadcast(Intent(action).setPackage(packageName).apply(extras))
    }

    override fun onDestroy() {
        discovery?.stop()
        discovery = null
        stopSecureConnections()
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
            .setContentText("Ricerca locale attiva; le sessioni accettano solo dispositivi già fidati.")
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
        const val ACTION_SECURE_SESSION_ESTABLISHED = "org.devicelink.action.SECURE_SESSION_ESTABLISHED"
        const val ACTION_SECURE_SESSION_CLOSED = "org.devicelink.action.SECURE_SESSION_CLOSED"
        const val ACTION_SECURE_SESSION_ERROR = "org.devicelink.action.SECURE_SESSION_ERROR"
        const val ACTION_CONTROL_MESSAGE_RECEIVED = "org.devicelink.action.CONTROL_MESSAGE_RECEIVED"

        const val EXTRA_SERVICE_NAME = "service_name"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_PEER_ID = "peer_id"
        const val EXTRA_MESSAGE_KIND = "message_kind"
        const val EXTRA_PAYLOAD = "payload"

        private const val CHANNEL_ID = "devicelink_peer"
        private const val NOTIFICATION_ID = 1001
        private const val TCP_PROBE_TIMEOUT_MS = 4000
        private const val INBOUND_HANDSHAKE_TIMEOUT_MS = 10_000
        private const val INBOUND_HELLO_TIMEOUT_MS = 10_000
    }
}
