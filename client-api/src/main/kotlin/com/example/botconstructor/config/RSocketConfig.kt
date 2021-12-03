package com.example.botconstructor.config

import io.rsocket.RSocket
import io.rsocket.core.RSocketConnector
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.netty.client.WebsocketClientTransport
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.messaging.rsocket.RSocketRequester
import reactor.core.publisher.SignalType
import reactor.util.retry.Retry
import reactor.util.retry.RetryBackoffSpec
import java.net.URI
import java.time.Duration
import java.util.*


@Configuration
class RSocketConfig {

    @Bean
    @Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    fun requester(
        rSocketRequesterBuilder: RSocketRequester.Builder
    ) : RSocketRequester? {
        val host: String = "localhost"
        val port: Int = 9003
        val path: String = "/rsocket"
        return createRSocketRequester(
            rSocketRequesterBuilder,
            WebsocketClientTransport.create(
                URI.create(String.format("ws://%s:%s%s", host, port, path))
            )
        )
    }

    fun createRSocketRequester(
        rSocketRequesterBuilder: RSocketRequester.Builder,
        clientTransport: ClientTransport
    ): RSocketRequester? {
        val clientId = String.format("%s.%s", "client-api", UUID.randomUUID())
        val retryBackoffSpec: RetryBackoffSpec = Retry.fixedDelay(120, Duration.ofSeconds(1))
            .doBeforeRetry { retrySignal -> println("Reconnecting... ${retrySignal}") }
        val rSocketRequester = rSocketRequesterBuilder
            .setupRoute("bot.authorize.connect")
            .setupData(clientId)
            .rsocketConnector { connector: RSocketConnector -> connector.reconnect(retryBackoffSpec) }
            .transport(clientTransport)
        rSocketRequester.rsocketClient()
            .source()
            .flatMap<Any>(RSocket::onClose)
            .doOnError { error: Throwable? -> println("Connection CLOSED") }
            .doFinally { consumer: SignalType? -> println("DISCONNECTED") }
            .subscribe()
        return rSocketRequester
    }

}
