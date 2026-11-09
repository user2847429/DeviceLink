package org.devicelink.core.peer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class AndroidEncryptedTrustedPeerRepositoryInstrumentedTest {
    @Test
    fun persistsTrustedPeerEncryptedAndSupportsReopenAndRevoke() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString()
        val identityAlias = "devicelink.repo.identity.$suffix"
        val encryptionAlias = "devicelink.repo.aes.$suffix"
        val file = File(context.filesDir, "trusted-peers.v1.enc")
        val identityStore = DeviceIdentityStore(identityAlias)

        try {
            val identity = identityStore.getOrCreateIdentity()
            val repository = AndroidEncryptedTrustedPeerRepository(context, encryptionAlias)
            val displayName = "Tablet-private-name-$suffix"
            val record = TrustedPeerRecord(
                identity.peerId,
                identity.publicKeyEncoded,
                displayName,
                System.currentTimeMillis(),
                PairingOffer.SUPPORTED_PROTOCOL_VERSION
            )

            repository.put(record)

            val encryptedBytes = file.readBytes()
            assertFalse(
                "Display name must not appear in the encrypted file",
                String(encryptedBytes, Charsets.ISO_8859_1).contains(displayName)
            )

            val reopened = AndroidEncryptedTrustedPeerRepository(context, encryptionAlias)
            assertEquals(displayName, reopened.find(identity.peerId)?.displayName)
            assertEquals(identity.peerId, reopened.all().single().peerId)
            assertTrue(reopened.remove(identity.peerId))
            assertNull(reopened.find(identity.peerId))
            assertFalse(reopened.remove(identity.peerId))
        } finally {
            file.delete()
            deleteKey(identityAlias)
            deleteKey(encryptionAlias)
        }
    }

    private fun deleteKey(alias: String) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(alias)
    }
}
