package org.devicelink.core.peer

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.security.KeyStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AndroidTlsSessionInstrumentedTest {
    @Test
    fun mutualTls13AndExporterWorkWithSeparateAndroidKeystoreIdentities() {
        val suffix = System.nanoTime().toString()
        val clientAlias = "devicelink.test.client.$suffix"
        val serverAlias = "devicelink.test.server.$suffix"
        val clientIdentityStore = DeviceIdentityStore(clientAlias)
        val serverIdentityStore = DeviceIdentityStore(serverAlias)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val clientIdentity = clientIdentityStore.getOrCreateIdentity()
            val serverIdentity = serverIdentityStore.getOrCreateIdentity()
            val clientFactory = AndroidTlsSocketFactory(
                expectedPeerId = serverIdentity.peerId,
                expectedPeerPublicKeyEncoded = serverIdentity.publicKeyEncoded,
                identityStore = clientIdentityStore
            )
            val serverFactory = AndroidTlsSocketFactory(
                expectedPeerId = clientIdentity.peerId,
                expectedPeerPublicKeyEncoded = clientIdentity.publicKeyEncoded,
                identityStore = serverIdentityStore
            )
            val serverSocket = ServerSocket(
                0, 1, InetAddress.getLoopbackAddress()
            )
            serverSocket.soTimeout = 15_000

            try {
                val serverFuture = executor.submit<ByteArray> {
                    serverSocket.accept().use { accepted ->
                        AndroidTlsSession.accept(
                            serverFactory,
                            clientIdentity.peerId,
                            clientIdentity.publicKeyEncoded,
                            accepted
                        ).use { session ->
                            assertEquals("TLSv1.3", session.socket.session.protocol)
                            session.exportPairingMaterial(TEST_CONTEXT)
                        }
                    }
                }

                val clientMaterial = AndroidTlsSession.connect(
                    clientFactory,
                    serverIdentity.peerId,
                    serverIdentity.publicKeyEncoded,
                    InetAddress.getLoopbackAddress().hostAddress ?: "127.0.0.1",
                    serverSocket.localPort
                ).use { session ->
                    assertEquals("TLSv1.3", session.socket.session.protocol)
                    session.exportPairingMaterial(TEST_CONTEXT)
                }
                val serverMaterial = serverFuture.get(20, TimeUnit.SECONDS)
                assertArrayEquals(serverMaterial, clientMaterial)
                assertEquals(32, clientMaterial.size)
            } finally {
                serverSocket.close()
            }
        } finally {
            executor.shutdownNow()
            deleteKey(clientAlias)
            deleteKey(serverAlias)
        }
    }

    private fun deleteKey(alias: String) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(alias)
    }

    companion object {
        private val TEST_CONTEXT = byteArrayOf(
            0x44, 0x4c, 0x50, 0x31, 0x01, 0x02, 0x03, 0x04
        )
    }
}
