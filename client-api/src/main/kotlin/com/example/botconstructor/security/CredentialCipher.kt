package com.example.botconstructor.security

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM symmetric cipher for credential secrets. Each [encrypt] generates a fresh random
 * 12-byte IV and prepends it to the ciphertext, so encrypting the same plaintext twice yields
 * different blobs and an attacker cannot detect duplicate secrets.
 *
 * The 256-bit key comes from the env var named by [KEY_ENV] (a Base64 of 32 random bytes) and is
 * held only in memory: it is never persisted in Mongo, never logged, and never returned in any
 * response. When the env var is absent the cipher logs one loud startup warning and falls back to a
 * clearly-named, fixed INSECURE dev key so local development works — that key must never be relied
 * on in production.
 *
 * Blob layout: a single Base64 string of `IV(12 bytes) concatenated with GCM ciphertext+tag`.
 */
@Component
class CredentialCipher(env: Map<String, String> = System.getenv()) {

    private val key: SecretKeySpec = resolveKey(env[KEY_ENV])
    private val random = SecureRandom()

    /**
     * Encrypts [plaintext] under AES-256-GCM with a fresh random 12-byte IV, returning a single
     * Base64 blob of `IV || ciphertext+tag`. Two calls with the same input produce different blobs.
     */
    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    /**
     * Decrypts a [blob] produced by [encrypt], splitting the leading 12-byte IV from the GCM
     * ciphertext+tag. GCM authentication fails (throws) if the blob was tampered with or the key
     * is wrong.
     */
    fun decrypt(blob: String): String {
        val bytes = Base64.getDecoder().decode(blob)
        require(bytes.size > IV_BYTES) { "credential ciphertext is malformed" }
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val ciphertext = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun resolveKey(configured: String?): SecretKeySpec {
        val raw = if (configured.isNullOrBlank()) {
            log.warn(
                    "{} is not set: falling back to a FIXED INSECURE DEV KEY for credential encryption. " +
                            "This is acceptable only for local development. Set {} to a Base64 of 32 random " +
                            "bytes in any real environment.",
                    KEY_ENV,
                    KEY_ENV,
            )
            Base64.getDecoder().decode(INSECURE_DEV_KEY_B64)
        } else {
            Base64.getDecoder().decode(configured)
        }
        require(raw.size == KEY_BYTES) {
            "$KEY_ENV must decode to exactly $KEY_BYTES bytes (got ${raw.size}) for AES-256"
        }
        return SecretKeySpec(raw, "AES")
    }

    companion object {
        /** Env var holding the Base64 of 32 random bytes used as the AES-256 key. */
        const val KEY_ENV = "CREDENTIAL_ENCRYPTION_KEY"

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEY_BYTES = 32

        /**
         * A fixed, all-zero 32-byte key (Base64) used ONLY when [KEY_ENV] is unset, so local dev
         * works without configuration. It is intentionally well-known and insecure; never rely on it
         * outside local development.
         */
        private const val INSECURE_DEV_KEY_B64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

        private val log = LoggerFactory.getLogger(CredentialCipher::class.java)
    }
}
