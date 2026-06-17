package com.example.botconstructor.botapi.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetAddress

/**
 * Focused tests for the SSRF guards. These exercise the pure scheme/IP predicates directly;
 * [WebClientHttpCaller.isBlockedHost] resolves real hostnames, so the host-level tests use IP
 * literals (which resolve to themselves) to stay deterministic and offline.
 */
class WebClientHttpCallerTest {

    @Test
    fun `only http and https schemes are allowed`() {
        assertThat(WebClientHttpCaller.isAllowedScheme("http")).isTrue()
        assertThat(WebClientHttpCaller.isAllowedScheme("https")).isTrue()
        assertThat(WebClientHttpCaller.isAllowedScheme("HTTPS")).isTrue()
        assertThat(WebClientHttpCaller.isAllowedScheme("file")).isFalse()
        assertThat(WebClientHttpCaller.isAllowedScheme("gopher")).isFalse()
        assertThat(WebClientHttpCaller.isAllowedScheme("ftp")).isFalse()
        assertThat(WebClientHttpCaller.isAllowedScheme(null)).isFalse()
    }

    @Test
    fun `loopback addresses are not public`() {
        assertThat(WebClientHttpCaller.isPublicAddress(ip("127.0.0.1"))).isFalse()
        assertThat(WebClientHttpCaller.isPublicAddress(ip("::1"))).isFalse()
    }

    @Test
    fun `RFC1918 private ranges are not public`() {
        assertThat(WebClientHttpCaller.isPublicAddress(ip("10.0.0.1"))).isFalse()
        assertThat(WebClientHttpCaller.isPublicAddress(ip("172.16.0.1"))).isFalse()
        assertThat(WebClientHttpCaller.isPublicAddress(ip("172.31.255.255"))).isFalse()
        assertThat(WebClientHttpCaller.isPublicAddress(ip("192.168.1.1"))).isFalse()
    }

    @Test
    fun `172 addresses outside the 16-31 second octet are public`() {
        // 172.15/16 and 172.32/16 are NOT part of 172.16/12.
        assertThat(WebClientHttpCaller.isPublicAddress(ip("172.15.0.1"))).isTrue()
        assertThat(WebClientHttpCaller.isPublicAddress(ip("172.32.0.1"))).isTrue()
    }

    @Test
    fun `link-local and the cloud metadata IP are not public`() {
        assertThat(WebClientHttpCaller.isPublicAddress(ip("169.254.0.1"))).isFalse()
        assertThat(WebClientHttpCaller.isPublicAddress(ip("169.254.169.254"))).isFalse()
    }

    @Test
    fun `wildcard and multicast addresses are not public`() {
        assertThat(WebClientHttpCaller.isPublicAddress(ip("0.0.0.0"))).isFalse()
        assertThat(WebClientHttpCaller.isPublicAddress(ip("224.0.0.1"))).isFalse()
    }

    @Test
    fun `genuine public addresses are public`() {
        assertThat(WebClientHttpCaller.isPublicAddress(ip("8.8.8.8"))).isTrue()
        assertThat(WebClientHttpCaller.isPublicAddress(ip("1.1.1.1"))).isTrue()
    }

    @Test
    fun `isBlockedHost blocks private and loopback literals but allows public literals`() {
        assertThat(WebClientHttpCaller.isBlockedHost("127.0.0.1")).isTrue()
        assertThat(WebClientHttpCaller.isBlockedHost("10.0.0.5")).isTrue()
        assertThat(WebClientHttpCaller.isBlockedHost("169.254.169.254")).isTrue()
        assertThat(WebClientHttpCaller.isBlockedHost("8.8.8.8")).isFalse()
    }

    @Test
    fun `isBlockedHost blocks blank and unresolvable hosts`() {
        assertThat(WebClientHttpCaller.isBlockedHost("")).isTrue()
        assertThat(WebClientHttpCaller.isBlockedHost("   ")).isTrue()
        assertThat(WebClientHttpCaller.isBlockedHost("no-such-host.invalid")).isTrue()
    }

    @Test
    fun `IPv6 unique-local ULA addresses are not public`() {
        // fc00::/7 covers both fc00:: and fd00::; JDK isSiteLocalAddress only knows fec0::/10.
        assertThat(WebClientHttpCaller.isPublicAddress(ip("fc00::1"))).isFalse()
        assertThat(WebClientHttpCaller.isPublicAddress(ip("fd00::1"))).isFalse()
        assertThat(WebClientHttpCaller.isPublicAddress(ip("fdff:ffff::1"))).isFalse()
    }

    @Test
    fun `a genuine public IPv6 address is still public`() {
        // 2606:4700:4700::1111 (Cloudflare) is outside fc00::/7.
        assertThat(WebClientHttpCaller.isPublicAddress(ip("2606:4700:4700::1111"))).isTrue()
    }

    @Test
    fun `IPv6 forms embedding an internal IPv4 are not public`() {
        // NAT64 well-known prefix (64:ff9b::/96) and IPv4-compatible (::/96) encodings of an internal
        // IPv4 stay 16-byte and would otherwise be judged public.
        assertThat(WebClientHttpCaller.isPublicAddress(ip("64:ff9b::7f00:1"))).isFalse() // ->127.0.0.1
        assertThat(WebClientHttpCaller.isPublicAddress(ip("64:ff9b::a9fe:a9fe"))).isFalse() // ->169.254.169.254
        assertThat(WebClientHttpCaller.isPublicAddress(ip("::0a00:1"))).isFalse() // ::/96 -> 10.0.0.1
        // A NAT64-wrapped public IPv4 (8.8.8.8) stays public.
        assertThat(WebClientHttpCaller.isPublicAddress(ip("64:ff9b::808:808"))).isTrue()
    }

    @Test
    fun `isBlockedHost blocks a bracketed IPv6 ULA literal`() {
        // URI.host yields the bracketed form for IPv6 literals; the guard must strip brackets.
        assertThat(WebClientHttpCaller.isBlockedHost("[fd00::1]")).isTrue()
        assertThat(WebClientHttpCaller.isBlockedHost("fd00::1")).isTrue()
    }

    @Test
    fun `isBlockedHost blocks dotless-decimal IPv4 literal for loopback`() {
        // 2130706433 == 127.0.0.1; InetAddress decodes the integer form, so it must stay blocked.
        assertThat(WebClientHttpCaller.isBlockedHost("2130706433")).isTrue()
    }

    @Test
    fun `resolver predicate fails when any resolved address is non-public`() {
        // Pure testable core of the connect-time SSRF resolver.
        assertThat(SsrfFilteringResolverGroup.allResolvedArePublic(listOf(ip("8.8.8.8")))).isTrue()
        assertThat(SsrfFilteringResolverGroup.allResolvedArePublic(listOf(ip("127.0.0.1")))).isFalse()
        // Public + private mix must fail (the private IP is what an attacker rebinds to).
        assertThat(
                SsrfFilteringResolverGroup.allResolvedArePublic(listOf(ip("8.8.8.8"), ip("169.254.169.254"))),
        ).isFalse()
        // Empty resolution is unsafe.
        assertThat(SsrfFilteringResolverGroup.allResolvedArePublic(emptyList())).isFalse()
    }

    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal)
}
