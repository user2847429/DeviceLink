package org.devicelink.core.peer

import org.junit.Assert.*
import org.junit.Test

class SecureSessionLifecycleTest {
    @Test fun trustedPeerNeedsTls13MutualAuthenticationBeforeReady() {
        val tls = SecureSessionLifecycle.transition(SecureSessionState.TCP_CONNECTED, SecureSessionEvent.START_TLS)
        assertEquals(SecureSessionState.TLS_HANDSHAKING, tls.state)
        val authenticated = SecureSessionLifecycle.transition(tls.state, SecureSessionEvent.TLS_13_MUTUAL_AUTHENTICATED)
        assertEquals(SecureSessionState.TLS_AUTHENTICATED, authenticated.state)
        val ready = SecureSessionLifecycle.transition(authenticated.state, SecureSessionEvent.TRUSTED_IDENTITY_MATCHED)
        assertTrue(ready.accepted)
        assertEquals(SecureSessionState.READY, ready.state)
    }

    @Test fun unknownPeerRequiresBothUsersConfirmation() {
        val pairing = SecureSessionLifecycle.transition(SecureSessionState.TLS_AUTHENTICATED, SecureSessionEvent.UNKNOWN_IDENTITY_REQUIRES_PAIRING)
        assertEquals(SecureSessionState.PAIRING_PENDING, pairing.state)
        val ready = SecureSessionLifecycle.transition(pairing.state, SecureSessionEvent.BOTH_USERS_CONFIRMED)
        assertTrue(ready.accepted)
        assertEquals(SecureSessionState.READY, ready.state)
    }

    @Test fun cannotSkipTlsOrTrustGates() {
        val beforeTls = SecureSessionLifecycle.transition(SecureSessionState.TCP_CONNECTED, SecureSessionEvent.TRUSTED_IDENTITY_MATCHED)
        assertFalse(beforeTls.accepted)
        val duringTls = SecureSessionLifecycle.transition(SecureSessionState.TLS_HANDSHAKING, SecureSessionEvent.TRUSTED_IDENTITY_MATCHED)
        assertFalse(duringTls.accepted)
        val beforeConfirmation = SecureSessionLifecycle.transition(SecureSessionState.PAIRING_PENDING, SecureSessionEvent.TRUSTED_IDENTITY_MATCHED)
        assertFalse(beforeConfirmation.accepted)
    }

    @Test fun rejectionAndTlsFailureAreTerminal() {
        val rejected = SecureSessionLifecycle.transition(SecureSessionState.PAIRING_PENDING, SecureSessionEvent.PAIRING_REJECTED)
        assertEquals(SecureSessionState.FAILED, rejected.state)
        assertFalse(SecureSessionLifecycle.transition(rejected.state, SecureSessionEvent.BOTH_USERS_CONFIRMED).accepted)
        val failed = SecureSessionLifecycle.transition(SecureSessionState.TLS_HANDSHAKING, SecureSessionEvent.TLS_FAILED)
        assertEquals(SecureSessionState.FAILED, failed.state)
    }

    @Test fun readySessionCannotBeReauthenticated() {
        val result = SecureSessionLifecycle.transition(SecureSessionState.READY, SecureSessionEvent.TLS_13_MUTUAL_AUTHENTICATED)
        assertFalse(result.accepted)
        assertEquals(SecureSessionState.READY, result.state)
    }
}
