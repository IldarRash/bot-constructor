package com.example.botconstructor.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec
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
     * @param customEndpointsSecurity The custom security configuration for endpoints.
     * @return The configured [SecurityWebFilterChain].
     */
    @Bean
    fun securityWebFilterChain(
            http: ServerHttpSecurity,
            webFilter: AuthenticationWebFilter,
            customEndpointsSecurity: EndpointsSecurityConfig
    ): SecurityWebFilterChain = http
            .authorizeExchange()
            .applyConfig(customEndpointsSecurity)
            .and()
            .addFilterAt(webFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .httpBasic().disable()
            .cors().disable()
            .csrf().disable()
            .formLogin().disable()
            .logout().disable()
            .build()


    /**
     * Defines the security rules for specific endpoints.
     *
     * @return An [EndpointsSecurityConfig] bean.
     */
    @Bean
    fun customEndpointsSecurity() = EndpointsSecurityConfig { http ->
        http
                .pathMatchers(HttpMethod.POST, "/api/users", "/api/users/login").permitAll()
                .pathMatchers(HttpMethod.GET, "/actuator/**", "/actuator").permitAll()
                .anyExchange().authenticated()
    }

    private fun AuthorizeExchangeSpec.applyConfig(config: EndpointsSecurityConfig) = config.apply(this)
}

/**
 * A functional interface for applying custom security rules to the [AuthorizeExchangeSpec].
 */
fun interface EndpointsSecurityConfig {
    /**
     * Applies the security rules to the given [AuthorizeExchangeSpec].
     *
     * @param http The [AuthorizeExchangeSpec] to configure.
     * @return The configured [AuthorizeExchangeSpec].
     */
    fun apply(http: AuthorizeExchangeSpec): AuthorizeExchangeSpec
}
