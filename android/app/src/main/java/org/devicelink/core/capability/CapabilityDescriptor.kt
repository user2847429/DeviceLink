package org.devicelink.core.capability

/**
 * Stable, platform-neutral description of a feature a peer can provide.
 *
 * Metadata only: registration does not make a capability remotely callable.
 * Invocation requires an authenticated session, authorization, and a defined
 * wire format, none of which are provided by this scaffold.
 */
data class CapabilityDescriptor(
    val id: String,
    val version: Int,
    val operations: List<CapabilityOperation>,
    val requiredGrants: Set<String> = emptySet()
) {
    init {
        require(ID_PATTERN.matches(id)) { "Invalid capability id: $id" }
        require(version > 0) { "Capability version must be positive" }
        require(operations.map { it.id }.distinct().size == operations.size) {
            "Operation ids must be unique within a capability"
        }
    }

    companion object {
        private val ID_PATTERN = Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+")
    }
}

/** A named, bounded operation; never an arbitrary shell command. */
data class CapabilityOperation(
    val id: String,
    val requiresUserApproval: Boolean = false
) {
    init {
        require(OPERATION_PATTERN.matches(id)) { "Invalid operation id: $id" }
    }

    companion object {
        private val OPERATION_PATTERN = Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*")
    }
}
