package org.devicelink.core.capability

/**
 * A typed request for one declared capability operation.
 *
 * The payload is opaque at this layer; each operation must later define and
 * validate its own schema before invoking an Android adapter.
 */
data class CapabilityRequest(
    val requestId: String,
    val capabilityId: String,
    val capabilityVersion: Int,
    val operationId: String,
    val payload: ByteArray
) {
    fun validate(): CapabilityRequestValidation = when {
        requestId.isBlank() || requestId.toByteArray(Charsets.UTF_8).size > MAX_REQUEST_ID_BYTES ->
            CapabilityRequestValidation.InvalidRequestId
        capabilityId.isBlank() || capabilityId.toByteArray(Charsets.UTF_8).size > MAX_IDENTIFIER_BYTES ->
            CapabilityRequestValidation.InvalidCapabilityId
        capabilityVersion < 1 -> CapabilityRequestValidation.InvalidCapabilityVersion
        operationId.isBlank() || operationId.toByteArray(Charsets.UTF_8).size > MAX_IDENTIFIER_BYTES ->
            CapabilityRequestValidation.InvalidOperationId
        payload.size > MAX_PAYLOAD_BYTES -> CapabilityRequestValidation.PayloadTooLarge
        else -> CapabilityRequestValidation.Valid
    }

    companion object {
        const val MAX_REQUEST_ID_BYTES = 128
        const val MAX_IDENTIFIER_BYTES = 96
        const val MAX_PAYLOAD_BYTES = 64 * 1024
    }
}

/**
 * Security facts supplied by the authenticated session layer, never copied
 * from a remote request. This is a policy input, not a session implementation.
 */
data class AuthenticatedPeerContext(
    val peerId: String,
    val isAuthenticated: Boolean,
    val isTrusted: Boolean
)

enum class CapabilityRequestValidation {
    Valid,
    InvalidRequestId,
    InvalidCapabilityId,
    InvalidCapabilityVersion,
    InvalidOperationId,
    PayloadTooLarge
}

enum class CapabilityAuthorizationResult {
    Allowed,
    InvalidRequest,
    UnauthenticatedPeer,
    UntrustedPeer,
    CapabilityMismatch,
    UnsupportedCapabilityVersion,
    UnsupportedOperation,
    MissingGrant,
    UserApprovalRequired
}

/**
 * Pure authorization check. It deliberately does not execute the operation,
 * ask Android for permissions, or treat a remote capability advertisement
 * as trusted.
 */
object CapabilityAuthorizer {
    fun authorize(
        request: CapabilityRequest,
        peer: AuthenticatedPeerContext,
        localCapability: CapabilityDescriptor,
        grantedGrants: Set<String>,
        userApproved: Boolean
    ): CapabilityAuthorizationResult {
        if (request.validate() != CapabilityRequestValidation.Valid) {
            return CapabilityAuthorizationResult.InvalidRequest
        }
        if (!peer.isAuthenticated) return CapabilityAuthorizationResult.UnauthenticatedPeer
        if (!peer.isTrusted) return CapabilityAuthorizationResult.UntrustedPeer
        if (request.capabilityId != localCapability.id) {
            return CapabilityAuthorizationResult.CapabilityMismatch
        }
        if (request.capabilityVersion != localCapability.version) {
            return CapabilityAuthorizationResult.UnsupportedCapabilityVersion
        }

        val operation = localCapability.operations.firstOrNull { it.id == request.operationId }
            ?: return CapabilityAuthorizationResult.UnsupportedOperation

        if (!grantedGrants.containsAll(localCapability.requiredGrants)) {
            return CapabilityAuthorizationResult.MissingGrant
        }
        if (operation.requiresUserApproval && !userApproved) {
            return CapabilityAuthorizationResult.UserApprovalRequired
        }
        return CapabilityAuthorizationResult.Allowed
    }
}
