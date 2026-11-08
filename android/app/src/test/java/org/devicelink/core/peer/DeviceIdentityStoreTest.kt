package org.devicelink.core.peer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeviceIdentityStoreTest {
    @Test
    fun peerIdIsStableForSamePublicKeyBytes() {
        val key = byteArrayOf(1, 2, 3, 4, 5)
        assertEquals(DeviceIdentityStore.peerIdFor(key), DeviceIdentityStore.peerIdFor(key.clone()))
    }

    @Test
    fun peerIdChangesWhenPublicKeyChanges() {
        assertNotEquals(
            DeviceIdentityStore.peerIdFor(byteArrayOf(1, 2, 3)),
            DeviceIdentityStore.peerIdFor(byteArrayOf(1, 2, 4))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun peerIdRejectsEmptyPublicKey() {
        DeviceIdentityStore.peerIdFor(byteArrayOf())
    }
}
