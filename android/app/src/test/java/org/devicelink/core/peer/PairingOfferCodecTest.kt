package org.devicelink.core.peer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class PairingOfferCodecTest {
    @Test
    fun roundTripsCanonicalOffer() {
        val publicKey = testPublicKey()
        val now = 1_800_000_000_000L
        val offer = PairingOffer(
            protocolVersion = PairingOffer.SUPPORTED_PROTOCOL_VERSION,
            peerId = DeviceIdentityStore.peerIdFor(publicKey),
            publicKeyEncoded = publicKey,
            nonce = ByteArray(PairingOffer.NONCE_BYTES) { it.toByte() },
            expiresAtEpochMillis = now + 60_000
        )

        val decoded = PairingOfferCodec.decode(PairingOfferCodec.encode(offer, now), now)

        assertEquals(offer.protocolVersion, decoded.protocolVersion)
        assertEquals(offer.peerId, decoded.peerId)
        assertArrayEquals(offer.publicKeyEncoded, decoded.publicKeyEncoded)
        assertArrayEquals(offer.nonce, decoded.nonce)
        assertEquals(offer.expiresAtEpochMillis, decoded.expiresAtEpochMillis)
    }

    @Test
    fun rejectsExpiredOffer() {
        val now = 1_800_000_000_000L
        val offer = validOffer(now - 1)
        assertThrows(IllegalArgumentException::class.java) {
            PairingOfferCodec.encode(offer, now)
        }
    }

    @Test
    fun rejectsWrongPrefixAndMalformedBase64() {
        assertThrows(IllegalArgumentException::class.java) {
            PairingOfferCodec.decode("OTHER:AAAA", 1_800_000_000_000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingOfferCodec.decode("DLPAIR1:%%%", 1_800_000_000_000L)
        }
    }

    @Test
    fun rejectsTrailingBinaryData() {
        val now = 1_800_000_000_000L
        val encoded = PairingOfferCodec.encode(validOffer(now + 60_000), now)
        val raw = java.util.Base64.getUrlDecoder().decode(encoded.removePrefix("DLPAIR1:"))
        val extended = raw + byteArrayOf(0x01)
        val malformed = "DLPAIR1:" + java.util.Base64.getUrlEncoder()
            .withoutPadding().encodeToString(extended)

        assertThrows(IllegalArgumentException::class.java) {
            PairingOfferCodec.decode(malformed, now)
        }
    }

    private fun validOffer(expiry: Long): PairingOffer {
        val key = testPublicKey()
        return PairingOffer(
            PairingOffer.SUPPORTED_PROTOCOL_VERSION,
            DeviceIdentityStore.peerIdFor(key),
            key,
            ByteArray(PairingOffer.NONCE_BYTES) { (it + 1).toByte() },
            expiry
        )
    }

    private fun testPublicKey(): ByteArray {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair().public.encoded
    }
}
