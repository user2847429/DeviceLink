package org.devicelink.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlMessageCodecTest {
    private fun message(
        kind: MessageKind = MessageKind.HELLO,
        payload: ByteArray = byteArrayOf(0x01, 0x02, 0x03),
    ) = ControlMessage(
        version = ControlMessage.CURRENT_PROTOCOL_VERSION,
        messageId = "message-1",
        senderPeerId = "dl1:peer-é",
        kind = kind,
        payload = payload,
    )

    @Test
    fun roundTripsEveryMessageKind() {
        MessageKind.values().forEach { kind ->
            val original = message(kind = kind)
            val decoded = ControlMessageCodec.decode(ControlMessageCodec.encode(original))
            assertTrue("Failed for $kind: $decoded", decoded is DecodeResult.Success)
            val actual = (decoded as DecodeResult.Success).message
            assertEquals(original.version, actual.version)
            assertEquals(original.messageId, actual.messageId)
            assertEquals(original.senderPeerId, actual.senderPeerId)
            assertEquals(original.kind, actual.kind)
            assertArrayEquals(original.payload, actual.payload)
        }
    }

    @Test
    fun roundTripsMaximumSizeMessage() {
        val original = ControlMessage(
            version = 1,
            messageId = "m".repeat(ControlMessage.MAX_MESSAGE_ID_BYTES),
            senderPeerId = "p".repeat(ControlMessage.MAX_PEER_ID_BYTES),
            kind = MessageKind.EVENT,
            payload = ByteArray(ControlMessage.MAX_CONTROL_PAYLOAD_BYTES) { it.toByte() },
        )
        val decoded = ControlMessageCodec.decode(ControlMessageCodec.encode(original))
        assertTrue(decoded is DecodeResult.Success)
        assertArrayEquals(original.payload, (decoded as DecodeResult.Success).message.payload)
    }

    @Test
    fun rejectsShortOversizedAndTrailingFrames() {
        assertEquals(
            DecodeError.FrameTooShort,
            (ControlMessageCodec.decode(ByteArray(4)) as DecodeResult.Invalid).error,
        )
        assertEquals(
            DecodeError.FrameTooLarge,
            (ControlMessageCodec.decode(ByteArray(ControlMessageCodec.MAX_FRAME_BYTES + 1)) as DecodeResult.Invalid).error,
        )
        val valid = ControlMessageCodec.encode(message())
        assertEquals(
            DecodeError.InvalidFrameLength,
            (ControlMessageCodec.decode(valid + byteArrayOf(0)) as DecodeResult.Invalid).error,
        )
    }

    @Test
    fun rejectsBadMagicAndUnknownKind() {
        val badMagic = ControlMessageCodec.encode(message()).also { it[0] = 0 }
        assertEquals(
            DecodeError.InvalidMagic,
            (ControlMessageCodec.decode(badMagic) as DecodeResult.Invalid).error,
        )
        val unknownKind = ControlMessageCodec.encode(message()).also { it[9] = 99 }
        assertEquals(
            DecodeError.UnknownMessageKind,
            (ControlMessageCodec.decode(unknownKind) as DecodeResult.Invalid).error,
        )
    }

    @Test
    fun rejectsInvalidUtf8() {
        val frame = ControlMessageCodec.encode(message())
        // Header is 18 bytes; message ID follows and is guaranteed non-empty.
        frame[18] = 0xC3.toByte()
        frame[19] = 0x28
        assertEquals(
            DecodeError.InvalidUtf8,
            (ControlMessageCodec.decode(frame) as DecodeResult.Invalid).error,
        )
    }

    @Test
    fun rejectsUnsupportedProtocolVersion() {
        val frame = ControlMessageCodec.encode(message())
        frame[5] = 0
        frame[6] = 0
        frame[7] = 0
        frame[8] = 99
        assertEquals(
            DecodeError.UnsupportedProtocolVersion,
            (ControlMessageCodec.decode(frame) as DecodeResult.Invalid).error,
        )
    }
}
