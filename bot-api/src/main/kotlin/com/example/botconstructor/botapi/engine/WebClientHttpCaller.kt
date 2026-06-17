package com.example.botconstructor.botapi.engine

import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.time.Duration

/**
 * Production [HttpCaller] backed by a hardened [WebClient]. This lets a bot owner trigger
 * server-side requests, so every call is gated by SSRF/safety guards and can NEVER throw out of the
 * walk — failures resolve to a non-ok [HttpCallResult] (statusCode `0`) which routes the graph to
 * the node's `error`/default handle.
 *
 * Guards enforced:
 *  - **Scheme allow-list:** only `http`/`https`; anything else (`file:`, `gopher:`, ...) is blocked.
 *  - **Resolver-pinned address allow-list:** a custom Netty [AddressResolverGroup]
 *    ([SsrfFilteringResolverGroup]) wraps the default resolver and FAILS resolution when ANY
 *    resolved address is non-public — so the IP Netty actually connects to is the IP that was
 *    validated. This closes the DNS-rebinding / TOCTOU window where a TTL-0 name returns a public IP
 *    at check time and a private IP at connect time: validation and connection share one resolution.
 *    The pre-flight [isBlockedHost] literal check remains as a cheap fast-fail, but the resolver is
 *    the authoritative defense.
 *  - **Block-list:** loopback, RFC1918 private (10/8, 172.16/12, 192.168/16), link-local
 *    (169.254/16, incl. the cloud metadata IP 169.254.169.254), wildcard/any-local, multicast,
 *    site-local, and IPv6 unique-local ULA (`fc00::/7`, i.e. fc00:: and fd00::) addresses.
 *  - **No redirects:** auto-redirect is disabled so a public URL cannot 30x-bounce to a private one.
 *  - **Response size cap:** [MAX_RESPONSE_BYTES] via `maxInMemorySize` prevents memory exhaustion.
 *  - **Timeout:** [REQUEST_TIMEOUT]; a timeout is treated as an error.
 */
@Component
class WebClientHttpCaller : HttpCaller {

