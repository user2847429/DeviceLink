package org.devicelink.core.capability

/**
 * Stable, platform-neutral description of a feature a peer can provide.
 *
 * This is metadata only: registering a capability does not make it remotely
 * callable. Invocation will be added only after the authenticated session,
 * authorization checks, and wire format are implemented.
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

/**
 * In-process catalogue of capabilities available on this installation.
 * The registry deliberately has no network or Android framework dependency.
 */
class CapabilityRegistry {
    private val entries = linkedMapOf<String, CapabilityDescriptor>()

    @Synchronized
    fun register(descriptor: CapabilityDescriptor) {
        require(!entries.containsKey(descriptor.id)) {
            "Capability already registered: ${descriptor.id}"
        }
        entries[descriptor.id] = descriptor
    }

    @Synchronized
    fun find(id: String): CapabilityDescriptor? = entries[id]

    @Synchronized
    fun all(): List<CapabilityDescriptor> = entries.values.toList()

    @Synchronized
    fun contains(id: String): Boolean = entries.containsKey(id)
}
