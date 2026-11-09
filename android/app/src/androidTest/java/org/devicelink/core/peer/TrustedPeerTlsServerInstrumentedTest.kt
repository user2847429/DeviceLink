package org.devicelink.core.peer

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.devicelink.core.protocol.ControlMessage
import org.devicelink.core.protocol.MessageKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.security.KeyStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TrustedPeerTlsServerInstrumentedTest {
    @Test
    fun serverAcceptsOnlyIdentityPresentInTrustedRegistryAndExchangesControlMessage() {
        val suffix = System.nanoTime().toString()
        val clientAlias = "devicelink.test.trusted-client.$suffix"
        val serverAlias = "devicelink.test.trusted-server.$suffix"
        val clientIdentityStore = DeviceIdentityStore(clientAlias)
        val serverIdentityStore = DeviceIdentityStore(serverAlias)
        val records = linkedMapOf<String, TrustedPeerRecord>()
        val repository = object : TrustedPeerRepository {
            override fun find(peerId: String) = records[peerId]
            override fun all() = records.values.toList()
            override fun put(record: TrustedPeerRecord) { records[record.peerId] = record }
            override fun remove(peerId: String) = records.remove(peerId) != null
        }
        val registry = TrustedPeerRegistry(repository)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val clientIdentity = clientIdentityStore.getOrCreateIdentity()
            val serverIdentity = serverIdentityStore.getOrCreateIdentity()
            registry.run {
                repository.put(
                    TrustedPeerRecord(
                        clientIdentity.peerId,
                        clientIdentity.publicKeyEncoded,
                        "Trusted test client",
                        System.currentTimeMillis(),
                        PairingOffer.SUPPORTED_PROTOCOL_VERSION
                    )
                )
            }

            val clientFactory = AndroidTlsSocketFactory(
                expectedPeerId = serverIdentity.peerId,
                expectedPeerPublicKeyEncoded = serverIdentity.publicKeyEncoded,
                identityStore = clientIdentityStore
            )
            val serverFactory = AndroidTlsSocketFactory.forTrustedPeers(
                registry = registry,
                identityStore = serverIdentityStore
            )
            val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).apply {
                soTimeout = 15_000
            }

            try {
                val serverFuture = executor.submit<String> {
                    serverSocket.accept().use { accepted ->
                        AndroidTlsSession.acceptTrusted(serverFactory, accepted).use { session ->
                            AuthenticatedControlChannel.establish(
                                session,
                                serverIdentity.peerId
                            ).use { channel ->
                                val received = channel.receive()
                                    ?: error("Client closed before sending its control message.")
                                assertEquals(MessageKind.EVENT, received.kind)
                                assertEquals("trusted-ping", String(received.payload, Charsets.UTF_8))
                                channel.send(
                                    ControlMessage(
                                        version = ControlMessage.CURRENT_VERSION,
                                        messageId = "trusted-pong",
                                        senderPeerId = serverIdentity.peerId,
                                        kind = MessageKind.EVENT,
                                        payload = "trusted-pong".toByteArray(Charsets.UTF_8)
                                    )
                                )
                                session.peerId
                            }
                        }
                    }
                }

                AndroidTlsSession.connect(
                    clientFactory,
                    serverIdentity.peerId,
                    serverIdentity.publicKeyEncoded,
                    InetAddress.getLoopbackAddress().hostAddress ?: "127.0.0.1",
                    serverSocket.localPort
                ).use { session ->
                    AuthenticatedControlChannel.establish(
                        session,
                        clientIdentity.peerId
                    ).use { channel ->
                        channel.send(
                            ControlMessage(
                                version = ControlMessage.CURRENT_VERSION,
                                messageId = "trusted-ping",
                                senderPeerId = clientIdentity.peerId,
                                kind = MessageKind.EVENT,
                                payload = "trusted-ping".toByteArray(Charsets.UTF_8)
                            )
                        )
                        val reply = channel.receive()
                            ?: error("Server closed before replying.")
                        assertEquals("trusted-pong", String(reply.payload, Charsets.UTF_8))
                    }
                }

                assertEquals(clientIdentity.peerId, serverFuture.get(20, TimeUnit.SECONDS))
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
}
