package com.example.botconstructor.bot_template.support

import io.rsocket.RSocket
import org.springframework.boot.autoconfigure.rsocket.RSocketMessageHandlerCustomizer
import org.springframework.boot.rsocket.server.RSocketServerCustomizer
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import org.springframework.stereotype.Component


@Component
class AssociateTemplateRSocketServerCustomizer : RSocketServerCustomizer {

    override fun customize(rSocketServer: io.rsocket.core.RSocketServer?) {
        rSocketServer?.interceptors { it.forRequester(createRSocket()) }
    }

    fun createRSocket(): (RSocket) -> RSocket {
        return { BotTemplateAwareRSocket(it) }
    }
}


@Component
class BotTemplateRSocketMessageHandlerCustomizer : RSocketMessageHandlerCustomizer {

    override fun customize(messageHandler: RSocketMessageHandler?) {
        messageHandler?.argumentResolverConfigurer?.addCustomResolver( AssociatedTemplateMethodArgumentResolver())
    }
}
