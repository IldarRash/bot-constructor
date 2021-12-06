package com.example.botconstructor.services

import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring5.SpringTemplateEngine
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType

@Service
class AnswerTemplateService(val messageTemplateEngine: SpringTemplateEngine) {

    /*fun processAnswer(botEvent: BotEvent): String {
        val context = Context()
        processVariableFromAnswer(botEvent)
                .forEach { (t, u) ->
                    context.setVariable(t, u)
                }

        return messageTemplateEngine.process("", context)
    }

    protected fun processVariableFromAnswer(botEvent: BotEvent): Map<String, JvmType.Object> {
        return mapOf()
    }*/
}
