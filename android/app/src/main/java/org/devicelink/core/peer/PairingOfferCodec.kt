package org.devicelink.core.peer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Canonical, bounded text representation for QR pairing invitations.
 *
 * Encoding is deliberately independent from QR rendering/scanning libraries.
 * The payload is public metadata, not a secret or a trust credential.
 */
object PairingOfferCodec {
    private const val PREFIX = "DLPAIR1:"
    private val MAGIC = byteArrayOf(0x44, 0x4c, 0x50, 0x4f) // DLPO
    private const val FORMAT_VERSION = 1
    private const val FIXED_BYTES = 4 + 1 + 4 + 2 + 2 +
        PairingOffer.NONCE_BYTES + 8
    const val MAX_ENCODED_CHARS = 2_048
    private const val MAX_PEER_ID_BYTES = 128
    private const val MAX_BINARY_BYTES = FIXED_BYTES +
        MAX_PEER_ID_BYTES + PairingOffer.MAX_PUBLIC_KEY_BYTES

    fun encode(offer: PairingOffer, nowEpochMillis: Long): String {
        require(offer.validate(nowEpochMillis) == PairingOfferValidation.Valid) {
            "Pairing offer is invalid or expired."
        }
        val peerId = offer.peerId.toByteArray(StandardCharsets.UTF_8)
        require(peerId.isNotEmpty() && peerId.size <= MAX_PEER_ID_BYTES) {
            "Pairing peer ID length is invalid."
        }
        val publicKey = offer.publicKeyEncoded.clone()
        val nonce = offer.nonce.clone()
        val buffer = ByteBuffer.allocate(
            FIXED_BYTES + peerId.size + publicKey.size
        ).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MAGIC)
        buffer.put(FORMAT_VERSION.toByte())
        buffer.putInt(offer.protocolVersion)
        buffer.putShort(peerId.size.toShort())
        buffer.putShort(publicKey.size.toShort())
        buffer.put(nonce)
        buffer.putLong(offer.expiresAtEpochMillis)
        buffer.put(peerId)
        buffer.put(publicKey)
        val encoded = PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(buffer.array())
        require(encoded.length <= MAX_ENCODED_CHARS) {
            "Pairing offer is too large to encode."
        }
        return encoded
    }

    fun decode(encoded: String, nowEpochMillis: Long): PairingOffer {
        require(encoded.length in (PREFIX.length + 1)..MAX_ENCODED_CHARS) {
            "Pairing offer text length is invalid."
        }
        require(encoded.startsWith(PREFIX)) { "Pairing offer prefix is invalid." }
        val payload = try {
            Base64.getUrlDecoder().decode(encoded.substring(PREFIX.length))
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Pairing offer is not valid base64url.", error)
        }
        require(payload.size in FIXED_BYTES..MAX_BINARY_BYTES) {
            "Pairing offer binary length is invalid."
        }

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC.size)
        buffer.get(magic)
        require(magic.contentEquals(MAGIC)) { "Pairing offer magic is invalid." }
        require((buffer.get().toInt() and 0xff) == FORMAT_VERSION) {
            "Pairing offer format version is unsupported."
        }
        val protocolVersion = buffer.int
        val peerIdLength = buffer.short.toInt() and 0xffff
        val publicKeyLength = buffer.short.toInt() and 0xffff
        require(peerIdLength in 1..MAX_PEER_ID_BYTES) {
            "Pairing peer ID length is invalid."
        }
        require(publicKeyLength in PairingOffer.MIN_PUBLIC_KEY_BYTES..PairingOffer.MAX_PUBLIC_KEY_BYTES) {
            "Pairing public key length is invalid."
        }
        val nonce = ByteArray(PairingOffer.NONCE_BYTES)
        buffer.get(nonce)
        val expiresAt = buffer.long
        require(buffer.remaining() == peerIdLength + publicKeyLength) {
            "Pairing offer fields do not match the encoded length."
        }
        val peerIdBytes = ByteArray(peerIdLength)
        buffer.get(peerIdBytes)
        val peerId = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(peerIdBytes))
                .toString()
        } catch (error: Exception) {
            throw IllegalArgumentException("Pairing peer ID is not valid UTF-8.", error)
        }
        val publicKey = ByteArray(publicKeyLength)
        buffer.get(publicKey)
        require(!buffer.hasRemaining()) { "Pairing offer has trailing data." }

        val offer = PairingOffer(
            protocolVersion = protocolVersion,
            peerId = peerId,
            publicKeyEncoded = publicKey,
            nonce = nonce,
            expiresAtEpochMillis = expiresAt
        )
        require(offer.validate(nowEpochMillis) == PairingOfferValidation.Valid) {
            "Pairing offer validation failed."
        }
        return offer
    }
}
