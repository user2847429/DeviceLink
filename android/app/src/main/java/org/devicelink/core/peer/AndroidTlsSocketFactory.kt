package org.devicelink.core.peer

import android.net.ssl.SSLSockets
import java.net.Socket
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.MessageDigest
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * Creates TLS 1.3 sockets pinned to a specific DeviceLink installation identity.
 *
 * This adapter deliberately accepts exactly one expected remote identity per
 * socket. First-time pairing pins the public key from a validated QR offer;
 * an already trusted connection pins the key from the trusted-peer record.
 */
class AndroidTlsSocketFactory(
    private val expectedPeerId: String,
    expectedPeerPublicKeyEncoded: ByteArray,
    private val identityStore: DeviceIdentityStore = DeviceIdentityStore(),
) {
    private val expectedPublicKey = expectedPeerPublicKeyEncoded.clone()

    init {
        require(expectedPeerId.isNotBlank()) { "Expected peer ID must not be blank." }
        require(DeviceIdentityStore.isSupportedPublicKey(expectedPublicKey)) {
            "Expected peer key must be a supported P-256 public key."
        }
        require(DeviceIdentityStore.peerIdFor(expectedPublicKey) == expectedPeerId) {
            "Expected peer ID does not match its public key."
        }
    }

    /** Creates a client-mode socket. The caller must invoke startHandshake(). */
    fun createClientSocket(host: String, port: Int, connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS): SSLSocket {
        require(host.isNotBlank())
        require(port in 1..65535)
        require(connectTimeoutMillis > 0)
        val context = createPinnedContext()
        val socket = context.socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(host, port), connectTimeoutMillis)
            socket.enabledProtocols = arrayOf(TLS_1_3)
            socket.useClientMode = true
            return socket
        } catch (error: Exception) {
            try { socket.close() } catch (_: Exception) { }
            throw error
        }
    }

    /**
     * Wraps an accepted TCP socket in server-mode TLS and requires a client
     * certificate. The caller must invoke startHandshake().
     */
    fun wrapAcceptedSocket(accepted: Socket): SSLSocket {
        require(accepted.isConnected && !accepted.isClosed) { "TCP socket must be connected." }
        val context = createPinnedContext()
        return (context.socketFactory.createSocket(
            accepted,
            accepted.inetAddress.hostAddress,
            accepted.port,
            true
        ) as SSLSocket).apply {
            enabledProtocols = arrayOf(TLS_1_3)
            useClientMode = false
            needClientAuth = true
        }
    }

    /**
     * Exports per-session pairing material only after a completed TLS 1.3
     * handshake. The caller supplies the same canonical, non-empty context
     * bytes on both peers (normally binding the pairing nonce and version).
     */
    fun exportPairingMaterial(socket: SSLSocket, context: ByteArray): ByteArray {
        require(context.isNotEmpty()) { "Pairing exporter context must not be empty." }
        check(socket.session.isValid && socket.session.protocol == TLS_1_3) {
            "A completed TLS 1.3 session is required."
        }
        if (!SSLSockets.isSupportedSocket(socket)) {
            throw SSLException("TLS socket does not support Android keying-material export.")
        }
        return SSLSockets.exportKeyingMaterial(
            socket,
            EXPORTER_LABEL,
            context.clone(),
            EXPORTER_LENGTH
        ) ?: throw SSLException("TLS keying-material export is unavailable.")
    }

    private fun createPinnedContext(): SSLContext {
        // Ensure the installation identity exists before loading AndroidKeyStore.
        val identity = identityStore.getOrCreateIdentity()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val localCertificate = keyStore.getCertificate(identityStore.keyAlias)
            ?: throw SSLException("DeviceLink identity certificate is missing from Android Keystore.")
        if (!MessageDigest.isEqual(identity.publicKeyEncoded, localCertificate.publicKey.encoded)) {
            throw SSLException("Android Keystore identity certificate does not match the DeviceLink peer ID.")
        }

        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).run {
            init(keyStore, null)
            keyManagers
        }
        val trustManager = PinnedPeerTrustManager(expectedPublicKey)
        return SSLContext.getInstance("TLS").apply {
            init(keyManagers, arrayOf(trustManager), null)
        }
    }

    private class PinnedPeerTrustManager(
        expectedPublicKeyEncoded: ByteArray
    ) : X509TrustManager {
        private val pinnedKey = expectedPublicKeyEncoded.clone()

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
            verifyPinnedCertificate(chain)

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) =
            verifyPinnedCertificate(chain)

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

        private fun verifyPinnedCertificate(chain: Array<out X509Certificate>) {
            val leaf = chain.firstOrNull()
                ?: throw java.security.cert.CertificateException("Peer did not present a certificate.")
            try {
                leaf.checkValidity()
            } catch (error: Exception) {
                throw java.security.cert.CertificateException("Peer certificate is not currently valid.", error)
            }
            val publicKey = leaf.publicKey.encoded
            if (!DeviceIdentityStore.isSupportedPublicKey(publicKey) ||
                !MessageDigest.isEqual(pinnedKey, publicKey)
            ) {
                throw java.security.cert.CertificateException("Peer certificate does not match the pinned DeviceLink identity.")
            }
        }
    }

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TLS_1_3 = "TLSv1.3"
        private const val EXPORTER_LABEL = "EXPORTER-DeviceLink-Pairing-v1"
        private const val EXPORTER_LENGTH = 32
    }
}
