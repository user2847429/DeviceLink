package org.devicelink.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlMessageTest {
    private fun message(
        version: Int = ControlMessage.CURRENT_PROTOCOL_VERSION,
        messageId: String = "message-1",
        senderPeerId: String = "peer-1",
        payload: ByteArray = byteArrayOf(0x01, 0x02),
    ) = ControlMessage(
        version = version,
        messageId = messageId,
        senderPeerId = senderPeerId,
        kind = MessageKind.HELLO,
        payload = payload,
    )

    @Test
    fun acceptsValidMessage() {
        assertEquals(ValidationResult.Valid, message().validate())
    }

    @Test
    fun rejectsUnsupportedVersion() {
        assertEquals(ValidationResult.UnsupportedVersion, message(version = 99).validate())
    }

    @Test
    fun rejectsEmptyIdentifiers() {
        assertEquals(ValidationResult.EmptyMessageId, message(messageId = "").validate())
        assertEquals(ValidationResult.EmptyPeerId, message(senderPeerId = "").validate())
    }

    @Test
    fun measuresIdentifierLimitsInUtf8Bytes() {
        val accepted = "é".repeat(ControlMessage.MAX_MESSAGE_ID_BYTES / 2)
        val rejected = "é".repeat((ControlMessage.MAX_MESSAGE_ID_BYTES / 2) + 1)

        assertEquals(ValidationResult.Valid, message(messageId = accepted).validate())
        assertEquals(ValidationResult.MessageIdTooLong, message(messageId = rejected).validate())
    }

    @Test
    fun measuresPeerIdentifierLimitsInUtf8Bytes() {
        val accepted = "界".repeat(ControlMessage.MAX_PEER_ID_BYTES / 3)
        val rejected = "界".repeat((ControlMessage.MAX_PEER_ID_BYTES / 3) + 1)

        assertEquals(ValidationResult.Valid, message(senderPeerId = accepted).validate())
        assertEquals(ValidationResult.PeerIdTooLong, message(senderPeerId = rejected).validate())
    }

    @Test
    fun acceptsPayloadAtLimitAndRejectsPayloadAboveLimit() {
        val limit = ControlMessage.MAX_CONTROL_PAYLOAD_BYTES
        assertEquals(ValidationResult.Valid, message(payload = ByteArray(limit)).validate())
        assertEquals(
            ValidationResult.PayloadTooLarge,
            message(payload = ByteArray(limit + 1)).validate(),
        )
    }
}
