package org.devicelink.core.peer

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Public data encoded in a pairing QR invitation.
 *
 * This is a data contract only; QR rendering/scanning and transport are not
 * implemented here. The nonce is fresh per invitation and is never a secret.
 */
data class PairingOffer(
    val protocolVersion: Int,
    val peerId: String,
    val publicKeyEncoded: ByteArray,
    val nonce: ByteArray,
    val expiresAtEpochMillis: Long,
) {
    fun validate(nowEpochMillis: Long): PairingOfferValidation {
        if (protocolVersion != SUPPORTED_PROTOCOL_VERSION) {
            return PairingOfferValidation.UnsupportedProtocolVersion
        }
        if (publicKeyEncoded.size !in MIN_PUBLIC_KEY_BYTES..MAX_PUBLIC_KEY_BYTES) {
            return PairingOfferValidation.InvalidPublicKey
        }
        if (!DeviceIdentityStore.isSupportedPublicKey(publicKeyEncoded)) {
            return PairingOfferValidation.InvalidPublicKey
        }
        val expectedPeerId = try {
            DeviceIdentityStore.peerIdFor(publicKeyEncoded)
        } catch (_: IllegalArgumentException) {
            return PairingOfferValidation.InvalidPublicKey
        }
        if (peerId != expectedPeerId) return PairingOfferValidation.PeerIdMismatch
        if (nonce.size != NONCE_BYTES) return PairingOfferValidation.InvalidNonce
        if (expiresAtEpochMillis <= nowEpochMillis) return PairingOfferValidation.Expired
        if (expiresAtEpochMillis - nowEpochMillis > MAX_OFFER_LIFETIME_MILLIS) {
            return PairingOfferValidation.ExpiryTooFar
        }
        return PairingOfferValidation.Valid
    }

    companion object {
        const val SUPPORTED_PROTOCOL_VERSION = 1
        const val NONCE_BYTES = 32
        const val MIN_PUBLIC_KEY_BYTES = 64
        const val MAX_PUBLIC_KEY_BYTES = 1024
        const val MAX_OFFER_LIFETIME_MILLIS = 5 * 60 * 1000L
    }
}

enum class PairingOfferValidation {
    Valid,
    UnsupportedProtocolVersion,
    InvalidPublicKey,
    PeerIdMismatch,
    InvalidNonce,
    Expired,
    ExpiryTooFar,
}

/**
 * Produces the human comparison code from secret material exported by the
 * authenticated TLS session. It must never be called with QR contents,
 * a public nonce, or an arbitrary locally generated value as the secret.
 *
 * Both peers supply the same exporter secret and identities; sorting the
 * identities gives both sides the same context regardless of role.
 */
object PairingConfirmationCode {
    private const val ALGORITHM = "HmacSHA256"
    private const val DOMAIN = "DeviceLink pairing confirmation v1"

    fun derive(
        tlsExporterSecret: ByteArray,
        firstPeerId: String,
        secondPeerId: String,
        sessionNonce: ByteArray,
    ): String {
        require(tlsExporterSecret.size >= MIN_EXPORTER_SECRET_BYTES) {
            "TLS exporter secret is too short."
        }
        require(firstPeerId.isNotBlank() && secondPeerId.isNotBlank()) {
            "Peer identities must not be blank."
        }
        require(firstPeerId != secondPeerId) { "Cannot pair an identity with itself." }
        require(sessionNonce.size == PairingOffer.NONCE_BYTES) {
            "Session nonce must be 32 bytes."
        }

        val peers = listOf(firstPeerId, secondPeerId).sorted()
        val first = peers[0].toByteArray(StandardCharsets.UTF_8)
        val second = peers[1].toByteArray(StandardCharsets.UTF_8)
        val domain = DOMAIN.toByteArray(StandardCharsets.UTF_8)
        val context = ByteBuffer.allocate(4 + domain.size + 4 + first.size + 4 + second.size + sessionNonce.size)
            .putInt(domain.size).put(domain)
            .putInt(first.size).put(first)
            .putInt(second.size).put(second)
            .put(sessionNonce)
            .array()

        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(tlsExporterSecret, ALGORITHM))
        val digest = mac.doFinal(context)
        val value = ((digest[0].toLong() and 0xff) shl 24) or
            ((digest[1].toLong() and 0xff) shl 16) or
            ((digest[2].toLong() and 0xff) shl 8) or
            (digest[3].toLong() and 0xff)
        return (value % CODE_SPACE).toString().padStart(CODE_DIGITS, '0')
    }

    const val CODE_DIGITS = 6
    private const val CODE_SPACE = 1_000_000L
    private const val MIN_EXPORTER_SECRET_BYTES = 32
}

/** Immutable two-sided confirmation state. No peer is trusted until COMPLETE. */
data class PairingConfirmationState(
    val expiresAtEpochMillis: Long,
    val localConfirmed: Boolean = false,
    val remoteConfirmed: Boolean = false,
) {
    fun confirm(side: ConfirmationSide, nowEpochMillis: Long): PairingConfirmationProgress {
        if (nowEpochMillis >= expiresAtEpochMillis) {
            return PairingConfirmationProgress(this, PairingConfirmationStatus.Expired)
        }
        if (localConfirmed && remoteConfirmed) {
            return PairingConfirmationProgress(this, PairingConfirmationStatus.AlreadyComplete)
        }

        val next = when (side) {
            ConfirmationSide.LOCAL -> copy(localConfirmed = true)
            ConfirmationSide.REMOTE -> copy(remoteConfirmed = true)
        }
        val status = if (next.localConfirmed && next.remoteConfirmed) {
            PairingConfirmationStatus.Complete
        } else {
            PairingConfirmationStatus.WaitingForOtherPeer
        }
        return PairingConfirmationProgress(next, status)
    }
}

enum class ConfirmationSide { LOCAL, REMOTE }

enum class PairingConfirmationStatus {
    WaitingForOtherPeer,
    Complete,
    Expired,
    AlreadyComplete,
}

data class PairingConfirmationProgress(
    val state: PairingConfirmationState,
    val status: PairingConfirmationStatus,
)
