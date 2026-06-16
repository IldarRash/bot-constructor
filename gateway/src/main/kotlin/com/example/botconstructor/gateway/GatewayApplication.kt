package com.example.botconstructor.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.web.server.WebFilter

@SpringBootApplication
class GatewayApplication {

    /**
     * Blocks any path under `/api/internal/` at the edge. Those endpoints (e.g. the webhook-token
     * lookup) are for server-to-server calls only — bot-api reaches client-api directly via
     * `CLIENT_API_URI`, never through this gateway — so they must not be reachable from the internet
     * via the broad `/api` catch-all route. A `WebFilter` runs before gateway route matching, so it
     * short-circuits the request; returns 404 so a blocked path is indistinguishable from a
     * nonexistent one.
     */
    @Bean
    fun blockInternalPaths(): WebFilter = WebFilter { exchange, chain ->
        if (exchange.request.uri.path.startsWith("/api/internal/")) {
            exchange.response.statusCode = HttpStatus.NOT_FOUND
            exchange.response.setComplete()
        } else {
            chain.filter(exchange)
        }
    }
}

fun main(args: Array<String>) {
    runApplication<GatewayApplication>(*args)
}
