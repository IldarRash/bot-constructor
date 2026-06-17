package com.example.botconstructor.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

class CredentialCipherTest {

    // A real 32-byte key so the test does not depend on the insecure dev fallback.
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val cipher = CredentialCipher(mapOf(CredentialCipher.KEY_ENV to key))

    @Test
    fun `encrypt then decrypt round-trips the plaintext`() {
        val plaintext = """{"botToken":"123:secret"}"""

        val blob = cipher.encrypt(plaintext)

        assertThat(blob).isNotEqualTo(plaintext)
        assertThat(cipher.decrypt(blob)).isEqualTo(plaintext)
    }

    @Test
    fun `two encryptions of the same plaintext differ due to a random IV`() {
        val plaintext = "same-secret"

        val first = cipher.encrypt(plaintext)
        val second = cipher.encrypt(plaintext)

        assertThat(first).isNotEqualTo(second)
        assertThat(cipher.decrypt(first)).isEqualTo(plaintext)
        assertThat(cipher.decrypt(second)).isEqualTo(plaintext)
    }

    @Test
    fun `falls back to the insecure dev key when the env var is absent`() {
        val devCipher = CredentialCipher(emptyMap())

        val blob = devCipher.encrypt("hello")

        assertThat(devCipher.decrypt(blob)).isEqualTo("hello")
    }
}
