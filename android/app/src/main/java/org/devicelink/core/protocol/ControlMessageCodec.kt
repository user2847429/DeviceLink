package org.devicelink.core.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Versioned, bounded binary encoding for control-plane messages.
 *
 * This codec provides framing only. It does not authenticate or encrypt data;
 * callers must use the authenticated transport before exchanging messages.
 */
object ControlMessageCodec {
    private val MAGIC = byteArrayOf(0x44, 0x4c, 0x43, 0x50) // "DLCP"
    private const val FRAME_FORMAT_VERSION = 1
    private const val HEADER_BYTES = 18
    const val MAX_FRAME_BYTES =
        HEADER_BYTES + ControlMessage.MAX_MESSAGE_ID_BYTES +
            ControlMessage.MAX_PEER_ID_BYTES + ControlMessage.MAX_CONTROL_PAYLOAD_BYTES

    private val kindCodes = mapOf(
        MessageKind.HELLO to 1,
        MessageKind.PAIRING to 2,
        MessageKind.CAPABILITIES to 3,
        MessageKind.COMMAND to 4,
        MessageKind.EVENT to 5,
        MessageKind.ACKNOWLEDGEMENT to 6,
        MessageKind.TRANSFER_OFFER to 7,
        MessageKind.TRANSFER_ACCEPT to 8,
        MessageKind.TRANSFER_CHUNK to 9,
        MessageKind.TRANSFER_FINISH to 10,
    )
    private val kindsByCode = kindCodes.entries.associate { (kind, code) -> code to kind }

    fun encode(message: ControlMessage): ByteArray {
        require(message.validate() == ValidationResult.Valid) {
            "Invalid control message: ${message.validate()}"
        }

        val messageId = message.messageId.toByteArray(Charsets.UTF_8)
        val peerId = message.senderPeerId.toByteArray(Charsets.UTF_8)
        val kindCode = checkNotNull(kindCodes[message.kind])

        val output = ByteArrayOutputStream(HEADER_BYTES + messageId.size + peerId.size + message.payload.size)
        DataOutputStream(output).use { data ->
            data.write(MAGIC)
            data.writeByte(FRAME_FORMAT_VERSION)
            data.writeInt(message.version)
            data.writeByte(kindCode)
            data.writeShort(messageId.size)
            data.writeShort(peerId.size)
            data.writeInt(message.payload.size)
            data.write(messageId)
            data.write(peerId)
            data.write(message.payload)
        }
        return output.toByteArray()
    }

    fun decode(frame: ByteArray): DecodeResult {
        if (frame.size < HEADER_BYTES) return DecodeResult.Invalid(DecodeError.FrameTooShort)
        if (frame.size > MAX_FRAME_BYTES) return DecodeResult.Invalid(DecodeError.FrameTooLarge)

        return try {
            val input = DataInputStream(ByteArrayInputStream(frame))
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            if (!magic.contentEquals(MAGIC)) {
                return DecodeResult.Invalid(DecodeError.InvalidMagic)
            }

            val formatVersion = input.readUnsignedByte()
            if (formatVersion != FRAME_FORMAT_VERSION) {
                return DecodeResult.Invalid(DecodeError.UnsupportedFrameFormat)
            }

            val protocolVersion = input.readInt()
            val kind = kindsByCode[input.readUnsignedByte()]
                ?: return DecodeResult.Invalid(DecodeError.UnknownMessageKind)
            val messageIdLength = input.readUnsignedShort()
            val peerIdLength = input.readUnsignedShort()
            val payloadLength = input.readInt()

            if (messageIdLength > ControlMessage.MAX_MESSAGE_ID_BYTES ||
                peerIdLength > ControlMessage.MAX_PEER_ID_BYTES ||
                payloadLength < 0 ||
                payloadLength > ControlMessage.MAX_CONTROL_PAYLOAD_BYTES
            ) {
                return DecodeResult.Invalid(DecodeError.InvalidFieldLength)
            }

            val expectedSize = HEADER_BYTES.toLong() + messageIdLength +
                peerIdLength + payloadLength
            if (expectedSize != frame.size.toLong()) {
                return DecodeResult.Invalid(DecodeError.InvalidFrameLength)
            }

            val messageId = readUtf8(input, messageIdLength)
                ?: return DecodeResult.Invalid(DecodeError.InvalidUtf8)
            val peerId = readUtf8(input, peerIdLength)
                ?: return DecodeResult.Invalid(DecodeError.InvalidUtf8)
            val payload = ByteArray(payloadLength)
            input.readFully(payload)

            val message = ControlMessage(
                version = protocolVersion,
                messageId = messageId,
                senderPeerId = peerId,
                kind = kind,
                payload = payload,
            )
            when (message.validate()) {
                ValidationResult.Valid -> DecodeResult.Success(message)
                ValidationResult.UnsupportedVersion -> DecodeResult.Invalid(DecodeError.UnsupportedProtocolVersion)
                ValidationResult.EmptyMessageId,
                ValidationResult.MessageIdTooLong,
                ValidationResult.EmptyPeerId,
                ValidationResult.PeerIdTooLong,
                ValidationResult.PayloadTooLarge -> DecodeResult.Invalid(DecodeError.InvalidMessage)
            }
        } catch (_: Exception) {
            DecodeResult.Invalid(DecodeError.MalformedFrame)
        }
    }

    private fun readUtf8(input: DataInputStream, length: Int): String? {
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            null
        }
    }
}

sealed class DecodeResult {
    data class Success(val message: ControlMessage) : DecodeResult()
    data class Invalid(val error: DecodeError) : DecodeResult()
}

enum class DecodeError {
    FrameTooShort,
    FrameTooLarge,
    InvalidMagic,
    UnsupportedFrameFormat,
    UnknownMessageKind,
    InvalidFieldLength,
    InvalidFrameLength,
    InvalidUtf8,
    UnsupportedProtocolVersion,
    InvalidMessage,
    MalformedFrame,
}
