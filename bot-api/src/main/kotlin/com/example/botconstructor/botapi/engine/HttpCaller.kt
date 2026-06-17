package com.example.botconstructor.botapi.engine

import reactor.core.publisher.Mono

/**
 * Result of an HTTP call made by the engine on behalf of an `httpRequest` node.
 *
 * @property statusCode The HTTP status code, or `0` when the request never produced a response
 *   (blocked by SSRF guards, DNS failure, timeout, malformed URL, etc.).
 * @property body The parsed response body: a `Map`/`List` when the body was JSON, a `String` for
 *   plain text, or `null` when there was no body. This is what gets stored into `vars[saveAs]`, so
 *   nested JSON is reachable via dotted-path interpolation (`{{saveAs.field}}`).
 * @property ok Whether the call succeeded with a 2xx status. A non-ok result routes the walk down
 *   the node's `error` handle (or the default handle when no `error` edge exists).
 */
data class HttpCallResult(
        val statusCode: Int,
        val body: Any?,
        val ok: Boolean,
)

/**
 * Spring-free seam the engine uses to make outbound HTTP calls. Keeping this a `fun interface` lets
 * the trigger/keyword/condition tests supply a trivial fake while production wires a hardened,
 * `WebClient`-backed implementation ([com.example.botconstructor.botapi.engine.WebClientHttpCaller]).
 *
 * Implementations MUST NOT throw out of [call]: any failure (blocked host, timeout, transport error,
 * non-2xx) is reported as a non-ok [HttpCallResult] so the graph walk can route it to `error` rather
 * than aborting.
 */
fun interface HttpCaller {
    /**
     * Performs [method] against [url] with [headers] and an optional [body].
     */
    fun call(method: String, url: String, headers: Map<String, String>, body: String?): Mono<HttpCallResult>
}
