package com.example.botconstructor.authserver.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import org.springframework.security.config.annotation.rsocket.RSocketSecurity
import org.springframework.security.messaging.handler.invocation.reactive.AuthenticationPrincipalArgumentResolver
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor


@Configuration
@EnableRSocketSecurity
@EnableReactiveMethodSecurity
class SecurityConfig {
    @Bean
    fun messageHandler(strategies: RSocketStrategies): RSocketMessageHandler {
        val mh = RSocketMessageHandler()
        mh.argumentResolverConfigurer.addCustomResolver(AuthenticationPrincipalArgumentResolver())
        mh.rSocketStrategies = strategies
        return mh
    }

    @Bean
    fun rsocketInterceptor(security: RSocketSecurity): PayloadSocketAcceptorInterceptor {
        return security.authorizePayload { authorize ->
            authorize
                    .anyRequest().authenticated()
                    .anyExchange().permitAll()
        }.build()
    }
} 