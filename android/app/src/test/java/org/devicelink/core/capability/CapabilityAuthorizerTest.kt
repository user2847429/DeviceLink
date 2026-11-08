package org.devicelink.core.capability

import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityAuthorizerTest {
    private val capability = CapabilityDescriptor(
        id = "device.media",
        version = 2,
        operations = listOf(
            CapabilityOperation("play"),
            CapabilityOperation("set-volume", requiresUserApproval = true)
        ),
        requiredGrants = setOf("media.session")
    )
    private val trustedPeer = AuthenticatedPeerContext(
        peerId = "dl1:test-peer",
        isAuthenticated = true,
        isTrusted = true
    )
    private fun request(operation: String = "play") = CapabilityRequest(
        requestId = "req-001",
        capabilityId = "device.media",
        capabilityVersion = 2,
        operationId = operation,
        payload = byteArrayOf(1, 2, 3)
    )

    @Test fun allowsOnlyTrustedAuthenticatedAndGrantedPeer() {
        assertEquals(
            CapabilityAuthorizationResult.Allowed,
            CapabilityAuthorizer.authorize(
                request(), trustedPeer, capability, setOf("media.session"), false
            )
        )
    }

    @Test fun rejectsUnauthenticatedAndUntrustedPeers() {
        assertEquals(
            CapabilityAuthorizationResult.UnauthenticatedPeer,
            CapabilityAuthorizer.authorize(
                request(), trustedPeer.copy(isAuthenticated = false),
                capability, setOf("media.session"), false
            )
        )
        assertEquals(
            CapabilityAuthorizationResult.UntrustedPeer,
            CapabilityAuthorizer.authorize(
                request(), trustedPeer.copy(isTrusted = false),
                capability, setOf("media.session"), false
            )
        )
    }

    @Test fun rejectsWrongCapabilityVersionAndUnknownOperation() {
        assertEquals(
            CapabilityAuthorizationResult.UnsupportedCapabilityVersion,
            CapabilityAuthorizer.authorize(
                request().copy(capabilityVersion = 1), trustedPeer,
                capability, setOf("media.session"), false
            )
        )
        assertEquals(
            CapabilityAuthorizationResult.UnsupportedOperation,
            CapabilityAuthorizer.authorize(
                request("shell"), trustedPeer, capability, setOf("media.session"), false
            )
        )
    }

    @Test fun requiresGrantAndExplicitApprovalForSensitiveOperation() {
        assertEquals(
            CapabilityAuthorizationResult.MissingGrant,
            CapabilityAuthorizer.authorize(
                request(), trustedPeer, capability, emptySet(), false
            )
        )
        assertEquals(
            CapabilityAuthorizationResult.UserApprovalRequired,
            CapabilityAuthorizer.authorize(
                request("set-volume"), trustedPeer, capability, setOf("media.session"), false
            )
        )
        assertEquals(
            CapabilityAuthorizationResult.Allowed,
            CapabilityAuthorizer.authorize(
                request("set-volume"), trustedPeer, capability, setOf("media.session"), true
            )
        )
    }

    @Test fun rejectsOversizedPayloadBeforeAuthorization() {
        val oversized = request().copy(payload = ByteArray(CapabilityRequest.MAX_PAYLOAD_BYTES + 1))
        assertEquals(
            CapabilityAuthorizationResult.InvalidRequest,
            CapabilityAuthorizer.authorize(
                oversized, trustedPeer, capability, setOf("media.session"), false
            )
        )
    }
}
