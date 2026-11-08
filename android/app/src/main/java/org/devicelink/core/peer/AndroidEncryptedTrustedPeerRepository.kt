package org.devicelink.core.peer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * App-private trusted-peer storage. The file contains only an authenticated AES-GCM
 * envelope; plaintext records exist only in memory while an operation is running.
 *
 * A missing Keystore key alongside an existing data file is treated as data loss,
 * never as an empty registry. The caller must surface the error and require explicit
 * recovery rather than silently replacing the trust database.
 */
class AndroidEncryptedTrustedPeerRepository(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : TrustedPeerRepository {
    private val dataFile = File(context.applicationContext.filesDir, FILE_NAME)
    private val atomicFile = AtomicFile(dataFile)

    @Synchronized
    override fun find(peerId: String): TrustedPeerRecord? =
        readRecords()[peerId]?.let(::copyRecord)

    @Synchronized
    override fun all(): List<TrustedPeerRecord> =
        readRecords().values.map(::copyRecord)

    @Synchronized
    override fun put(record: TrustedPeerRecord) {
        validateRecord(record)
        val records = readRecords()
        val existing = records[record.peerId]
        require(existing == null || existing.hasSamePublicKey(record.publicKeyEncoded)) {
            "A trusted peer ID cannot be replaced with a different public key."
        }
        require(existing != null || records.size < MAX_RECORDS) {
            "Trusted peer limit reached."
        }
        records[record.peerId] = copyRecord(record)
        writeRecords(records)
    }

    @Synchronized
    override fun remove(peerId: String): Boolean {
        val records = readRecords()
        if (records.remove(peerId) == null) return false
        writeRecords(records)
        return true
    }

    private fun readRecords(): LinkedHashMap<String, TrustedPeerRecord> {
        val envelope = try {
            atomicFile.openRead().use { it.readBounded(MAX_ENVELOPE_BYTES) }
        } catch (_: java.io.FileNotFoundException) {
            return linkedMapOf()
        }
        val plaintext = decrypt(envelope)
        return decodeRecords(plaintext)
    }

    private fun writeRecords(records: LinkedHashMap<String, TrustedPeerRecord>) {
        val plaintext = encodeRecords(records)
        val encrypted = encrypt(plaintext)
        val stream = atomicFile.startWrite()
        try {
            stream.write(encrypted)
            stream.flush()
            atomicFile.finishWrite(stream)
        } catch (failure: Exception) {
            atomicFile.failWrite(stream)
            throw failure
        }
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_BYTES).also(java.security.SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val header = envelopeHeader(iv.size, plaintext.size + GCM_TAG_BYTES)
        cipher.updateAAD(header)
        val ciphertext = cipher.doFinal(plaintext)
        return header + iv + ciphertext
    }

    private fun decrypt(envelope: ByteArray): ByteArray {
        require(envelope.size in MIN_ENVELOPE_BYTES..MAX_ENVELOPE_BYTES) {
            "Trusted-peer database has an invalid size."
        }
        val input = ByteBuffer.wrap(envelope)
        val magic = ByteArray(MAGIC.size).also(input::get)
        require(magic.contentEquals(MAGIC)) { "Unknown trusted-peer database format." }
        val version = input.get().toInt() and 0xff
        val ivLength = input.get().toInt() and 0xff
        val cipherLength = input.int
        require(version == FORMAT_VERSION && ivLength == GCM_IV_BYTES) {
            "Unsupported trusted-peer database format."
        }
        require(cipherLength in GCM_TAG_BYTES..MAX_CIPHERTEXT_BYTES &&
            input.remaining() == ivLength + cipherLength) {
            "Invalid trusted-peer database envelope."
        }
        val header = envelopeHeader(ivLength, cipherLength)
        val iv = ByteArray(ivLength).also(input::get)
        val ciphertext = ByteArray(cipherLength).also(input::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(header)
        return cipher.doFinal(ciphertext)
    }

    private fun getExistingKey(): SecretKey {
        val keyStore = loadKeyStore()
        return keyStore.getKey(keyAlias, null) as? SecretKey
            ?: throw IllegalStateException(
                "Trusted-peer encryption key is missing. Existing trust data was not modified."
            )
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = loadKeyStore()
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun encodeRecords(records: LinkedHashMap<String, TrustedPeerRecord>): ByteArray {
        require(records.size <= MAX_RECORDS) { "Too many trusted peers." }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(records.size)
            records.values.sortedBy { it.peerId }.forEach { record ->
                validateRecord(record)
                output.writeString(record.peerId, MAX_PEER_ID_BYTES)
                output.writeBytesField(record.publicKeyEncoded, MAX_PUBLIC_KEY_BYTES)
                output.writeString(record.displayName, TrustedPeerRegistry.MAX_DISPLAY_NAME_BYTES)
                output.writeLong(record.pairedAtEpochMillis)
                output.writeInt(record.protocolVersion)
            }
        }
        return bytes.toByteArray().also {
            require(it.size <= MAX_PLAINTEXT_BYTES) { "Trusted-peer database is too large." }
        }
    }

    private fun decodeRecords(plaintext: ByteArray): LinkedHashMap<String, TrustedPeerRecord> {
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Trusted-peer database is too large." }
        val records = linkedMapOf<String, TrustedPeerRecord>()
        DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
            val count = input.readInt()
            require(count in 0..MAX_RECORDS) { "Invalid trusted-peer count." }
            repeat(count) {
                val peerId = input.readString(MAX_PEER_ID_BYTES)
                val publicKey = input.readBytesField(MAX_PUBLIC_KEY_BYTES)
                val displayName = input.readString(TrustedPeerRegistry.MAX_DISPLAY_NAME_BYTES)
                val pairedAt = input.readLong()
                val protocolVersion = input.readInt()
                val record = TrustedPeerRecord(peerId, publicKey, displayName, pairedAt, protocolVersion)
                validateRecord(record)
                require(records.put(peerId, record) == null) { "Duplicate trusted peer." }
            }
            require(input.available() == 0) { "Trailing trusted-peer database data." }
        }
        return records
    }

    private fun validateRecord(record: TrustedPeerRecord) {
        require(record.peerId.toByteArray(StandardCharsets.UTF_8).size in 1..MAX_PEER_ID_BYTES)
        require(record.publicKeyEncoded.size in PairingOffer.MIN_PUBLIC_KEY_BYTES..MAX_PUBLIC_KEY_BYTES)
        require(DeviceIdentityStore.isSupportedPublicKey(record.publicKeyEncoded)) {
            "Trusted peer public key is not a supported EC P-256 key."
        }
        require(DeviceIdentityStore.peerIdFor(record.publicKeyEncoded) == record.peerId) {
            "Trusted peer ID does not match its public key."
        }
        require(record.displayName.isNotBlank() &&
            record.displayName.toByteArray(StandardCharsets.UTF_8).size <= TrustedPeerRegistry.MAX_DISPLAY_NAME_BYTES)
        require(record.pairedAtEpochMillis > 0 && record.protocolVersion >= 1)
    }

    private fun copyRecord(record: TrustedPeerRecord) = TrustedPeerRecord(
        record.peerId, record.publicKeyEncoded, record.displayName,
        record.pairedAtEpochMillis, record.protocolVersion
    )

    private fun envelopeHeader(ivLength: Int, cipherLength: Int): ByteArray =
        ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(FORMAT_VERSION.toByte()).put(ivLength.toByte()).putInt(cipherLength).array()

    private fun DataOutputStream.writeString(value: String, maxBytes: Int) =
        writeBytesField(value.toByteArray(StandardCharsets.UTF_8), maxBytes)

    private fun DataOutputStream.writeBytesField(value: ByteArray, maxBytes: Int) {
        require(value.size <= maxBytes)
        writeInt(value.size)
        write(value)
    }

    private fun DataInputStream.readString(maxBytes: Int): String =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(readBytesField(maxBytes))).toString()

    private fun DataInputStream.readBytesField(maxBytes: Int): ByteArray {
        val length = readInt()
        require(length in 0..maxBytes && length <= available()) { "Invalid field length." }
        return ByteArray(length).also(::readFully)
    }

    private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= maxBytes) { "Trusted-peer database is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        private const val DEFAULT_KEY_ALIAS = "devicelink.trusted-peers.aes.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FILE_NAME = "trusted-peers.v1.enc"
        private const val FORMAT_VERSION = 1
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val GCM_IV_BYTES = 12
        private const val MAX_RECORDS = 256
        private const val MAX_PEER_ID_BYTES = 128
        private const val MAX_PUBLIC_KEY_BYTES = 1024
        private const val MAX_PLAINTEXT_BYTES = 512 * 1024
        private const val MAX_CIPHERTEXT_BYTES = MAX_PLAINTEXT_BYTES + GCM_TAG_BYTES
        private const val HEADER_BYTES = 10
        private const val MAX_ENVELOPE_BYTES = HEADER_BYTES + GCM_IV_BYTES + MAX_CIPHERTEXT_BYTES
        private const val MIN_ENVELOPE_BYTES = HEADER_BYTES + GCM_IV_BYTES + GCM_TAG_BYTES
        private val MAGIC = byteArrayOf(0x44, 0x4c, 0x54, 0x50) // DLTP
    }
}
