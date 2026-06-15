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
