package com.example.botconstructor.api

import com.example.botconstructor.model.Bot
import com.example.botconstructor.repositories.BotAnswerRepositories
import com.example.botconstructor.services.AnswerTemplateService
import org.springframework.http.MediaType
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.stereotype.Controller
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.io.File

@Controller
@MessageMapping("bot.authorize")
class BotController(
        val telegramClient: WebClient,
        val instagramClient: WebClient,
        val answerTemplateService: AnswerTemplateService,
        val botAnswerRepositories: BotAnswerRepositories
) {

    @MessageMapping("telegram")
    fun telegramAuthorize(@Payload token: String, requester: RSocketRequester): Mono<String> {
        return telegramClient.get()
                .uri {
                    it.path("bot$token/getMe").build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToMono {
                    if (it.statusCode().is2xxSuccessful) {
                        botAnswerRepositories.register(Bot(token, requester))
                        return@exchangeToMono bodyExtractTelegram(it)
                    }
                    requester.dispose()
                    return@exchangeToMono bodyExtractTelegram(it)
                }
    }

    fun bodyExtractTelegram(clientResponse: ClientResponse): Mono<String> =
            clientResponse.bodyToMono(String::class.java)


}
