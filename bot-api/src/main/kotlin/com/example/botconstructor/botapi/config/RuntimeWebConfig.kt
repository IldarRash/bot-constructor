package com.example.botconstructor.botapi.config

import com.example.botconstructor.botapi.api.RuntimeHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

    /**
     * Jackson 2 [ObjectMapper] for the runtime (execution-record serialization in [RuntimeService]).
     * Spring Boot 4 auto-configures a Jackson 3 mapper (`tools.jackson.*`), so the legacy
     * `com.fasterxml.jackson.databind.ObjectMapper` injected by RuntimeService is not otherwise a bean.
     */
    @Bean
    fun objectMapper(): ObjectMapper = jacksonObjectMapper()

    @Bean
    fun runtimeRoutes(runtimeHandler: RuntimeHandler) = router {
        "/api/runtime".nest {
            accept(MediaType.APPLICATION_JSON).nest {
                POST("/bots/{id}/sessions", runtimeHandler::startSession)
                // On-demand owner-scoped editor run of the saved flow (rich per-node data + pinned data).
                POST("/bots/{id}/execute", runtimeHandler::runManual)
                POST("/sessions/{sessionId}/messages", runtimeHandler::handleMessage)
                // Public, token-guarded stateless flow invocation (no user JWT).
                POST("/webhooks/{token}", runtimeHandler::runWebhook)
            }
        }
    }
}
