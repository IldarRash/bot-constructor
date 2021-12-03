package com.example.botconstructor.config

import com.example.botconstructor.api.InstagramWebhookHandler
import com.example.botconstructor.api.TelegramWebhookHandler
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.router

@Configuration
class WebApiConfig(val telegramProps: TelegramProps, val instagramProps: InstagramProps) {

    @Bean
    fun telegramClient() =
        WebClient.builder()
            .baseUrl(telegramProps.host)
            .build()

    @Bean
    fun instagramClient() =
        WebClient.builder()
            .baseUrl(instagramProps.host)
            .build()


    @Bean
    fun routes(
        instagramWebhookHandler: InstagramWebhookHandler,
        telegramWebhookHandler: TelegramWebhookHandler
    ) = router {
        "/api".nest {
            "instagram".nest {
                accept(MediaType.APPLICATION_JSON).nest {
                    POST("/webhooks", instagramWebhookHandler::webHook)
                }
                GET("/webhooks", instagramWebhookHandler::subscribe)
            }
            "telegram".nest {
                accept(MediaType.APPLICATION_JSON).nest {
                    POST("/webhooks", telegramWebhookHandler::webHook)
                }
            }
        }
    }


}


@ConstructorBinding
@ConfigurationProperties(prefix = "bot.api.telegram")
data class TelegramProps(
    val host: String, val timeout: Int)

@ConstructorBinding
@ConfigurationProperties(prefix = "bot.api.instagram")
data class InstagramProps(val host: String, val timeout: Int)
