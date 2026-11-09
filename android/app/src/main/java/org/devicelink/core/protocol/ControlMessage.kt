package org.devicelink.core.protocol

/** Versioned in-memory model for DeviceLink control-plane messages. */
enum class MessageKind {
    HELLO,
    PAIRING,
    CAPABILITIES,
    COMMAND,
    EVENT,
    ACKNOWLEDGEMENT,
    TRANSFER_OFFER,
    TRANSFER_ACCEPT,
    TRANSFER_CHUNK,
    TRANSFER_FINISH,
}

data class ControlMessage(
    val version: Int,
    val messageId: String,
    val senderPeerId: String,
    val kind: MessageKind,
    val payload: ByteArray,
) {
    fun validate(): ValidationResult = when {
        version != CURRENT_PROTOCOL_VERSION -> ValidationResult.UnsupportedVersion
        messageId.isEmpty() -> ValidationResult.EmptyMessageId
        messageId.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_ID_BYTES ->
            ValidationResult.MessageIdTooLong
        senderPeerId.isEmpty() -> ValidationResult.EmptyPeerId
        senderPeerId.toByteArray(Charsets.UTF_8).size > MAX_PEER_ID_BYTES ->
            ValidationResult.PeerIdTooLong
        payload.size > MAX_CONTROL_PAYLOAD_BYTES -> ValidationResult.PayloadTooLarge
        else -> ValidationResult.Valid
    }

    companion object {
        const val CURRENT_PROTOCOL_VERSION = 1
        const val MAX_CONTROL_PAYLOAD_BYTES = 64 * 1024
        const val MAX_PEER_ID_BYTES = 128
        const val MAX_MESSAGE_ID_BYTES = 128
    }
}

enum class ValidationResult {
    Valid,
    UnsupportedVersion,
    EmptyMessageId,
    MessageIdTooLong,
    EmptyPeerId,
    PeerIdTooLong,
    PayloadTooLarge,
}
