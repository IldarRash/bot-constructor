package com.example.botconstructor.services

import com.example.botconstructor.dto.EmptyEvent
import com.example.botconstructor.dto.ErrorEvent
import com.example.botconstructor.dto.Event
import com.example.botconstructor.dto.EventType
import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.repositories.BotTemplateRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

@Service
class BotTemplateService(
        val botTemplateRepository: BotTemplateRepository,
        val validators: Map<EventType, Validator<Event>>,
        val editors: Map<EventType, Editor<Event>>
) {
    val mapUsersTemplateActive = ConcurrentHashMap<String, BotTemplate>()

    fun newTemplate(name: String): Mono<BotTemplate> {

        val emptyTemplate = BotTemplate.empty(name, "ildar")
        mapUsersTemplateActive.put(emptyTemplate.id, emptyTemplate)

        return Mono.just(emptyTemplate)
    }


    fun editTemplate(events: Flux<Event>, id: String): Flux<Event> {
        if (!mapUsersTemplateActive.contains(id))
            return Flux.fromIterable(
                    listOf(ErrorEvent(id, "Template doesnt fount", EventType.ERROR))
            )

        val template: BotTemplate = mapUsersTemplateActive[id]!!
        return events
                .map { validators[it.getType()]?.validateEvent(it, template) }
                .map { editors[it!!.first.getType()]?.editTemplate(it, template) }
                .map {
                    if (it!!.second.valid) {
                        EmptyEvent()
                    } else {
                        ErrorEvent(template.id, it.second.message, it.first.getType())
                    }
                }
                .doOnTerminate {
                    botTemplateRepository.save(template).subscribe()
                }

    }
}
