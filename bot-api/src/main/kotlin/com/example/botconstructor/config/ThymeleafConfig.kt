package com.example.botconstructor.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.thymeleaf.spring5.SpringTemplateEngine
import org.thymeleaf.spring5.templateresolver.SpringResourceTemplateResolver
import java.util.*


@Configuration
class ThymeleafConfig {

    @Bean
    fun jsonTemplateResolver(): SpringResourceTemplateResolver {
        val resourceTemplateResolver = SpringResourceTemplateResolver()
        resourceTemplateResolver.prefix = "classpath:/templates/"
        resourceTemplateResolver.resolvablePatterns = Collections.singleton("json/*")
        resourceTemplateResolver.suffix = ".json"
        resourceTemplateResolver.characterEncoding = "UTF-8"
        resourceTemplateResolver.isCacheable = false
        resourceTemplateResolver.order = 2
        return resourceTemplateResolver
    }

    @Bean
    fun messageTemplateEngine() : SpringTemplateEngine {
        val templateEngine = SpringTemplateEngine()
        templateEngine.addTemplateResolver(jsonTemplateResolver())
        return templateEngine
    }
}
