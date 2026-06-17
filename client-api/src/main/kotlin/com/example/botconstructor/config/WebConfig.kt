package com.example.botconstructor.config

import com.example.botconstructor.api.BotHandler
import com.example.botconstructor.api.CredentialHandler
import com.example.botconstructor.api.ExecutionHandler
import com.example.botconstructor.api.UserHandler
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
@ComponentScan("com.example.botconstructor.api")
class WebConfig : WebFluxConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
    }

    @Bean
    fun route(
            userHandler: UserHandler,
            botHandler: BotHandler,
            executionHandler: ExecutionHandler,
            credentialHandler: CredentialHandler,
    ) = router {
        "/api".nest {
            accept(MediaType.APPLICATION_JSON).nest {
                GET("/user", userHandler::getCurrentUser)
                POST("/users", userHandler::signup)
                POST("/users/login", userHandler::login)
                PUT("/user", userHandler::updateUser)

                POST("/bots", botHandler::createBot)
                GET("/bots", botHandler::listBots)
                GET("/bots/{id}", botHandler::getBot)
                PUT("/bots/{id}", botHandler::updateBot)
                DELETE("/bots/{id}", botHandler::deleteBot)

                // Owner-scoped execution history for one bot (summaries, newest-first, paged) and the
                // full detail of a single run. Both are JWT-authenticated and owner-scoped.
                GET("/bots/{id}/executions", executionHandler::listExecutions)
                GET("/executions/{execId}", executionHandler::getExecution)

                // Owner-scoped, JWT-authenticated credential CRUD. Responses are metadata only —
                // secrets are encrypted on write and never returned here.
                POST("/credentials", credentialHandler::createCredential)
                GET("/credentials", credentialHandler::listCredentials)
                GET("/credentials/{id}", credentialHandler::getCredential)
                PUT("/credentials/{id}", credentialHandler::updateCredential)
                DELETE("/credentials/{id}", credentialHandler::deleteCredential)

                // Internal, unauthenticated server-to-server lookup for bot-api: resolve a bot by
                // its high-entropy webhook token. The token is the secret (no ownership check);
                // permitAll for this path is configured in SecurityConfig.
                GET("/internal/bots/by-webhook/{token}", botHandler::getBotByWebhookToken)

                // Internal, unauthenticated server-to-server write from bot-api after each flow run.
                // The gateway 404s all /api/internal/**, so it is server-to-server only; permitAll
                // for this exact POST path is configured in SecurityConfig. The owner is resolved
                // from the bot document, never trusted from the body.
                POST("/internal/executions", executionHandler::recordExecution)

                // Internal, unauthenticated server-to-server fetch for bot-api: resolve a credential
                // to its DECRYPTED secret, but only when the credential and the bot (botId query
                // param) share an owner — otherwise an opaque 404 (the anti-IDOR boundary). The
                // gateway 404s all internal paths; permitAll for this GET is set in SecurityConfig.
                GET("/internal/credentials/{credId}", credentialHandler::resolveCredential)
            }
        }
    }
}
