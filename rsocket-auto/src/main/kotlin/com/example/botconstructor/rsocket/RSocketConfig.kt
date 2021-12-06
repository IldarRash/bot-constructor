package com.example.botconstructor.rsocket

import com.example.botconstructor.services.Service
import com.example.botconstructor.services.ServiceType
import io.rsocket.RSocket
import io.rsocket.core.RSocketConnector
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.netty.client.WebsocketClientTransport
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.rsocket.RSocketRequester
import reactor.core.publisher.SignalType
import reactor.util.retry.Retry
import reactor.util.retry.RetryBackoffSpec
import java.net.InetAddress
import java.net.URI
import java.time.Duration
import java.util.*


@Configuration
class RSocketConfig {
    @Value("\${server.port}")
    lateinit var port: String

    @Value("\${spring.application.id}")
    lateinit var id: String

    @Bean
    fun requester(
        rSocketProps: RSocketProps,
        rSocketRequesterBuilder: RSocketRequester.Builder
    ) : RSocketRequester? {
        return createRSocketRequester(
            rSocketRequesterBuilder,
            WebsocketClientTransport.create(
                URI.create(String.format("ws://%s:%s%s", rSocketProps.host, rSocketProps.port, rSocketProps.rpath))
            ),
            Service(id, InetAddress.getLocalHost().getHostName(), port.toInt(), rSocketProps.type),
            rSocketProps.path
        )
    }

    fun createRSocketRequester(
        rSocketRequesterBuilder: RSocketRequester.Builder,
        clientTransport: ClientTransport,
        service: Service,
        path: String
    ): RSocketRequester? {
        val retryBackoffSpec: RetryBackoffSpec = Retry.fixedDelay(120, Duration.ofSeconds(1))
            .doBeforeRetry { retrySignal -> println("Reconnecting... $retrySignal") }
        val rSocketRequester = rSocketRequesterBuilder
            .setupRoute("${path}.connect")
            .setupData(service)
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

@ConstructorBinding
@ConfigurationProperties(prefix = "rsocket")
data class RSocketProps(
    val host: String, val port: Int, val type: ServiceType, val rpath: String, val path: String)
