package org.devicelink.core.peer

/**
 * Local lifecycle of the Android peer component.
 *
 * READY means the component initialized successfully. It does not mean that
 * discovery is active, a peer is connected, or a secure session exists.
 */
enum class PeerServiceState {
    STOPPED,
    STARTING,
    READY,
    STOPPING,
    FAILED
}

enum class PeerLifecycleEvent {
    START_REQUESTED,
    START_SUCCEEDED,
    START_FAILED,
    STOP_REQUESTED,
    STOP_SUCCEEDED,
    RETRY_REQUESTED
}

data class PeerTransition(
    val state: PeerServiceState,
    val accepted: Boolean
)

/**
 * Pure state transition logic, kept independent from Android APIs so every
 * lifecycle rule can be tested on the JVM.
 */
object PeerLifecycle {
    fun transition(
        current: PeerServiceState,
        event: PeerLifecycleEvent
    ): PeerTransition {
        val next = when (current to event) {
            PeerServiceState.STOPPED to PeerLifecycleEvent.START_REQUESTED ->
                PeerServiceState.STARTING

            PeerServiceState.STARTING to PeerLifecycleEvent.START_SUCCEEDED ->
                PeerServiceState.READY

            PeerServiceState.STARTING to PeerLifecycleEvent.START_FAILED ->
                PeerServiceState.FAILED

            PeerServiceState.STARTING to PeerLifecycleEvent.STOP_REQUESTED,
            PeerServiceState.READY to PeerLifecycleEvent.STOP_REQUESTED ->
                PeerServiceState.STOPPING

            PeerServiceState.STOPPING to PeerLifecycleEvent.STOP_SUCCEEDED,
            PeerServiceState.FAILED to PeerLifecycleEvent.STOP_REQUESTED ->
                PeerServiceState.STOPPED

            PeerServiceState.FAILED to PeerLifecycleEvent.RETRY_REQUESTED ->
                PeerServiceState.STARTING

            else -> return PeerTransition(current, accepted = false)
        }

        return PeerTransition(next, accepted = true)
    }
}
