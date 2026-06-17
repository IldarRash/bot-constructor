package com.example.botconstructor.botapi.engine

import io.netty.resolver.AddressResolver
import io.netty.resolver.AddressResolverGroup
import io.netty.resolver.DefaultAddressResolverGroup
import io.netty.util.concurrent.EventExecutor
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.Promise
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException

/**
 * A Netty [AddressResolverGroup] that pins SSRF validation to the address actually connected to.
 *
 * It delegates DNS resolution to the JDK default resolver, then filters every resolved
 * [InetSocketAddress] through [WebClientHttpCaller.isPublicAddress]. If ANY resolved address is
 * non-public the resolution is FAILED (with [UnknownHostException]), so Reactor-Netty never connects.
 * Because the request issued against the original URI re-uses THIS resolver at connect time, the IP
 * that gets validated is exactly the IP that gets dialed — closing the DNS-rebinding / TOCTOU window
 * that a separate pre-flight `InetAddress.getAllByName` check would leave open (TTL-0 trickery).
 *
 * The failed resolution surfaces as a transport error in [WebClientHttpCaller.call], which maps it to
 * a non-ok [HttpCallResult] routed to the node's `error`/default handle — never an exception out of
 * the walk.
 */
object SsrfFilteringResolverGroup : AddressResolverGroup<InetSocketAddress>() {

    private val delegate: AddressResolverGroup<InetSocketAddress> = DefaultAddressResolverGroup.INSTANCE

    /**
     * The pure, testable core: true when [addresses] is safe to connect to — non-empty and every
     * resolved address is public. An empty list (no resolution) and any non-public address are unsafe.
     */
    fun allResolvedArePublic(addresses: List<InetAddress>): Boolean =
            addresses.isNotEmpty() && addresses.all { WebClientHttpCaller.isPublicAddress(it) }

    override fun newResolver(executor: EventExecutor): AddressResolver<InetSocketAddress> =
            FilteringResolver(delegate.getResolver(executor), executor)

    /**
     * Wraps the delegate resolver, re-checking each resolved address against the SSRF block-list and
     * failing the resolution when any address is non-public.
     */
    private class FilteringResolver(
            private val delegate: AddressResolver<InetSocketAddress>,
            private val executor: EventExecutor,
    ) : AddressResolver<InetSocketAddress> {

        override fun isSupported(address: java.net.SocketAddress): Boolean = delegate.isSupported(address)

        override fun isResolved(address: java.net.SocketAddress): Boolean = delegate.isResolved(address)

        override fun resolve(address: java.net.SocketAddress): Future<InetSocketAddress> {
            val promise = executor.newPromise<InetSocketAddress>()
            delegate.resolve(address).addListener {
                @Suppress("UNCHECKED_CAST")
                val f = it as Future<InetSocketAddress>
                if (!f.isSuccess) {
                    promise.setFailure(f.cause())
                } else {
                    val resolved = f.now
                    if (resolved != null && !resolved.isUnresolved && isPublic(resolved.address)) {
                        promise.setSuccess(resolved)
                    } else {
                        promise.setFailure(blocked(address))
                    }
                }
            }
            return promise
        }

        override fun resolve(address: java.net.SocketAddress, promise: Promise<InetSocketAddress>): Future<InetSocketAddress> {
            resolve(address).addListener {
                @Suppress("UNCHECKED_CAST")
                val f = it as Future<InetSocketAddress>
                if (f.isSuccess) promise.setSuccess(f.now) else promise.setFailure(f.cause())
            }
            return promise
        }

        override fun resolveAll(address: java.net.SocketAddress): Future<List<InetSocketAddress>> {
            val promise = executor.newPromise<List<InetSocketAddress>>()
            delegate.resolveAll(address).addListener {
                @Suppress("UNCHECKED_CAST")
                val f = it as Future<List<InetSocketAddress>>
                if (!f.isSuccess) {
                    promise.setFailure(f.cause())
                } else {
                    val resolved = f.now ?: emptyList()
                    val ips = resolved.filterNot { a -> a.isUnresolved }.map { a -> a.address }
                    if (SsrfFilteringResolverGroup.allResolvedArePublic(ips)) {
                        promise.setSuccess(resolved)
                    } else {
                        promise.setFailure(blocked(address))
                    }
                }
            }
            return promise
        }

        override fun resolveAll(
                address: java.net.SocketAddress,
                promise: Promise<List<InetSocketAddress>>,
        ): Future<List<InetSocketAddress>> {
            resolveAll(address).addListener {
                @Suppress("UNCHECKED_CAST")
                val f = it as Future<List<InetSocketAddress>>
                if (f.isSuccess) promise.setSuccess(f.now) else promise.setFailure(f.cause())
            }
            return promise
        }

        override fun close() = delegate.close()

        private fun isPublic(ip: InetAddress?): Boolean =
                ip != null && WebClientHttpCaller.isPublicAddress(ip)

        private fun blocked(address: java.net.SocketAddress): UnknownHostException =
                UnknownHostException("SSRF guard blocked resolution for $address (non-public address)")
    }
}
