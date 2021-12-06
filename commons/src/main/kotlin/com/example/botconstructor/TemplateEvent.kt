package com.example.botconstructor

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo


enum class TemplateType {
    TELEGRAM, INSTAGRAM, VK, WHATSAPP, FACEBOOK
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = TelegramTemplate::class, name = "telegram"),
    JsonSubTypes.Type(value = InstagramTemplate::class, name = "instagram"),
)
interface Template<T> {

    fun getAuth(): T

    @JsonIgnore
    fun getType(): TemplateType
}


data class TelegramTemplate(val token: String) : Template<String> {
    override fun getAuth(): String = token

    override fun getType(): TemplateType = TemplateType.TELEGRAM

}


data class InstagramTemplate(val token: String) : Template<String> {
    override fun getAuth(): String = token

    override fun getType(): TemplateType = TemplateType.INSTAGRAM

}
