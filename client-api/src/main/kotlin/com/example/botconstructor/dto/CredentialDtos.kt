package com.example.botconstructor.dto

import com.example.botconstructor.model.Credential

/**
 * The supported credential types. Each names a secret shape and fixes the EXACT set of secret field
 * keys its `data` map must carry; [CredentialService] validates create/update bodies against this.
 *
 * @property id The stable wire id stored on the document and accepted on input.
 * @property fields The exact secret field keys required for this type (no more, no fewer).
 */
enum class CredentialType(val id: String, val fields: List<String>) {
    TELEGRAM_API("telegramApi", listOf("botToken")),
    ANTHROPIC_API("anthropicApi", listOf("apiKey")),
    SLACK_WEBHOOK("slackWebhook", listOf("webhookUrl")),
    DISCORD_WEBHOOK("discordWebhook", listOf("webhookUrl")),
    HTTP_HEADER_AUTH("httpHeaderAuth", listOf("headerName", "headerValue")),
    HTTP_BEARER_AUTH("httpBearerAuth", listOf("token"));

    companion object {
        fun fromId(id: String?): CredentialType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Create/update body. On create, [type] and [data] (with exactly the type's fields) are required. On
 * update both are optional: a present [name] renames, a present [data] re-encrypts; [type] is never
 * changed by update (a present value is ignored). The validation of [data] against the type lives in
 * [com.example.botconstructor.services.CredentialService] so it can return the shared 400 envelope.
 */
data class CredentialRequest(
        val name: String? = null,
        val type: String? = null,
        val data: Map<String, String>? = null,
)

/**
 * Metadata-only response handed to owners. It NEVER carries the secret fields or the encrypted blob —
 * exposing only what is safe to show in a credentials list/picker.
 */
data class CredentialView(
        val id: String,
        val name: String,
        val type: String,
        val createdAt: java.time.Instant,
)

/**
 * Decrypted secret, returned ONLY by the internal server-to-server fetch (never to a browser/owner).
 * The [data] map holds the plaintext secret fields and must never be logged, traced, or persisted by
 * the caller.
 */
data class CredentialSecretView(
        val type: String,
        val data: Map<String, String>,
)

fun Credential.toView() = CredentialView(
        id = id,
        name = name,
        type = type,
        createdAt = createdAt,
)
