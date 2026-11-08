package org.devicelink.core.peer

/** Immutable trusted-peer record with defensive key copies. */
class TrustedPeerRecord(
    val peerId: String,
    publicKeyEncoded: ByteArray,
    val displayName: String,
    val pairedAtEpochMillis: Long,
    val protocolVersion: Int,
) {
    private val encodedKey = publicKeyEncoded.clone()
    val publicKeyEncoded: ByteArray get() = encodedKey.clone()
    fun hasSamePublicKey(other: ByteArray): Boolean = encodedKey.contentEquals(other)
}

/** Persistence boundary. A production Android implementation must encrypt records at rest. */
interface TrustedPeerRepository {
    fun find(peerId: String): TrustedPeerRecord?
    fun all(): List<TrustedPeerRecord>
    fun put(record: TrustedPeerRecord)
    fun remove(peerId: String): Boolean
}

enum class TrustPeerResult {
    Trusted, AlreadyTrusted, PairingNotComplete, PairingExpired, InvalidIdentity,
    InvalidDisplayName, UnsupportedProtocolVersion, IdentityConflict
}

/** Local trust policy, independent from its storage backend. */
class TrustedPeerRegistry(private val repository: TrustedPeerRepository) {
    fun trustAfterPairing(
        peerId: String,
        publicKeyEncoded: ByteArray,
        displayName: String,
        protocolVersion: Int,
        confirmation: PairingConfirmationState,
        nowEpochMillis: Long,
    ): TrustPeerResult {
        if (!confirmation.localConfirmed || !confirmation.remoteConfirmed) {
            return TrustPeerResult.PairingNotComplete
        }
        if (nowEpochMillis >= confirmation.expiresAtEpochMillis) {
            return TrustPeerResult.PairingExpired
        }
        if (publicKeyEncoded.size !in PairingOffer.MIN_PUBLIC_KEY_BYTES..PairingOffer.MAX_PUBLIC_KEY_BYTES) {
            return TrustPeerResult.InvalidIdentity
        }
        if (!DeviceIdentityStore.isSupportedPublicKey(publicKeyEncoded)) {
            return TrustPeerResult.InvalidIdentity
        }
        val derivedPeerId = try {
            DeviceIdentityStore.peerIdFor(publicKeyEncoded)
        } catch (_: IllegalArgumentException) {
            return TrustPeerResult.InvalidIdentity
        }
        if (peerId != derivedPeerId) return TrustPeerResult.InvalidIdentity
        if (displayName.isBlank() || displayName.toByteArray(Charsets.UTF_8).size > MAX_DISPLAY_NAME_BYTES) {
            return TrustPeerResult.InvalidDisplayName
        }
        if (protocolVersion != PairingOffer.SUPPORTED_PROTOCOL_VERSION) {
            return TrustPeerResult.UnsupportedProtocolVersion
        }

        val existing = repository.find(peerId)
        if (existing != null) {
            return if (existing.hasSamePublicKey(publicKeyEncoded)) {
                TrustPeerResult.AlreadyTrusted
            } else {
                TrustPeerResult.IdentityConflict
            }
        }
        repository.put(
            TrustedPeerRecord(
                peerId, publicKeyEncoded, displayName.trim(), nowEpochMillis, protocolVersion
            )
        )
        return TrustPeerResult.Trusted
    }

    fun find(peerId: String): TrustedPeerRecord? = repository.find(peerId)

    fun all(): List<TrustedPeerRecord> = repository.all().map {
        TrustedPeerRecord(it.peerId, it.publicKeyEncoded, it.displayName, it.pairedAtEpochMillis, it.protocolVersion)
    }

    /** Callers must also terminate any active session for this peer. */
    fun revoke(peerId: String): Boolean = repository.remove(peerId)

    companion object { const val MAX_DISPLAY_NAME_BYTES = 128 }
}
