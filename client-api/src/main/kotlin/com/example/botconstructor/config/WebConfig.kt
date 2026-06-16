package com.example.botconstructor.config

import com.example.botconstructor.api.BotHandler
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
    fun route(userHandler: UserHandler, botHandler: BotHandler) = router {
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

                // Internal, unauthenticated server-to-server lookup for bot-api: resolve a bot by
                // its high-entropy webhook token. The token is the secret (no ownership check);
                // permitAll for this path is configured in SecurityConfig.
                GET("/internal/bots/by-webhook/{token}", botHandler::getBotByWebhookToken)
            }
        }
    }
}
