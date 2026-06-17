package com.example.botconstructor.botapi.engine

import reactor.core.publisher.Mono

/**
 * A decrypted credential as seen by the engine: the credential's [type] (e.g. `telegramApi`,
 * `httpBearerAuth`) and its secret field map [data] (e.g. `{botToken: "..."}`). The values here are
 * live secrets — they may ONLY flow into the outbound HTTP call a node builds. They must NEVER be
 * written into item json, a NodeTrace, the persisted execution record, or any log.
 *
 * @property type The credential type name; determines which secret field a connector reads.
 * @property data The decrypted secret field map.
 */
data class CredentialSecret(
        val type: String,
        val data: Map<String, String>,
)

/**
 * Spring-free seam the engine uses to resolve a `data.credentialId` reference into its decrypted
 * [CredentialSecret]. Mirrors [HttpCaller]: a `fun interface` so engine tests can supply a trivial
 * fake while the runtime wires the real, client-api-backed implementation bound to the current bot.
 *
 * Resolution is owner-scoped and anti-IDOR: the real implementation resolves through client-api's
 * internal credential lookup keyed by the current bot, so a graph can never reference another owner's
 * credential. An unknown / foreign / failing id resolves to an empty [Mono] (NOT an error), letting a
 * node distinguish "no credential configured" from "configured but unresolvable" by whether it asked.
 */
fun interface CredentialResolver {
    /**
     * Resolves [credentialId] to its decrypted [CredentialSecret], or an empty [Mono] when the id is
     * unknown, foreign, or otherwise unresolvable. MUST NOT leak the secret into logs/traces.
     */
    fun resolve(credentialId: String): Mono<CredentialSecret>

    companion object {
        /** No-op resolver: every id resolves empty. The default for callers that pass no resolver. */
        val NONE: CredentialResolver = CredentialResolver { Mono.empty() }
    }
}
