package org.devicelink.core.peer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerLifecycleTest {
    @Test
    fun startMovesThroughStartingToReady() {
        val starting = PeerLifecycle.transition(
            PeerServiceState.STOPPED,
            PeerLifecycleEvent.START_REQUESTED
        )
        assertTrue(starting.accepted)
        assertEquals(PeerServiceState.STARTING, starting.state)

        val ready = PeerLifecycle.transition(
            starting.state,
            PeerLifecycleEvent.START_SUCCEEDED
        )
        assertTrue(ready.accepted)
        assertEquals(PeerServiceState.READY, ready.state)
    }

    @Test
    fun failedStartCanBeRetried() {
        val failed = PeerLifecycle.transition(
            PeerServiceState.STARTING,
            PeerLifecycleEvent.START_FAILED
        )
        assertEquals(PeerServiceState.FAILED, failed.state)

        val retry = PeerLifecycle.transition(
            failed.state,
            PeerLifecycleEvent.RETRY_REQUESTED
        )
        assertTrue(retry.accepted)
        assertEquals(PeerServiceState.STARTING, retry.state)
    }

    @Test
    fun stopCompletesFromReady() {
        val stopping = PeerLifecycle.transition(
            PeerServiceState.READY,
            PeerLifecycleEvent.STOP_REQUESTED
        )
        assertEquals(PeerServiceState.STOPPING, stopping.state)

        val stopped = PeerLifecycle.transition(
            stopping.state,
            PeerLifecycleEvent.STOP_SUCCEEDED
        )
        assertTrue(stopped.accepted)
        assertEquals(PeerServiceState.STOPPED, stopped.state)
    }

    @Test
    fun staleOrInvalidEventsDoNotChangeState() {
        val result = PeerLifecycle.transition(
            PeerServiceState.STOPPED,
            PeerLifecycleEvent.START_SUCCEEDED
        )
        assertFalse(result.accepted)
        assertEquals(PeerServiceState.STOPPED, result.state)
    }

    @Test
    fun stoppingWhileStartIsPendingIsAllowed() {
        val result = PeerLifecycle.transition(
            PeerServiceState.STARTING,
            PeerLifecycleEvent.STOP_REQUESTED
        )
        assertTrue(result.accepted)
        assertEquals(PeerServiceState.STOPPING, result.state)
    }

    @Test
    fun failedServiceCanBeStopped() {
        val result = PeerLifecycle.transition(
            PeerServiceState.FAILED,
            PeerLifecycleEvent.STOP_REQUESTED
        )
        assertTrue(result.accepted)
        assertEquals(PeerServiceState.STOPPED, result.state)
    }
}
