package com.example.botconstructor.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler

/**
 * RSocket messaging configuration for the board collaboration boundary.
 *
 * Spring Boot auto-configures an [RSocketStrategies] bean with CBOR (first) then JSON Jackson
 * codecs, plus an [RSocketMessageHandler]. Browser clients here speak JSON, so this replaces the
 * auto-configured message handler with one whose default data mime type is `application/json`,
 * while reusing the auto-configured strategies (so the Jackson JSON encoder/decoder are used).
 */
@Configuration
class RSocketConfig {

    @Bean
    fun rSocketMessageHandler(strategies: RSocketStrategies): RSocketMessageHandler =
            RSocketMessageHandler().apply {
                rSocketStrategies = strategies
                setDefaultDataMimeType(MediaType.APPLICATION_JSON)
            }
}
