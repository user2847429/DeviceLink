package org.devicelink.core.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceCapabilitySnapshotCodecTest {
    @Test
    fun roundTripsSupportedUnsupportedAndUnknownStates() {
        val snapshot = DeviceCapabilitySnapshot(
            generatedAtEpochMillis = 1_800_000_000_000L,
            capabilities = listOf(
                DeviceCapability("Wi-Fi", "Wi-Fi Direct", true, "Declared by Android"),
                DeviceCapability("Bluetooth", "BLE", false, "Not declared"),
                DeviceCapability("Wi-Fi", "Concurrent STA + P2P", null, "Not exposed")
            )
        )

        val decoded = DeviceCapabilitySnapshotCodec.decode(
            DeviceCapabilitySnapshotCodec.encode(snapshot)
        )

        assertEquals(snapshot.generatedAtEpochMillis, decoded.generatedAtEpochMillis)
        assertEquals(snapshot.capabilities, decoded.capabilities)
        assertNull(decoded.capabilities.last().supported)
    }

    @Test
    fun rejectsTrailingBytesAndInvalidMagic() {
        val encoded = DeviceCapabilitySnapshotCodec.encode(
            DeviceCapabilitySnapshot(1_800_000_000_000L, emptyList())
        )
        assertThrows(IllegalArgumentException::class.java) {
            DeviceCapabilitySnapshotCodec.decode(encoded + byteArrayOf(1))
        }
        val badMagic = encoded.clone().also { it[0] = 0 }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceCapabilitySnapshotCodec.decode(badMagic)
        }
    }

    @Test
    fun rejectsOversizedCapabilityListsAndFields() {
        val tooMany = DeviceCapabilitySnapshot(
            1_800_000_000_000L,
            List(DeviceCapabilitySnapshotCodec.MAX_CAPABILITIES + 1) {
                DeviceCapability("Wi-Fi", "Feature", true, "")
            }
        )
        assertThrows(IllegalArgumentException::class.java) {
            DeviceCapabilitySnapshotCodec.encode(tooMany)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceCapabilitySnapshotCodec.encode(
                DeviceCapabilitySnapshot(
                    1_800_000_000_000L,
                    listOf(DeviceCapability("Wi-Fi", "x".repeat(200), true, ""))
                )
            )
        }
    }

    @Test
    fun rejectsUnknownSupportState() {
        val bytes = DeviceCapabilitySnapshotCodec.encode(
            DeviceCapabilitySnapshot(
                1_800_000_000_000L,
                listOf(DeviceCapability("Wi-Fi", "Wi-Fi Direct", true, ""))
            )
        )
        bytes[15] = 3
        assertThrows(IllegalArgumentException::class.java) {
            DeviceCapabilitySnapshotCodec.decode(bytes)
        }
    }
}
