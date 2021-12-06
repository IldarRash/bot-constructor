package com.example.botconstructor.config

import com.example.botconstructor.services.LocalServiceRegistry
import com.example.botconstructor.services.ServiceType
import io.rsocket.core.RSocketConnector
import io.rsocket.core.Resume
import io.rsocket.loadbalance.LoadbalanceTarget
import io.rsocket.loadbalance.RoundRobinLoadbalanceStrategy
import io.rsocket.transport.netty.client.WebsocketClientTransport
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.rsocket.RSocketRequester
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.net.URI
import java.time.Duration

@Configuration
class RSocketDiscoveryConfig {

    fun transportsViaServiceDiscovery(
        localService: LocalServiceRegistry,
        type: ServiceType
    ) = Flux
            .interval(Duration.ZERO, Duration.ofMillis(3000))
            .onBackpressureDrop()
            .filter{
                localService.isUpdate.getAndSet(false)
            }
            .concatMap(
                {
                    Mono.just(localService
                        .getInstants(type)
                        .stream()
                        .map {
                            LoadbalanceTarget.from(
                                it.id,
                                WebsocketClientTransport.create(URI.create("ws://" + it.host + ":" + it.port + "/rsocket"))
                            )
                        }
                        .toList())
                },
                1
            )

    @Bean
    fun clientApiRequester(
        rsocketRequesterBuilder: RSocketRequester.Builder,
        localService: LocalServiceRegistry
    ) = rsocketRequesterBuilder
            .rsocketConnector { connector: RSocketConnector ->
                connector
                    .lease()
                    .reconnect(
                        Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(100))
                            .maxBackoff(Duration.ofSeconds(5))
                    )
            }
            .transports(transportsViaServiceDiscovery(localService, ServiceType.CLIENT_API), RoundRobinLoadbalanceStrategy())
}
