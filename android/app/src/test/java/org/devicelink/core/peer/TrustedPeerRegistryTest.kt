package org.devicelink.core.peer

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.*
import org.junit.Test

class TrustedPeerRegistryTest {
    private class FakeRepository : TrustedPeerRepository {
        private val records = linkedMapOf<String, TrustedPeerRecord>()
        override fun find(peerId: String) = records[peerId]
        override fun all() = records.values.toList()
        override fun put(record: TrustedPeerRecord) { records[record.peerId] = record }
        override fun remove(peerId: String) = records.remove(peerId) != null
    }

    private fun key(): ByteArray {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair().public.encoded
    }

    private fun completedState(expiry: Long = 10_000) =
        PairingConfirmationState(expiry, localConfirmed = true, remoteConfirmed = true)

    @Test fun trustRequiresBothConfirmationsAndUnexpiredPairing() {
        val registry = TrustedPeerRegistry(FakeRepository())
        val publicKey = key()
        val peerId = DeviceIdentityStore.peerIdFor(publicKey)
        assertEquals(TrustPeerResult.PairingNotComplete, registry.trustAfterPairing(
            peerId, publicKey, "Tablet", 1,
            PairingConfirmationState(10_000, localConfirmed = true), 1_000))
        assertEquals(TrustPeerResult.PairingExpired, registry.trustAfterPairing(
            peerId, publicKey, "Tablet", 1, completedState(1_000), 1_000))
    }

    @Test fun trustsPeerOnlyWhenIdentityAndMetadataAreValid() {
        val registry = TrustedPeerRegistry(FakeRepository())
        val publicKey = key()
        val peerId = DeviceIdentityStore.peerIdFor(publicKey)
        assertEquals(TrustPeerResult.Trusted, registry.trustAfterPairing(
            peerId, publicKey, "  Tablet  ", 1, completedState(), 2_000))
        val record = registry.find(peerId)!!
        assertEquals("Tablet", record.displayName)
        assertEquals(2_000L, record.pairedAtEpochMillis)
        assertEquals(peerId, DeviceIdentityStore.peerIdFor(record.publicKeyEncoded))
    }

    @Test fun rejectsIdentitySubstitutionAndInvalidDisplayName() {
        val registry = TrustedPeerRegistry(FakeRepository())
        val keyA = key()
        val keyB = key()
        assertEquals(TrustPeerResult.InvalidIdentity, registry.trustAfterPairing(
            DeviceIdentityStore.peerIdFor(keyA), keyB, "Tablet", 1, completedState(), 1_000))
        val malformedKey = ByteArray(91) { it.toByte() }
        assertEquals(TrustPeerResult.InvalidIdentity, registry.trustAfterPairing(
            DeviceIdentityStore.peerIdFor(malformedKey), malformedKey, "Tablet", 1,
            completedState(), 1_000))
        assertEquals(TrustPeerResult.InvalidDisplayName, registry.trustAfterPairing(
            DeviceIdentityStore.peerIdFor(keyA), keyA, "  ", 1, completedState(), 1_000))
    }

    @Test fun duplicateTrustIsIdempotentButDifferentKeyIsConflict() {
        val repository = FakeRepository()
        val registry = TrustedPeerRegistry(repository)
        val publicKey = key()
        val peerId = DeviceIdentityStore.peerIdFor(publicKey)
        assertEquals(TrustPeerResult.Trusted, registry.trustAfterPairing(
            peerId, publicKey, "Phone", 1, completedState(), 1_000))
        assertEquals(TrustPeerResult.AlreadyTrusted, registry.trustAfterPairing(
            peerId, publicKey, "Phone", 1, completedState(), 2_000))
        repository.put(TrustedPeerRecord(peerId, key(), "Unexpected", 1_000, 1))
        assertEquals(TrustPeerResult.IdentityConflict, registry.trustAfterPairing(
            peerId, publicKey, "Phone", 1, completedState(), 3_000))
    }

    @Test fun revocationRemovesTrustAndDefensiveCopiesProtectKeyMaterial() {
        val registry = TrustedPeerRegistry(FakeRepository())
        val publicKey = key()
        val peerId = DeviceIdentityStore.peerIdFor(publicKey)
        assertEquals(TrustPeerResult.Trusted, registry.trustAfterPairing(
            peerId, publicKey, "Phone", 1, completedState(), 1_000))
        val exposed = registry.find(peerId)!!.publicKeyEncoded
        exposed[0] = (exposed[0].toInt() xor 0xff).toByte()
        assertEquals(peerId, DeviceIdentityStore.peerIdFor(registry.find(peerId)!!.publicKeyEncoded))
        assertTrue(registry.revoke(peerId))
        assertNull(registry.find(peerId))
        assertFalse(registry.revoke(peerId))
        assertTrue(registry.all().isEmpty())
    }
}
