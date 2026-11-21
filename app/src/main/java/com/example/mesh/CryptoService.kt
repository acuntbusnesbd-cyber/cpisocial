package com.example.mesh

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-End Encryption (E2EE) Engine for Echo Social Bluetooth Mesh.
 *
 * Uses:
 * - ECDH on NIST P-256 (secp256r1) for ephemeral/identity key agreement
 * - HKDF-SHA256 (RFC 5869) to derive a 256-bit AES session key bound to packet context
 * - AES-256-GCM with 12-byte random IV and 128-bit authentication tag
 * - Additional Authenticated Data (AAD) binding unencrypted routing headers (packetId|senderId|recipientId)
 *
 * Relay Guarantee:
 * Intermediate relay devices only see the unencrypted routing header and opaque Base64 ciphertext.
 * Without the recipient's private key, any decryption attempt fails GCM authentication (AEADBadTagException).
 */
class CryptoService {

    companion object {
        private const val EC_CURVE = "secp256r1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val AES_KEY_BYTES = 32 // 256-bit
        private val HKDF_INFO = "echo-social-ble-mesh-e2ee-v1".toByteArray(StandardCharsets.UTF_8)
    }

    data class EncryptedEnvelope(
        val ciphertextB64: String,
        val ivB64: String,
        val senderPublicKeyB64: String,
        val recipientKeyFingerprint: String,
        val algorithm: String = "ECDH-P256 / AES-256-GCM"
    )

    private val secureRandom = SecureRandom()

    fun generateIdentityKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(EC_CURVE), secureRandom)
        return generator.generateKeyPair()
    }

    fun encodePublicKey(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun decodePublicKey(publicKeyB64: String): PublicKey {
        val bytes = Base64.decode(publicKeyB64, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(bytes)
        return KeyFactory.getInstance("EC").generatePublic(spec)
    }

    fun encodePrivateKey(privateKey: PrivateKey): String {
        return Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
    }

    fun decodePrivateKey(privateKeyB64: String): PrivateKey {
        val bytes = Base64.decode(privateKeyB64, Base64.NO_WRAP)
        val spec = PKCS8EncodedKeySpec(bytes)
        return KeyFactory.getInstance("EC").generatePrivate(spec)
    }

    fun getKeyFingerprint(publicKeyB64: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(Base64.decode(publicKeyB64, Base64.NO_WRAP))
        return digest.take(4).joinToString("") { "%02X".format(it) }
    }

    /**
     * Generates a human-verifiable 6-block Safety Number between two peers (order-independent).
     */
    fun computeSafetyNumber(pubKeyAB64: String, pubKeyBB64: String): String {
        val ordered = listOf(pubKeyAB64, pubKeyBB64).sorted()
        val combined = (ordered[0] + "|" + ordered[1]).toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(combined)
        return (0 until 6).joinToString(" ") { idx ->
            val offset = idx * 4
            val value = ((digest[offset].toInt() and 0xFF) shl 16) or
                ((digest[offset + 1].toInt() and 0xFF) shl 8) or
                (digest[offset + 2].toInt() and 0xFF)
            String.format("%05d", value % 100000)
        }
    }

    /**
     * Encrypts arbitrary bytes (UTF-8 text, JPEG/WebP image chunk, Opus/AAC voice chunk, or PCM audio frame)
     * strictly for the target recipient's Public Key.
     */
    fun encryptForRecipient(
        plaintext: ByteArray,
        senderPrivateKey: PrivateKey,
        senderPublicKey: PublicKey,
        recipientPublicKeyB64: String,
        aadContext: String
    ): EncryptedEnvelope {
        val recipientPublicKey = decodePublicKey(recipientPublicKeyB64)
        val sharedKey = deriveSharedAesKey(
            privateKey = senderPrivateKey,
            peerPublicKey = recipientPublicKey,
            saltContext = aadContext
        )

        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, sharedKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aadContext.toByteArray(StandardCharsets.UTF_8))

        val ciphertext = cipher.doFinal(plaintext)

        return EncryptedEnvelope(
            ciphertextB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            senderPublicKeyB64 = encodePublicKey(senderPublicKey),
            recipientKeyFingerprint = getKeyFingerprint(recipientPublicKeyB64)
        )
    }

    fun encryptTextForRecipient(
        text: String,
        senderPrivateKey: PrivateKey,
        senderPublicKey: PublicKey,
        recipientPublicKeyB64: String,
        aadContext: String
    ): EncryptedEnvelope {
        return encryptForRecipient(
            plaintext = text.toByteArray(StandardCharsets.UTF_8),
            senderPrivateKey = senderPrivateKey,
            senderPublicKey = senderPublicKey,
            recipientPublicKeyB64 = recipientPublicKeyB64,
            aadContext = aadContext
        )
    }

    /**
     * Decrypts an EncryptedEnvelope using the recipient's Private Key.
     * Any intermediate relay node without the recipient's Private Key will fail GCM tag verification.
     */
    fun decryptFromSender(
        envelope: EncryptedEnvelope,
        recipientPrivateKey: PrivateKey,
        aadContext: String
    ): ByteArray {
        val senderPublicKey = decodePublicKey(envelope.senderPublicKeyB64)
        val sharedKey = deriveSharedAesKey(
            privateKey = recipientPrivateKey,
            peerPublicKey = senderPublicKey,
            saltContext = aadContext
        )

        val iv = Base64.decode(envelope.ivB64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.ciphertextB64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, sharedKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aadContext.toByteArray(StandardCharsets.UTF_8))
        return cipher.doFinal(ciphertext)
    }

    fun decryptTextFromSender(
        envelope: EncryptedEnvelope,
        recipientPrivateKey: PrivateKey,
        aadContext: String
    ): String {
        return String(decryptFromSender(envelope, recipientPrivateKey, aadContext), StandardCharsets.UTF_8)
    }

    /**
     * Proves cryptographically that a relay device holding its own KeyPair cannot read the payload.
     */
    fun attemptRelaySnooping(
        envelope: EncryptedEnvelope,
        relayPrivateKey: PrivateKey,
        aadContext: String
    ): Boolean {
        return try {
            decryptFromSender(envelope, relayPrivateKey, aadContext)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun inspectCiphertextSnippet(envelope: EncryptedEnvelope, maxChars: Int = 38): String {
        val raw = envelope.ciphertextB64
        return if (raw.length <= maxChars) raw else raw.take(maxChars) + "..."
    }

    private fun deriveSharedAesKey(
        privateKey: PrivateKey,
        peerPublicKey: PublicKey,
        saltContext: String
    ): SecretKey {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // HKDF-Extract & Expand using HmacSHA256
        val salt = MessageDigest.getInstance("SHA-256")
            .digest(saltContext.toByteArray(StandardCharsets.UTF_8))
        val prk = hmacSha256(salt, sharedSecret)
        val okm = hmacSha256(prk, HKDF_INFO + byteArrayOf(0x01))
        return SecretKeySpec(okm.copyOf(AES_KEY_BYTES), "AES")
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
