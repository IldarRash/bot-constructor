package com.example.botconstructor.bot_template.support

import com.example.botconstructor.model.BotTemplate
import io.jsonwebtoken.lang.Assert
import io.rsocket.RSocket
import io.rsocket.util.RSocketProxy
import org.springframework.core.MethodParameter
import org.springframework.messaging.Message
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodArgumentResolver
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.annotation.support.RSocketRequesterMethodArgumentResolver.RSOCKET_REQUESTER_HEADER
import reactor.core.publisher.Mono


class BotTemplateAwareRSocket( source: RSocket) : RSocketProxy(source) {
    lateinit var botTemplate: BotTemplate
}


class AssociatedTemplateMethodArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        val type: Class<*> = parameter.getParameterType()
        return BotTemplate::class.java == type || BotTemplate::class.java.isAssignableFrom(type)
    }

    override fun resolveArgument(parameter: MethodParameter, message: Message<*>): Mono<Any> {
        val headerValue: Any? = message.getHeaders().get(RSOCKET_REQUESTER_HEADER)
        Assert.notNull(headerValue, "Missing '" + RSOCKET_REQUESTER_HEADER + "'")
        Assert.isInstanceOf(RSocketRequester::class.java, headerValue, "Expected header value of type RSocketRequester")
        val requester = headerValue as RSocketRequester
        val rsocket = requester.rsocket()
        return if (rsocket is BotTemplateAwareRSocket) {
            Mono.just((rsocket as BotTemplateAwareRSocket?)!!.botTemplate)
        } else Mono.error(IllegalArgumentException("Unexpected parameter type: $parameter"))
    }
}
