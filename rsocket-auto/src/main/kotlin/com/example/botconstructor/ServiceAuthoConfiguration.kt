package com.example.botconstructor

import com.example.botconstructor.rsocket.RSocketConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ConditionalOnProperty(prefix= "rsocket", name = ["host", "port", "path", "type"])
@Import(value = [RSocketConfig::class])
@ConfigurationPropertiesScan
class ServiceAutoConfiguration {
}
