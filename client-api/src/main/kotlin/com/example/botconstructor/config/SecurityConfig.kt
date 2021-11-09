package com.example.botconstructor;

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        webFilter: AuthenticationWebFilter?,
        endpointsConfig: EndpointsSecurityConfig
    ): SecurityWebFilterChain = http
        .authorizeExchange()
        .applyConfig(endpointsConfig)
        .and()
        .addFilterAt(webFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .httpBasic().disable()
        .cors().disable()
        .csrf().disable()
        .formLogin().disable()
        .logout().disable()
        .build()


    @Bean
    fun endpointsConfig() = EndpointsSecurityConfig { http ->
        http
            .pathMatchers(HttpMethod.POST, "/api/users", "/api/users/login").permitAll()
            .pathMatchers(HttpMethod.GET, "/actuator/**", "/actuator").permitAll()
            .anyExchange().authenticated()
    }

    private fun AuthorizeExchangeSpec.applyConfig(config: EndpointsSecurityConfig) = config.apply(this)
}

fun interface EndpointsSecurityConfig {
    fun apply(http: AuthorizeExchangeSpec): AuthorizeExchangeSpec
}
