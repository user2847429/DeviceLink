package org.devicelink.core.device

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Versioned, bounded capability inventory exchanged only inside an authenticated
 * DeviceLink control channel. This codec does not establish trust by itself.
 */
data class DeviceCapabilitySnapshot(
    val generatedAtEpochMillis: Long,
    val capabilities: List<DeviceCapability>
)

object DeviceCapabilitySnapshotCodec {
    private val MAGIC = byteArrayOf(0x44, 0x4c, 0x43, 0x41) // DLCA
    private const val VERSION = 1
    const val MAX_CAPABILITIES = 64
    const val MAX_SNAPSHOT_BYTES = 16 * 1024
    private const val MAX_CATEGORY_BYTES = 64
    private const val MAX_NAME_BYTES = 96
    private const val MAX_DETAIL_BYTES = 512

    fun encode(snapshot: DeviceCapabilitySnapshot): ByteArray {
        require(snapshot.generatedAtEpochMillis > 0) { "Invalid snapshot timestamp." }
        require(snapshot.capabilities.size <= MAX_CAPABILITIES) { "Too many capabilities." }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(MAGIC)
            data.writeByte(VERSION)
            data.writeLong(snapshot.generatedAtEpochMillis)
            data.writeShort(snapshot.capabilities.size)
            snapshot.capabilities.forEach { capability ->
                val category = boundedUtf8(capability.category, MAX_CATEGORY_BYTES)
                val name = boundedUtf8(capability.name, MAX_NAME_BYTES)
                val detail = boundedUtf8(capability.detail, MAX_DETAIL_BYTES)
                require(category.isNotEmpty() && name.isNotEmpty()) {
                    "Capability category and name must not be blank."
                }
                data.writeByte(when (capability.supported) {
                    true -> 1
                    false -> 2
                    null -> 0
                })
                data.writeShort(category.size)
                data.writeShort(name.size)
                data.writeShort(detail.size)
                data.write(category)
                data.write(name)
                data.write(detail)
            }
        }
        val bytes = output.toByteArray()
        require(bytes.size <= MAX_SNAPSHOT_BYTES) { "Capability snapshot is too large." }
        return bytes
    }

    fun decode(bytes: ByteArray): DeviceCapabilitySnapshot {
        require(bytes.size in HEADER_BYTES..MAX_SNAPSHOT_BYTES) {
            "Capability snapshot length is invalid."
        }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val magic = ByteArray(MAGIC.size)
        input.readFully(magic)
        require(magic.contentEquals(MAGIC)) { "Capability snapshot magic is invalid." }
        require(input.readUnsignedByte() == VERSION) { "Capability snapshot version is unsupported." }
        val timestamp = input.readLong()
        require(timestamp > 0) { "Capability snapshot timestamp is invalid." }
        val count = input.readUnsignedShort()
        require(count <= MAX_CAPABILITIES) { "Too many capabilities." }

        val result = ArrayList<DeviceCapability>(count)
        repeat(count) {
            val supported = when (input.readUnsignedByte()) {
                0 -> null
                1 -> true
                2 -> false
                else -> throw IllegalArgumentException("Unknown capability support state.")
            }
            val categoryLength = input.readUnsignedShort()
            val nameLength = input.readUnsignedShort()
            val detailLength = input.readUnsignedShort()
            require(categoryLength in 1..MAX_CATEGORY_BYTES &&
                nameLength in 1..MAX_NAME_BYTES &&
                detailLength <= MAX_DETAIL_BYTES) {
                "Capability field length is invalid."
            }
            val category = readUtf8(input, categoryLength)
            val name = readUtf8(input, nameLength)
            val detail = readUtf8(input, detailLength)
            result += DeviceCapability(category, name, supported, detail)
        }
        require(input.available() == 0) { "Capability snapshot has trailing data." }
        return DeviceCapabilitySnapshot(timestamp, result.toList())
    }

    private fun boundedUtf8(value: String, maxBytes: Int): ByteArray {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= maxBytes) { "Capability text field is too long." }
        return bytes
    }

    private fun readUtf8(input: DataInputStream, length: Int): String {
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }

    private const val HEADER_BYTES = 4 + 1 + 8 + 2
}
