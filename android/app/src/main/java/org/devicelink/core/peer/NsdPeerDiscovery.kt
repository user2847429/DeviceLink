package org.devicelink.core.peer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DiscoveredPeer(
    val serviceName: String,
    val hostAddress: String,
    val port: Int
)

/**
 * Local-network discovery using both DNS-SD/mDNS and UDP broadcast.
 * The temporary TCP listener accepts and closes sockets; it does not yet
 * exchange application data or establish a trusted session.
 */
class NsdPeerDiscovery(
    context: Context,
    private val onPeerFound: (DiscoveredPeer) -> Unit,
    private val onPeerLost: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private var serverSocket: ServerSocket? = null
    private var udpListener: DatagramSocket? = null
    private var udpListenerThread: Thread? = null
    private var udpAnnounceThread: Thread? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var ownServiceName: String? = null
    private val resolving = ConcurrentHashMap.newKeySet<String>()
    private val reportedPeers = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var running = false

    @Synchronized
    fun start() {
        if (running) return
        try {
            val listener = ServerSocket(0)
            serverSocket = listener
            running = true
            Thread({
                while (running) {
                    try {
                        listener.accept().use { /* Placeholder only; no protocol is implemented yet. */ }
                    } catch (_: SocketException) {
                        break
                    } catch (_: Exception) {
                        if (running) onError("Errore durante l'ascolto TCP locale.")
                    }
                }
            }, "DeviceLink-local-listener").apply {
                isDaemon = true
                start()
            }

            val deviceId = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY_DEVICE_ID, null)
                ?: UUID.randomUUID().toString().replace("-", "").also {
                    appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                        .edit().putString(KEY_DEVICE_ID, it).apply()
                }
            val stableName = "DeviceLink-${deviceId.take(8)}"
            ownServiceName = stableName

            startUdpDiscovery(deviceId, stableName, listener.localPort)

            val service = NsdServiceInfo().apply {
                serviceName = stableName
                serviceType = SERVICE_TYPE
                port = listener.localPort
                setAttribute("id", deviceId.toByteArray(Charsets.UTF_8))
                setAttribute("protocol", PROTOCOL_VERSION.toByteArray(Charsets.UTF_8))
            }

            val registration = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    ownServiceName = info.serviceName
                    onStatus("Annuncio locale attivo; ricerca mDNS e UDP broadcast in corso…")
                    beginDiscovery()
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    onError("Registrazione mDNS non riuscita (codice $errorCode). La ricerca UDP resta attiva.")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    onError("Chiusura annuncio mDNS non riuscita (codice $errorCode).")
                }
            }
            registrationListener = registration
            nsdManager.registerService(service, NsdManager.PROTOCOL_DNS_SD, registration)
        } catch (e: Exception) {
            onError("Impossibile avviare la ricerca locale: ${e.javaClass.simpleName}: ${e.message ?: "errore senza dettagli"}.")
            stop()
        }
    }

    private fun startUdpDiscovery(deviceId: String, deviceName: String, tcpPort: Int) {
        try {
            val receiveSocket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 1000
                bind(InetSocketAddress(UDP_DISCOVERY_PORT))
            }
            udpListener = receiveSocket

            udpListenerThread = Thread({
                val buffer = ByteArray(4096)
                while (running) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        receiveSocket.receive(packet)
                        val json = JSONObject(
                            String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        )
                        if (json.optString("magic") != UDP_MAGIC ||
                            json.optInt("protocol") != UDP_PROTOCOL ||
                            json.optString("deviceId") == deviceId
                        ) continue

                        val peerName = json.optString("deviceName")
                        val peerPort = json.optInt("tcpPort")
                        if (peerName.isBlank() || peerPort !in 1..65535) continue

                        if (reportedPeers.add(peerName)) {
                            onPeerFound(
                                DiscoveredPeer(peerName, packet.address.hostAddress ?: continue, peerPort)
                            )
                            onStatus("Dispositivo trovato tramite UDP broadcast: $peerName.")
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Periodically check the running flag.
                    } catch (_: SocketException) {
                        if (running) onError("Ascolto UDP interrotto.")
                        break
                    } catch (_: Exception) {
                        if (running) onError("Pacchetto UDP di discovery non valido.")
                    }
                }
            }, "DeviceLink-udp-listener").apply {
                isDaemon = true
                start()
            }

            udpAnnounceThread = Thread({
                val payload = JSONObject()
                    .put("magic", UDP_MAGIC)
                    .put("protocol", UDP_PROTOCOL)
                    .put("deviceId", deviceId)
                    .put("deviceName", deviceName)
                    .put("tcpPort", tcpPort)
                    .toString().toByteArray(Charsets.UTF_8)

                while (running) {
                    try {
                        broadcastOnLocalNetworks(payload)
                    } catch (e: Exception) {
                        if (running) onError("Invio UDP broadcast non riuscito: ${e.message ?: "errore di rete"}.")
                    }
                    try { Thread.sleep(UDP_ANNOUNCE_INTERVAL_MS) } catch (_: InterruptedException) { break }
                }
            }, "DeviceLink-udp-announcer").apply {
                isDaemon = true
                start()
            }
            onStatus("Ricerca UDP broadcast attiva sulla rete locale; avvio anche mDNS…")
        } catch (e: Exception) {
            onError("Impossibile avviare UDP broadcast sulla porta $UDP_DISCOVERY_PORT: ${e.message ?: e.javaClass.simpleName}.")
        }
    }

    private fun broadcastOnLocalNetworks(payload: ByteArray) {
        val destinations = linkedSetOf<Pair<InetAddress, InetAddress>>()
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || !networkInterface.supportsMulticast()) continue
            for (entry in networkInterface.interfaceAddresses) {
                val local = entry.address
                val broadcast = entry.broadcast
                if (local is Inet4Address && broadcast is Inet4Address) {
                    destinations.add(local to broadcast)
                }
            }
        }

        for ((local, broadcast) in destinations) {
            try {
                DatagramSocket(null).use { socket ->
                    socket.broadcast = true
                    socket.bind(InetSocketAddress(local, 0))
                    socket.send(DatagramPacket(payload, payload.size, broadcast, UDP_DISCOVERY_PORT))
                }
            } catch (_: Exception) {
                // A disconnected or restricted interface must not block other interfaces.
            }
        }
        if (destinations.isEmpty()) {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val target = InetAddress.getByName("255.255.255.255")
                socket.send(DatagramPacket(payload, payload.size, target, UDP_DISCOVERY_PORT))
            }
        }
    }

    private fun beginDiscovery() {
        if (!running || discoveryListener != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                onStatus("Ricerca mDNS attiva; UDP broadcast continua in parallelo.")
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName == ownServiceName || !info.serviceType.endsWith(SERVICE_TYPE)) return
                onStatus("Rilevato un annuncio mDNS; verifica indirizzo in corso…")
                if (!resolving.add(info.serviceName)) return
                nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        resolving.remove(serviceInfo.serviceName)
                        onError("Trovato ${serviceInfo.serviceName}, ma impossibile risolvere l'indirizzo (codice $errorCode).")
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        resolving.remove(serviceInfo.serviceName)
                        val host = serviceInfo.host?.hostAddress ?: return
                        if (running && reportedPeers.add(serviceInfo.serviceName)) {
                            onPeerFound(DiscoveredPeer(serviceInfo.serviceName, host, serviceInfo.port))
                            onStatus("Dispositivo trovato tramite mDNS: ${serviceInfo.serviceName}.")
                        }
                    }
                })
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                reportedPeers.remove(info.serviceName)
                onPeerLost(info.serviceName)
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onError("Avvio ricerca mDNS non riuscito (codice $errorCode). UDP broadcast resta attivo.")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                onError("Arresto della ricerca mDNS non riuscito (codice $errorCode).")
            }
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    @Synchronized
    fun stop() {
        if (!running && serverSocket == null && udpListener == null) return
        running = false
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) { }
        }
        discoveryListener = null
        registrationListener?.let {
            try { nsdManager.unregisterService(it) } catch (_: Exception) { }
        }
        registrationListener = null
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
        try { udpListener?.close() } catch (_: Exception) { }
        udpListener = null
        udpListenerThread?.interrupt()
        udpListenerThread = null
        udpAnnounceThread?.interrupt()
        udpAnnounceThread = null
        ownServiceName = null
        resolving.clear()
        reportedPeers.clear()
    }

    companion object {
        const val SERVICE_TYPE = "_devicelink._tcp."
        const val PROTOCOL_VERSION = "1"
        private const val PREFERENCES = "devicelink_identity"
        private const val KEY_DEVICE_ID = "stable_device_id"
        private const val UDP_MAGIC = "devicelink-discovery"
        private const val UDP_PROTOCOL = 1
        private const val UDP_DISCOVERY_PORT = 1717
        private const val UDP_ANNOUNCE_INTERVAL_MS = 3000L
    }
}
