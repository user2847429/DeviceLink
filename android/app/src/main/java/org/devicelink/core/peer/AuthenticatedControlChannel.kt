package org.devicelink.core.peer

import org.devicelink.core.protocol.ControlFrameStream
import org.devicelink.core.protocol.ControlHelloHandshake
import org.devicelink.core.protocol.ControlMessage
import org.devicelink.core.protocol.ControlMessageCodec
import org.devicelink.core.protocol.DecodeResult
import org.devicelink.core.protocol.MessageKind
import java.io.EOFException
import java.io.IOException

/**
 * Control-plane channel available only after TLS authentication and a successful
 * identity-bound HELLO exchange. It does not itself authorize commands/capabilities.
 */
class AuthenticatedControlChannel private constructor(
    private val session: AndroidTlsSession,
    val localPeerId: String,
    val remotePeerId: String,
) : AutoCloseable {
    private val input = session.socket.getInputStream()
    private val output = session.socket.getOutputStream()

    /** Sends a protocol message whose sender must be this installation. */
    @Synchronized
    fun send(message: ControlMessage) {
        check(message.senderPeerId == localPeerId) {
            "Outgoing control message has the wrong sender identity."
        }
        require(message.kind != MessageKind.HELLO) {
            "HELLO is reserved for channel establishment."
        }
        require(message.validate() == org.devicelink.core.protocol.ValidationResult.Valid) {
            "Outgoing control message is invalid."
        }
        ControlFrameStream.writeFrame(output, message)
        output.flush()
    }

    /** Returns null on clean peer shutdown; malformed or misidentified messages fail closed. */
    @Synchronized
    fun receive(): ControlMessage? {
        val frame = ControlFrameStream.readFrame(input) ?: return null
        val message = when (val decoded = ControlMessageCodec.decode(frame)) {
            is DecodeResult.Success -> decoded.message
            is DecodeResult.Invalid -> throw IOException(
                "Peer sent an invalid control frame: ${decoded.error}."
            )
        }
        if (message.senderPeerId != remotePeerId) {
            throw SecurityException("Control message sender does not match the authenticated peer.")
        }
        if (message.kind == MessageKind.HELLO) {
            throw IOException("Unexpected HELLO after channel establishment.")
        }
        return message
    }

    override fun close() = session.close()

    companion object {
        const val DEFAULT_HELLO_TIMEOUT_MILLIS = 10_000

        /**
         * Establishes the application channel over a completed pinned TLS session.
         * Both peers must call this concurrently. Any failed or timed-out HELLO
         * closes the underlying TLS session.
         */
        fun establish(
            session: AndroidTlsSession,
            localPeerId: String,
            helloTimeoutMillis: Int = DEFAULT_HELLO_TIMEOUT_MILLIS,
        ): AuthenticatedControlChannel {
            require(helloTimeoutMillis > 0)
            val socket = session.socket
            try {
                socket.soTimeout = helloTimeoutMillis
                val remotePeerId = ControlHelloHandshake.exchange(
                    localPeerId = localPeerId,
                    expectedRemotePeerId = session.peerId,
                    input = socket.getInputStream(),
                    output = socket.getOutputStream(),
                )
                socket.soTimeout = 0
                return AuthenticatedControlChannel(session, localPeerId, remotePeerId)
            } catch (failure: Exception) {
                try { session.close() } catch (_: Exception) { }
                throw failure
            }
        }
    }
}
