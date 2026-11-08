package org.devicelink.core.capability

/**
 * A capability is one small, independently versioned feature a peer can offer.
 *
 * This contract intentionally contains no Android types, UI objects, shell
 * commands, or executable payloads. Platform adapters and command handlers
 * will be added behind this boundary in later steps.
 */
data class CapabilityDescriptor(
    val id: String,
    val version: Int,
    val operations: Set<String>,
    val requiredPermissions: Set<String> = emptySet(),
) {
    fun validate(): CapabilityValidation = when {
        !isValidIdentifier(id) -> CapabilityValidation.InvalidCapabilityId
        version < 1 -> CapabilityValidation.InvalidVersion
        operations.isEmpty() -> CapabilityValidation.NoOperations
        operations.any { !isValidIdentifier(it) } -> CapabilityValidation.InvalidOperation
        requiredPermissions.any { !isValidIdentifier(it) } ->
            CapabilityValidation.InvalidPermission
        else -> CapabilityValidation.Valid
    }

    companion object {
        private val IDENTIFIER = Regex("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*")

        private fun isValidIdentifier(value: String): Boolean =
            value.length in 1..96 && IDENTIFIER.matches(value)
    }
}

/** Registry for locally implemented capabilities; it does not execute commands. */
class CapabilityRegistry {
    private val descriptors = linkedMapOf<String, CapabilityDescriptor>()

    @Synchronized
    fun register(descriptor: CapabilityDescriptor): CapabilityRegistration {
        when (descriptor.validate()) {
            CapabilityValidation.Valid -> Unit
            else -> return CapabilityRegistration.InvalidDescriptor(descriptor.validate())
        }

        if (descriptors.containsKey(descriptor.id)) {
            return CapabilityRegistration.DuplicateId(descriptor.id)
        }

        descriptors[descriptor.id] = descriptor.copy(
            operations = descriptor.operations.toSet(),
            requiredPermissions = descriptor.requiredPermissions.toSet(),
        )
        return CapabilityRegistration.Registered
    }

    @Synchronized
    fun find(capabilityId: String): CapabilityDescriptor? = descriptors[capabilityId]

    @Synchronized
    fun all(): List<CapabilityDescriptor> = descriptors.values.toList()

    @Synchronized
    fun supports(capabilityId: String, operation: String): Boolean =
        descriptors[capabilityId]?.operations?.contains(operation) == true
}

enum class CapabilityValidation {
    Valid,
    InvalidCapabilityId,
    InvalidVersion,
    NoOperations,
    InvalidOperation,
    InvalidPermission,
}

sealed class CapabilityRegistration {
    data object Registered : CapabilityRegistration()
    data class DuplicateId(val id: String) : CapabilityRegistration()
    data class InvalidDescriptor(val reason: CapabilityValidation) : CapabilityRegistration()
}
