package org.devicelink.core.capability

/**
 * In-process catalogue of capabilities available on this installation.
 * This registry has no network or Android framework dependency.
 */
class CapabilityRegistry {
    private val entries = linkedMapOf<String, CapabilityDescriptor>()

    @Synchronized
    fun register(descriptor: CapabilityDescriptor) {
        require(!entries.containsKey(descriptor.id)) {
            "Capability already registered: ${descriptor.id}"
        }
        // Keep a defensive snapshot so later caller-side collection changes
        // cannot silently alter the registered capability contract.
        entries[descriptor.id] = descriptor.copy(
            operations = descriptor.operations.toList(),
            requiredGrants = descriptor.requiredGrants.toSet()
        )
    }

    @Synchronized
    fun find(id: String): CapabilityDescriptor? = entries[id]

    @Synchronized
    fun all(): List<CapabilityDescriptor> = entries.values.toList()

    @Synchronized
    fun contains(id: String): Boolean = entries.containsKey(id)

    /** True only for an operation explicitly declared by this local capability. */
    @Synchronized
    fun supports(capabilityId: String, operationId: String): Boolean =
        entries[capabilityId]?.operations?.any { it.id == operationId } == true
}
