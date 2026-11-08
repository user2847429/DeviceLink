package org.devicelink.core.peer

import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket

/**
 * A completed, TLS-1.3 socket whose remote leaf certificate is pinned to one
 * DeviceLink installation identity. Construction is restricted to the
 * handshake helpers below; callers never receive a pre-handshake socket.
 *
 * This does not itself mark an unknown peer as trusted. First-time pairing
 * must still complete the out-of-band comparison and two-sided confirmation.
 */
class AndroidTlsSession private constructor(
    val socket: SSLSocket,
    val peerId: String,
    private val tlsFactory: AndroidTlsSocketFactory,
) : AutoCloseable {

    /** Exports short-lived pairing material from this completed TLS session. */
    fun exportPairingMaterial(context: ByteArray): ByteArray =
        tlsFactory.exportPairingMaterial(socket, context)

    override fun close() {
        socket.close()
    }

    companion object {
        const val DEFAULT_HANDSHAKE_TIMEOUT_MILLIS = 10_000

        /** Connects and completes a pinned TLS 1.3 handshake before returning. */
        fun connect(
            tlsFactory: AndroidTlsSocketFactory,
            expectedPeerId: String,
            expectedPeerPublicKey: ByteArray,
            host: String,
            port: Int,
            handshakeTimeoutMillis: Int = DEFAULT_HANDSHAKE_TIMEOUT_MILLIS,
            connectTimeoutMillis: Int = DEFAULT_HANDSHAKE_TIMEOUT_MILLIS,
        ): AndroidTlsSession {
            require(handshakeTimeoutMillis > 0 && connectTimeoutMillis > 0)
            val socket = tlsFactory.createClientSocket(host, port, connectTimeoutMillis)
            return completeHandshake(
                socket, tlsFactory, expectedPeerId, expectedPeerPublicKey,
                handshakeTimeoutMillis
            )
        }

        /** Wraps an accepted TCP socket and completes mutual TLS before returning. */
        fun accept(
            tlsFactory: AndroidTlsSocketFactory,
            expectedPeerId: String,
            expectedPeerPublicKey: ByteArray,
            acceptedSocket: java.net.Socket,
            handshakeTimeoutMillis: Int = DEFAULT_HANDSHAKE_TIMEOUT_MILLIS,
        ): AndroidTlsSession {
            require(handshakeTimeoutMillis > 0)
            val socket = tlsFactory.wrapAcceptedSocket(acceptedSocket)
            return completeHandshake(
                socket, tlsFactory, expectedPeerId, expectedPeerPublicKey,
                handshakeTimeoutMillis
            )
        }

        private fun completeHandshake(
            socket: SSLSocket,
            tlsFactory: AndroidTlsSocketFactory,
            expectedPeerId: String,
            expectedPeerPublicKey: ByteArray,
            timeoutMillis: Int,
        ): AndroidTlsSession {
            try {
                socket.soTimeout = timeoutMillis
                socket.startHandshake()

                val session = socket.session
                check(session.isValid && session.protocol == TLS_1_3) {
                    "TLS 1.3 handshake did not complete."
                }
                val certificate = session.peerCertificates.firstOrNull() as? X509Certificate
                    ?: throw javax.net.ssl.SSLPeerUnverifiedException(
                        "Peer did not present an X.509 certificate."
                    )
                certificate.checkValidity()
                val actualKey = certificate.publicKey.encoded
                check(DeviceIdentityStore.isSupportedPublicKey(actualKey)) {
                    "Peer certificate does not contain a supported DeviceLink identity key."
                }
                check(DeviceIdentityStore.peerIdFor(actualKey) == expectedPeerId) {
                    "Peer certificate does not match the expected DeviceLink peer ID."
                }
                check(MessageDigest.isEqual(expectedPeerPublicKey, actualKey)) {
                    "Peer certificate public key does not match the expected pinned key."
                }
                socket.soTimeout = 0
                return AndroidTlsSession(socket, expectedPeerId, tlsFactory)
            } catch (error: Exception) {
                try {
                    socket.close()
                } catch (_: Exception) {
                    // Preserve the handshake or validation failure.
                }
                throw error
            }
        }

        private const val TLS_1_3 = "TLSv1.3"
    }
}
