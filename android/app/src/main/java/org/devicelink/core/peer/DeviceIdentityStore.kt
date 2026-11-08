package org.devicelink.core.peer

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.AlgorithmParameters
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec

/**
 * Installation identity backed by Android Keystore.
 *
 * The private key is non-exportable. This class does not pair peers or create
 * a network session; it only provides a stable public identity and signatures.
 */
class DeviceIdentityStore(
    internal val keyAlias: String = DEFAULT_KEY_ALIAS
) {
    @Synchronized
    fun getOrCreateIdentity(): DeviceIdentity {
        val keyStore = loadKeyStore()
        val existing = keyStore.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry
        val entry = existing ?: createKeyEntry(keyStore)
        val publicKey = entry.certificate.publicKey
        return DeviceIdentity(
            peerId = peerIdFor(publicKey.encoded),
            publicKeyEncoded = publicKey.encoded.clone()
        )
    }

    @Synchronized
    fun sign(challenge: ByteArray): ByteArray {
        require(challenge.isNotEmpty()) { "Challenge must not be empty." }
        val keyStore = loadKeyStore()
        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: createKeyEntry(keyStore)
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(entry.privateKey)
            update(challenge)
            sign()
        }
    }

    fun verify(publicKeyEncoded: ByteArray, challenge: ByteArray, signature: ByteArray): Boolean {
        if (publicKeyEncoded.isEmpty() || challenge.isEmpty() || signature.isEmpty()) return false
        return try {
            val keyFactory = java.security.KeyFactory.getInstance(KEY_ALGORITHM)
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyEncoded))
            Signature.getInstance(SIGNATURE_ALGORITHM).run {
                initVerify(publicKey)
                update(challenge)
                verify(signature)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun createKeyEntry(keyStore: KeyStore): KeyStore.PrivateKeyEntry {
        val generator = KeyPairGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        generator.generateKeyPair()
        return keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: error("Android Keystore did not return the generated identity key.")
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        private const val DEFAULT_KEY_ALIAS = "devicelink.installation.identity.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALGORITHM = "EC"
        private const val EC_CURVE = "secp256r1"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

        /** True only for a decodable EC public key using the exact P-256 domain parameters. */
        fun isSupportedPublicKey(publicKeyEncoded: ByteArray): Boolean = try {
            val key = java.security.KeyFactory.getInstance(KEY_ALGORITHM)
                .generatePublic(X509EncodedKeySpec(publicKeyEncoded)) as? ECPublicKey
                ?: return false
            val expected = AlgorithmParameters.getInstance(KEY_ALGORITHM).run {
                init(ECGenParameterSpec(EC_CURVE))
                getParameterSpec(ECParameterSpec::class.java)
            }
            val actual = key.params
            actual.curve == expected.curve &&
                actual.generator == expected.generator &&
                actual.order == expected.order &&
                actual.cofactor == expected.cofactor
        } catch (_: Exception) {
            false
        }

        /** Stable, platform-neutral identifier derived from the canonical public-key encoding. */
        fun peerIdFor(publicKeyEncoded: ByteArray): String {
            require(publicKeyEncoded.isNotEmpty()) { "Public key must not be empty." }
            val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyEncoded)
            val hex = digest.joinToString("") { "%02x".format(it) }
            return "dl1:$hex"
        }
    }
}

data class DeviceIdentity(
    val peerId: String,
    val publicKeyEncoded: ByteArray
)
