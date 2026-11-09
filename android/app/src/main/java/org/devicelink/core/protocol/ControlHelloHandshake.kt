package org.devicelink.core.protocol

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Performs the first application-protocol exchange inside an already authenticated
 * transport. TLS authenticates the certificate; this HELLO binds the protocol
 * message identity to the peer identity pinned by that TLS session.
 *
 * Both endpoints write HELLO before reading, so the exchange is symmetric and
 * does not depend on which endpoint initiated TCP.
 */
object ControlHelloHandshake {
    fun exchange(
        localPeerId: String,
        expectedRemotePeerId: String,
        input: InputStream,
        output: OutputStream,
        messageId: String = UUID.randomUUID().toString(),
    ): String {
        require(localPeerId.isNotBlank() && localPeerId.toByteArray(Charsets.UTF_8).size <=
            ControlMessage.MAX_PEER_ID_BYTES) { "Local peer ID is invalid." }
        require(expectedRemotePeerId.isNotBlank() &&
            expectedRemotePeerId.toByteArray(Charsets.UTF_8).size <=
            ControlMessage.MAX_PEER_ID_BYTES) { "Expected remote peer ID is invalid." }

        val hello = ControlMessage(
            version = ControlMessage.CURRENT_PROTOCOL_VERSION,
            messageId = messageId,
            senderPeerId = localPeerId,
            kind = MessageKind.HELLO,
            payload = byteArrayOf(),
        )
        ControlFrameStream.writeFrame(output, hello)
        output.flush()

        val frame = ControlFrameStream.readFrame(input)
            ?: throw EOFException("Peer closed the connection before sending HELLO.")
        val remoteHello = when (val decoded = ControlMessageCodec.decode(frame)) {
            is DecodeResult.Success -> decoded.message
            is DecodeResult.Invalid -> throw IOException(
                "Peer sent an invalid HELLO frame: ${decoded.error}."
            )
        }
        if (remoteHello.kind != MessageKind.HELLO || remoteHello.payload.isNotEmpty()) {
            throw IOException("The first application message must be an empty HELLO.")
        }
        if (remoteHello.senderPeerId != expectedRemotePeerId) {
            throw SecurityException("HELLO identity does not match the authenticated TLS peer.")
        }
        return remoteHello.senderPeerId
    }
}
