package org.devicelink.core.capability

import org.junit.Assert.*
import org.junit.Test

class CapabilityRegistryTest {
    @Test fun registersAndFindsCapability() {
        val registry = CapabilityRegistry()
        val descriptor = CapabilityDescriptor(
            id = "device.apps",
            version = 1,
            operations = listOf(CapabilityOperation("list"), CapabilityOperation("launch", true)),
            requiredGrants = setOf("android.launcher.query")
        )
        registry.register(descriptor)
        assertEquals(descriptor, registry.find("device.apps"))
        assertEquals(listOf(descriptor), registry.all())
    }

    @Test fun rejectsDuplicateCapabilityIds() {
        val registry = CapabilityRegistry()
        val descriptor = CapabilityDescriptor("device.media", 1, emptyList())
        registry.register(descriptor)
        try {
            registry.register(descriptor)
            fail("Expected duplicate registration to fail")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsInvalidIdsAndDuplicateOperations() {
        try {
            CapabilityDescriptor("Apps", 1, emptyList())
            fail("Expected invalid capability id to fail")
        } catch (_: IllegalArgumentException) { }

        try {
            CapabilityDescriptor(
                "device.files", 1,
                listOf(CapabilityOperation("list"), CapabilityOperation("list"))
            )
            fail("Expected duplicate operation ids to fail")
        } catch (_: IllegalArgumentException) { }
    }
}
