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
class AuthenticatedControlChannelInstrumentedTest {
    @Test
    fun exchangesIdentityBoundHelloAndControlMessageOverMutualTls() {
        val suffix = System.nanoTime().toString()
        val clientAlias = "devicelink.channel.client.$suffix"
        val serverAlias = "devicelink.channel.server.$suffix"
        val clientStore = DeviceIdentityStore(clientAlias)
        val serverStore = DeviceIdentityStore(serverAlias)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val clientIdentity = clientStore.getOrCreateIdentity()
            val serverIdentity = serverStore.getOrCreateIdentity()
            val clientFactory = AndroidTlsSocketFactory(
                serverIdentity.peerId, serverIdentity.publicKeyEncoded, clientStore
            )
            val serverFactory = AndroidTlsSocketFactory(
                clientIdentity.peerId, clientIdentity.publicKeyEncoded, serverStore
            )
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
                listener.soTimeout = 15_000
                val serverFuture = executor.submit<String> {
                    listener.accept().use { accepted ->
                        val session = AndroidTlsSession.accept(
                            serverFactory,
                            clientIdentity.peerId,
                            clientIdentity.publicKeyEncoded,
                            accepted
                        )
                        AuthenticatedControlChannel.establish(
                            session, serverIdentity.peerId
                        ).use { channel ->
                            assertEquals(clientIdentity.peerId, channel.remotePeerId)
                            val incoming = channel.receive()
                            assertEquals(MessageKind.EVENT, incoming?.kind)
                            assertEquals("ping", incoming?.payload?.toString(Charsets.UTF_8))
                            channel.send(
                                ControlMessage(
                                    ControlMessage.CURRENT_PROTOCOL_VERSION,
                                    "server-event-1",
                                    serverIdentity.peerId,
                                    MessageKind.EVENT,
                                    "pong".toByteArray(Charsets.UTF_8)
                                )
                            )
                            channel.remotePeerId
                        }
                    }
                }

                val clientSession = AndroidTlsSession.connect(
                    clientFactory,
                    serverIdentity.peerId,
                    serverIdentity.publicKeyEncoded,
                    InetAddress.getLoopbackAddress().hostAddress ?: "127.0.0.1",
                    listener.localPort
                )
                AuthenticatedControlChannel.establish(
                    clientSession, clientIdentity.peerId
                ).use { channel ->
                    assertEquals(serverIdentity.peerId, channel.remotePeerId)
                    channel.send(
                        ControlMessage(
                            ControlMessage.CURRENT_PROTOCOL_VERSION,
                            "client-event-1",
                            clientIdentity.peerId,
                            MessageKind.EVENT,
                            "ping".toByteArray(Charsets.UTF_8)
                        )
                    )
                    val response = channel.receive()
                    assertEquals(MessageKind.EVENT, response?.kind)
                    assertEquals("pong", response?.payload?.toString(Charsets.UTF_8))
                }
                assertEquals(clientIdentity.peerId, serverFuture.get(20, TimeUnit.SECONDS))
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
