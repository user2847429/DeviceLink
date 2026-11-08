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
        entries[descriptor.id] = descriptor
    }

    @Synchronized
    fun find(id: String): CapabilityDescriptor? = entries[id]

    @Synchronized
    fun all(): List<CapabilityDescriptor> = entries.values.toList()

    @Synchronized
    fun contains(id: String): Boolean = entries.containsKey(id)
}
