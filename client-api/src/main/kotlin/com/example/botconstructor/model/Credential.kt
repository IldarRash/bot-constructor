package com.example.botconstructor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * An owner-scoped, reusable secret (e.g. a Telegram bot token, an Anthropic API key, a webhook URL)
 * referenced by flow nodes through `data.credentialId` so secrets live OUT of inline node config.
 *
 * The secret field map (e.g. `{botToken: "..."}`) is JSON-serialized then encrypted with AES-256-GCM
 * by [com.example.botconstructor.security.CredentialCipher]; only the resulting [encrypted] blob is
 * stored here. The plaintext secret is never persisted, and decryption happens only inside client-api
 * for the internal server-to-server fetch. Responses to owners carry metadata only — never [encrypted]
 * and never the decrypted fields.
 *
 * @property type One of the supported credential type ids (see
 * [com.example.botconstructor.dto.CredentialType]); it fixes which secret fields are valid.
 * @property encrypted The Base64 `IV || ciphertext+tag` blob produced by the cipher.
 */
@Document("credential")
data class Credential(
        @Id
        val id: String,
        val ownerId: String,
        val name: String,
        val type: String,
        val encrypted: String,
        val createdAt: Instant = Instant.now(),
)
