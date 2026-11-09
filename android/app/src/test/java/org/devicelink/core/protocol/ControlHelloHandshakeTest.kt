package org.devicelink.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class ControlHelloHandshakeTest {

    @Test
    fun exchangesHelloAndBindsExpectedRemoteIdentity() {
        val remoteBytes = encodeRemoteHello("dl1:remote")
        val output = ByteArrayOutputStream()

        val peerId = ControlHelloHandshake.exchange(
            localPeerId = "dl1:local",
            expectedRemotePeerId = "dl1:remote",
            input = ByteArrayInputStream(remoteBytes),
            output = output,
            messageId = "local-hello",
        )

        assertEquals("dl1:remote", peerId)
        val writtenFrame = ControlFrameStream.readFrame(ByteArrayInputStream(output.toByteArray()))!!
        val written = (ControlMessageCodec.decode(writtenFrame) as DecodeResult.Success).message
        assertEquals(MessageKind.HELLO, written.kind)
        assertEquals("dl1:local", written.senderPeerId)
        assertEquals("local-hello", written.messageId)
    }

    @Test
    fun rejectsHelloFromDifferentTlsPeerIdentity() {
        assertThrows(SecurityException::class.java) {
            exchangeWith(encodeRemoteHello("dl1:attacker"))
        }
    }

    @Test
    fun rejectsNonHelloAsFirstMessage() {
        val bytes = encode(
            ControlMessage(
                ControlMessage.CURRENT_PROTOCOL_VERSION,
                "remote-command",
                "dl1:remote",
                MessageKind.COMMAND,
                byteArrayOf(),
            )
        )
        assertThrows(IOException::class.java) { exchangeWith(bytes) }
    }

    @Test
    fun rejectsHelloWithUnexpectedPayload() {
        val bytes = encode(
            ControlMessage(
                ControlMessage.CURRENT_PROTOCOL_VERSION,
                "remote-hello",
                "dl1:remote",
                MessageKind.HELLO,
                byteArrayOf(1),
            )
        )
        assertThrows(IOException::class.java) { exchangeWith(bytes) }
    }

    @Test
    fun rejectsCleanEofBeforeRemoteHello() {
        assertThrows(java.io.EOFException::class.java) {
            exchangeWith(byteArrayOf())
        }
    }

    private fun exchangeWith(remoteFrame: ByteArray) {
        ControlHelloHandshake.exchange(
            localPeerId = "dl1:local",
            expectedRemotePeerId = "dl1:remote",
            input = ByteArrayInputStream(remoteFrame),
            output = ByteArrayOutputStream(),
            messageId = "local-hello",
        )
    }

    private fun encodeRemoteHello(peerId: String): ByteArray = encode(
        ControlMessage(
            ControlMessage.CURRENT_PROTOCOL_VERSION,
            "remote-hello",
            peerId,
            MessageKind.HELLO,
            byteArrayOf(),
        )
    )

    private fun encode(message: ControlMessage): ByteArray =
        ByteArrayOutputStream().also { ControlFrameStream.writeFrame(it, message) }.toByteArray()
}