    private val webClient: WebClient = WebClient.builder()
            .clientConnector(
                    ReactorClientHttpConnector(
                            HttpClient.create()
                                    // Disable auto-redirects: a public URL must not 30x to a private host.
                                    .followRedirect(false)
                                    // Pin the connect-time address to the validated one: the resolver
                                    // fails for any host that resolves to a non-public IP, so Netty can
                                    // never connect to an address that was not checked (anti-rebinding).
                                    .resolver(SsrfFilteringResolverGroup),
                    ),
            )
            .codecs { it.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES) }
            .build()

    override fun call(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: String?,
    ): Mono<HttpCallResult> {
        // Validate before any network activity; a blocked URL returns a non-ok result, not an error.
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return Mono.just(blocked())
        }
        if (!isAllowedScheme(uri.scheme)) return Mono.just(blocked())
        val host = uri.host ?: return Mono.just(blocked())
        if (isBlockedHost(host)) return Mono.just(blocked())

        val httpMethod = HttpMethod.valueOf(method)

        return Mono.defer {
            val request = webClient.method(httpMethod)
                    .uri(uri)
                    .headers { h -> headers.forEach { (k, v) -> h.set(k, v) } }
            val spec = if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).bodyValue(body)
            } else {
                request
            }
            spec.exchangeToMono { response ->
                response.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .map { raw ->
                            HttpCallResult(
                                    statusCode = response.statusCode().value(),
                                    body = parseBody(raw, response.headers().contentType().orElse(null)),
                                    ok = response.statusCode().is2xxSuccessful,
                            )
                        }
            }
        }
                .timeout(REQUEST_TIMEOUT)
                // Any transport error, timeout, or oversized body becomes a non-ok result so the
                // engine routes to `error`/default instead of aborting the walk.
                .onErrorReturn(blocked())
    }

    /** A non-ok result for a request that never produced a response (blocked, malformed, failed). */
    private fun blocked(): HttpCallResult = HttpCallResult(statusCode = 0, body = null, ok = false)

    /**
     * Parses a raw response body: JSON (object/array) is decoded so nested fields are reachable via
     * `{{saveAs.field}}` interpolation; anything else (or unparseable JSON) is kept as the String.
     */
    private fun parseBody(raw: String, contentType: MediaType?): Any? {
        if (raw.isEmpty()) return null
        val looksJson = contentType?.isCompatibleWith(MediaType.APPLICATION_JSON) == true ||
                raw.trimStart().firstOrNull().let { it == '{' || it == '[' }
        if (!looksJson) return raw
        return try {
            JSON.readValue(raw, Any::class.java)
        } catch (_: Exception) {
            raw
        }
    }

    companion object {
        /** Per-request timeout; a timeout is reported as a non-ok result, never an exception. */
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(5)

        /** Upper bound on a buffered response body (256 KB) to cap memory use. */
        private const val MAX_RESPONSE_BYTES = 256 * 1024

        private val JSON = com.fasterxml.jackson.databind.ObjectMapper()

        /** Only `http`/`https` are allowed; case-insensitive. */
        fun isAllowedScheme(scheme: String?): Boolean =
                scheme?.lowercase() in setOf("http", "https")

        /**
         * True when [host] must NOT be contacted. Resolves the host to its IP addresses and blocks
         * if ANY resolved address is non-public (loopback / private / link-local / wildcard /
         * multicast / site-local), or if resolution fails. Validating the resolved IPs — not the
         * literal host string — defeats names that point at internal addresses.
         */
        fun isBlockedHost(host: String): Boolean {
            if (host.isBlank()) return true
            // `URI.host` yields IPv6 literals in bracketed form (`[fd00::1]`); strip brackets so
            // `InetAddress.getByName` (which expects the unbracketed form) can resolve them.
            val bare = host.removeSurrounding("[", "]")
            val addresses = try {
                InetAddress.getAllByName(bare)
            } catch (_: UnknownHostException) {
                return true // cannot resolve -> refuse
            }
            if (addresses.isEmpty()) return true
            return addresses.any { !isPublicAddress(it) }
        }

        /**
         * True when [address] is a routable public address. Mirrors the SSRF block-list: loopback,
         * any-local (0.0.0.0/::), link-local (169.254/16, fe80::/10, incl. the 169.254.169.254 cloud
         * metadata endpoint), site-local/private (10/8, 172.16/12, 192.168/16, fec0::/10), and
         * multicast addresses are all NON-public.
         */
        fun isPublicAddress(address: InetAddress): Boolean = !(
                address.isLoopbackAddress ||
                        address.isAnyLocalAddress ||
                        address.isLinkLocalAddress ||
                        address.isSiteLocalAddress ||
                        address.isMulticastAddress ||
                        isPrivateIpv4(address) ||
                        isUniqueLocalIpv6(address) ||
                        isBlockedEmbeddedIpv4(address)
                )

        /**
         * Blocks IPv6 forms that embed an internal IPv4 address in their low 32 bits — IPv4-compatible
         * (`::/96`) and the NAT64 well-known prefix (`64:ff9b::/96`). The JDK collapses IPv4-mapped
         * (`::ffff:0:0/96`) literals to `Inet4Address` so those already hit the IPv4 checks, but the
         * compatible/NAT64 forms stay 16-byte and would otherwise be judged public even when the
         * embedded host is loopback/private. Re-runs the block-list on the embedded IPv4.
         */
        private fun isBlockedEmbeddedIpv4(address: InetAddress): Boolean {
            val o = address.address
            if (o.size != 16) return false
            val z = 0.toByte()
            val ipv4Compatible = (0 until 12).all { o[it] == z }
            val nat64 = o[0] == z && o[1] == 0x64.toByte() &&
                    o[2] == 0xFF.toByte() && o[3] == 0x9B.toByte() &&
                    (4 until 12).all { o[it] == z }
            if (!ipv4Compatible && !nat64) return false
            val embedded = InetAddress.getByAddress(byteArrayOf(o[12], o[13], o[14], o[15]))
            return !isPublicAddress(embedded)
        }

        /**
         * Blocks IPv6 unique-local addresses (ULA, `fc00::/7` — covers both `fc00::` and `fd00::`).
         * [InetAddress.isSiteLocalAddress] only recognizes the deprecated `fec0::/10` range, so modern
         * ULAs would otherwise slip through. The high 7 bits of the first octet identify the range:
         * `(octet0 & 0xFE) == 0xFC`.
         */
        private fun isUniqueLocalIpv6(address: InetAddress): Boolean {
            val octets = address.address
            if (octets.size != 16) return false
            return (octets[0].toInt() and 0xFE) == 0xFC
        }

        /**
         * Explicit RFC1918 check for IPv4. [InetAddress.isSiteLocalAddress] already covers these
         * ranges, but checking the raw octets is robust and self-documenting (and guards the
         * 172.16/12 boundary precisely).
         */
        private fun isPrivateIpv4(address: InetAddress): Boolean {
            val octets = address.address
            if (octets.size != 4) return false
            val a = octets[0].toInt() and 0xFF
            val b = octets[1].toInt() and 0xFF
            return when (a) {
                10 -> true                                   // 10.0.0.0/8
                172 -> b in 16..31                            // 172.16.0.0/12
                192 -> b == 168                               // 192.168.0.0/16
                169 -> b == 254                               // 169.254.0.0/16 (link-local / metadata)
                127 -> true                                   // 127.0.0.0/8 loopback
                0 -> true                                     // 0.0.0.0/8
                else -> false
            }
        }
    }
}
