package org.devicelink.core.peer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.ServerSocket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DiscoveredPeer(
    val serviceName: String,
    val hostAddress: String,
    val port: Int
)

/**
 * Local-network discovery only. The temporary TCP listener accepts and closes
 * sockets; it does not exchange application data or establish a trusted session.
 */
class NsdPeerDiscovery(
    context: Context,
    private val onPeerFound: (DiscoveredPeer) -> Unit,
    private val onPeerLost: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var ownServiceName: String? = null
    private val resolving = ConcurrentHashMap.newKeySet<String>()
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
                        listener.accept().use { /* No protocol data is accepted yet. */ }
                    } catch (_: SocketException) {
                        break
                    } catch (_: Exception) {
                        if (running) onError("Errore durante l'ascolto locale.")
                    }
                }
            }, "DeviceLink-local-listener").apply {
                isDaemon = true
                start()
            }

            val service = NsdServiceInfo().apply {
                serviceName = "DeviceLink-${UUID.randomUUID().toString().take(8)}"
                serviceType = SERVICE_TYPE
                port = listener.localPort
                setAttribute("protocol", PROTOCOL_VERSION)
            }

            val registration = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    ownServiceName = info.serviceName
                    beginDiscovery()
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    onError("Registrazione del dispositivo non riuscita (codice $errorCode).")
                    stop()
                }
                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    onError("Chiusura dell'annuncio non riuscita (codice $errorCode).")
                }
            }
            registrationListener = registration
            nsdManager.registerService(service, NsdManager.PROTOCOL_DNS_SD, registration)
        } catch (e: Exception) {
            onError("Impossibile avviare la ricerca nella rete locale.")
            stop()
        }
    }

    private fun beginDiscovery() {
        if (!running || discoveryListener != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName == ownServiceName || !info.serviceType.endsWith(SERVICE_TYPE)) return
                if (!resolving.add(info.serviceName)) return
                nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        resolving.remove(serviceInfo.serviceName)
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        resolving.remove(serviceInfo.serviceName)
                        val host = serviceInfo.host?.hostAddress ?: return
                        if (running) {
                            onPeerFound(DiscoveredPeer(serviceInfo.serviceName, host, serviceInfo.port))
                        }
                    }
                })
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                onPeerLost(info.serviceName)
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onError("Ricerca dispositivi non riuscita (codice $errorCode).")
                stop()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                onError("Arresto della ricerca non riuscito (codice $errorCode).")
            }
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    @Synchronized
    fun stop() {
        if (!running && serverSocket == null) return
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
        ownServiceName = null
        resolving.clear()
    }

    companion object {
        const val SERVICE_TYPE = "_devicelink._tcp."
        const val PROTOCOL_VERSION = "1"
    }
}
