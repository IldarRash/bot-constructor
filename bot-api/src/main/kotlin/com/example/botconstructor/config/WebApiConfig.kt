package com.example.botconstructor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.lang.ref.Cleaner.create
import java.net.http.HttpClient

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
}


@Configuration
@ConfigurationProperties(prefix = "bot.api.telegram")
data class TelegramProps (val host: String, val timeout: Int)

@Configuration
@ConfigurationProperties(prefix = "bot.api.instagram")
data class InstagramProps (val host: String, val timeout: Int)
