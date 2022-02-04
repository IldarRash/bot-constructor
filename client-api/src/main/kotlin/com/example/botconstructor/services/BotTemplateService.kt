package com.example.botconstructor.services

import com.example.botconstructor.BroadCast
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
    val listeners = ConcurrentHashMap<String, BroadCast<Event>>()

    fun newTemplate(name: String): Mono<BotTemplate> {

        val emptyTemplate = BotTemplate.empty(name, "ildar")
        mapUsersTemplateActive[emptyTemplate.id] = emptyTemplate
        listeners[emptyTemplate.id] = BroadCast()
        return Mono.just(emptyTemplate)
    }


    fun editTemplate(events: Flux<Event>, id: String): Flux<Event> {
        if (!mapUsersTemplateActive.containsKey(id))
            return Flux.fromIterable(
                    listOf(ErrorEvent(id, "Template doesnt fount"))
            )

        if (!listeners.containsKey(id))
            listeners[id] = BroadCast()

        val template: BotTemplate = mapUsersTemplateActive[id]!!
        return listeners[id]!!.listener<Event>().mergeWith(events)
            .map { validators[it.getType()]?.validateEvent(it, template) }
            .map { editors[it!!.first.getType()]?.editTemplate(it, template) }
            .map {
                if (it!!.second.valid) {
                    EmptyEvent()
                } else {
                    ErrorEvent(template.id, it.second.message)
                }
            }
            .doOnTerminate {
                botTemplateRepository.save(template).subscribe()
            }

    }
}
