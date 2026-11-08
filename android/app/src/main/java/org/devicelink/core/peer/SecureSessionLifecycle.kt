package org.devicelink.core.peer

/** Pure security-gated lifecycle; it performs no TLS or socket operations. */
enum class SecureSessionState {
    TCP_CONNECTED, TLS_HANDSHAKING, TLS_AUTHENTICATED, PAIRING_PENDING, READY, CLOSED, FAILED
}

enum class SecureSessionEvent {
    START_TLS,
    TLS_13_MUTUAL_AUTHENTICATED,
    TLS_FAILED,
    TRUSTED_IDENTITY_MATCHED,
    UNKNOWN_IDENTITY_REQUIRES_PAIRING,
    BOTH_USERS_CONFIRMED,
    PAIRING_REJECTED,
    CLOSE,
    IO_FAILED
}

data class SecureSessionTransition(val state: SecureSessionState, val accepted: Boolean)

/**
 * Application readiness is impossible before verified TLS 1.3 mutual
 * authentication and either an existing trust match or two-sided pairing.
 *
 * The TLS_13_MUTUAL_AUTHENTICATED event is a contract for a future transport
 * adapter. It must only be emitted after the handshake, both certificate
 * checks, and DeviceLink public-key identity binding have succeeded.
 */
object SecureSessionLifecycle {
    fun transition(state: SecureSessionState, event: SecureSessionEvent): SecureSessionTransition {
        val next = when (state) {
            SecureSessionState.TCP_CONNECTED -> when (event) {
                SecureSessionEvent.START_TLS -> SecureSessionState.TLS_HANDSHAKING
                SecureSessionEvent.CLOSE -> SecureSessionState.CLOSED
                SecureSessionEvent.IO_FAILED -> SecureSessionState.FAILED
                else -> return rejected(state)
            }
            SecureSessionState.TLS_HANDSHAKING -> when (event) {
                SecureSessionEvent.TLS_13_MUTUAL_AUTHENTICATED -> SecureSessionState.TLS_AUTHENTICATED
                SecureSessionEvent.TLS_FAILED, SecureSessionEvent.IO_FAILED -> SecureSessionState.FAILED
                SecureSessionEvent.CLOSE -> SecureSessionState.CLOSED
                else -> return rejected(state)
            }
            SecureSessionState.TLS_AUTHENTICATED -> when (event) {
                SecureSessionEvent.TRUSTED_IDENTITY_MATCHED -> SecureSessionState.READY
                SecureSessionEvent.UNKNOWN_IDENTITY_REQUIRES_PAIRING -> SecureSessionState.PAIRING_PENDING
                SecureSessionEvent.CLOSE -> SecureSessionState.CLOSED
                SecureSessionEvent.IO_FAILED -> SecureSessionState.FAILED
                else -> return rejected(state)
            }
            SecureSessionState.PAIRING_PENDING -> when (event) {
                SecureSessionEvent.BOTH_USERS_CONFIRMED -> SecureSessionState.READY
                SecureSessionEvent.PAIRING_REJECTED, SecureSessionEvent.IO_FAILED -> SecureSessionState.FAILED
                SecureSessionEvent.CLOSE -> SecureSessionState.CLOSED
                else -> return rejected(state)
            }
            SecureSessionState.READY -> when (event) {
                SecureSessionEvent.CLOSE -> SecureSessionState.CLOSED
                SecureSessionEvent.IO_FAILED -> SecureSessionState.FAILED
                else -> return rejected(state)
            }
            SecureSessionState.CLOSED, SecureSessionState.FAILED -> return rejected(state)
        }
        return SecureSessionTransition(next, accepted = true)
    }

    private fun rejected(state: SecureSessionState) = SecureSessionTransition(state, accepted = false)
}
