package org.devicelink.core.peer

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingProtocolTest {
    private fun publicKey(): ByteArray {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair().public.encoded
    }

    @Test
    fun validatesOfferAndRejectsExpiredOrMismatchedIdentity() {
        val key = publicKey()
        val now = 1_000_000L
        val offer = PairingOffer(
            protocolVersion = 1,
            peerId = DeviceIdentityStore.peerIdFor(key),
            publicKeyEncoded = key,
            nonce = ByteArray(32) { it.toByte() },
            expiresAtEpochMillis = now + 60_000,
        )
        assertEquals(PairingOfferValidation.Valid, offer.validate(now))
        assertEquals(PairingOfferValidation.Expired, offer.validate(now + 60_000))
        assertEquals(
            PairingOfferValidation.PeerIdMismatch,
            offer.copy(peerId = "dl1:wrong").validate(now),
        )
        assertEquals(
            PairingOfferValidation.InvalidNonce,
            offer.copy(nonce = ByteArray(8)).validate(now),
        )
        assertEquals(
            PairingOfferValidation.ExpiryTooFar,
            offer.copy(expiresAtEpochMillis = now + PairingOffer.MAX_OFFER_LIFETIME_MILLIS + 1)
                .validate(now),
        )
    }

    @Test
    fun confirmationCodeIsSymmetricAndBoundToSessionContext() {
        val exporter = ByteArray(32) { (it + 1).toByte() }
        val nonce = ByteArray(32) { (it + 9).toByte() }
        val code = PairingConfirmationCode.derive(exporter, "peer-a", "peer-b", nonce)
        assertEquals(6, code.length)
        assertTrue(code.all { it in '0'..'9' })
        assertEquals(
            code,
            PairingConfirmationCode.derive(exporter, "peer-b", "peer-a", nonce),
        )
        assertNotEquals(
            code,
            PairingConfirmationCode.derive(exporter, "peer-a", "peer-b", ByteArray(32)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesShortExporterSecret() {
        PairingConfirmationCode.derive(ByteArray(16), "peer-a", "peer-b", ByteArray(32))
    }

    @Test
    fun pairingCompletesOnlyAfterBothSidesConfirm() {
        val initial = PairingConfirmationState(expiresAtEpochMillis = 10_000)
        val first = initial.confirm(ConfirmationSide.LOCAL, 1_000)
        assertEquals(PairingConfirmationStatus.WaitingForOtherPeer, first.status)
        assertEquals(false, first.state.remoteConfirmed)

        val second = first.state.confirm(ConfirmationSide.REMOTE, 2_000)
        assertEquals(PairingConfirmationStatus.Complete, second.status)
        assertEquals(true, second.state.localConfirmed)
        assertEquals(true, second.state.remoteConfirmed)
        assertEquals(
            PairingConfirmationStatus.AlreadyComplete,
            second.state.confirm(ConfirmationSide.LOCAL, 3_000).status,
        )
    }

    @Test
    fun expiredPairingCannotBeConfirmed() {
        val state = PairingConfirmationState(expiresAtEpochMillis = 5_000)
        val result = state.confirm(ConfirmationSide.LOCAL, 5_000)
        assertEquals(PairingConfirmationStatus.Expired, result.status)
        assertEquals(false, result.state.localConfirmed)
    }
}
