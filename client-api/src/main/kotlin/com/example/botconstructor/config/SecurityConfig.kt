package com.example.botconstructor.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter

/**
 * Configures web security for the application.
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    /**
     * Creates a [SecurityWebFilterChain] to protect the application's endpoints.
     *
     * @param http The [ServerHttpSecurity] to configure.
     * @param webFilter The [AuthenticationWebFilter] to add to the filter chain.
     * @return The configured [SecurityWebFilterChain].
     */
    @Bean
    fun securityWebFilterChain(
            http: ServerHttpSecurity,
            webFilter: AuthenticationWebFilter
    ): SecurityWebFilterChain = http
            .authorizeExchange { ex ->
                ex.pathMatchers(HttpMethod.POST, "/api/users", "/api/users/login").permitAll()
                ex.pathMatchers(HttpMethod.GET, "/actuator/**", "/actuator").permitAll()
                // Internal webhook lookup for bot-api: unauthenticated-but-token-guarded. There is
                // no user JWT because the webhook caller is anonymous; the unguessable, high-entropy
                // token in the path is the only credential and authorizes reading exactly that one
                // bot's flow. Unknown tokens return an opaque 404 (no enumeration). Scoped to GET
                // and this exact subpath so it widens the public surface as little as possible.
                ex.pathMatchers(HttpMethod.GET, "/api/internal/bots/by-webhook/**").permitAll()
                // Internal execution-record write from bot-api: same server-to-server boundary as
                // the webhook lookup. The gateway 404s all /api/internal/**, so this POST is never
                // client-reachable; the owner is resolved from the bot document in client-api, never
                // trusted from the request body. Scoped to POST and this exact path.
                ex.pathMatchers(HttpMethod.POST, "/api/internal/executions").permitAll()
                // Internal decrypted-credential fetch for bot-api: same server-to-server boundary as
                // the webhook lookup. The gateway 404s all internal paths, so this GET is never
                // client-reachable; the service returns the secret only when the credential and the
                // referenced bot share an owner (else an opaque 404). Scoped to GET and the internal
                // credentials subpath.
                ex.pathMatchers(HttpMethod.GET, "/api/internal/credentials/**").permitAll()
                // RSocket-over-websocket is a separate boundary that authenticates from its own
                // SETUP frame; the websocket upgrade must not be challenged by the HTTP filter.
                ex.pathMatchers("/rsocket").permitAll()
                ex.anyExchange().authenticated()
            }
            .addFilterAt(webFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .httpBasic { it.disable() }
            .cors { it.disable() }
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .build()
}
