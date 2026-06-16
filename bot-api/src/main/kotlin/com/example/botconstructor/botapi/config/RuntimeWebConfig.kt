package com.example.botconstructor.botapi.config

import com.example.botconstructor.botapi.api.RuntimeHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.config.CorsRegistry
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.function.server.router

@Configuration
@EnableWebFlux
@ComponentScan("com.example.botconstructor.botapi.api")
class RuntimeWebConfig : WebFluxConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
    }

    @Bean
    fun runtimeRoutes(runtimeHandler: RuntimeHandler) = router {
        "/api/runtime".nest {
            accept(MediaType.APPLICATION_JSON).nest {
                POST("/bots/{id}/sessions", runtimeHandler::startSession)
                POST("/sessions/{sessionId}/messages", runtimeHandler::handleMessage)
                // Public, token-guarded stateless flow invocation (no user JWT).
                POST("/webhooks/{token}", runtimeHandler::runWebhook)
            }
        }
    }
}
