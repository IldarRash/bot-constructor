package com.example.botconstructor.model

import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

enum class IntegrationType {
    INSTAGRAM, TELEGRAM, VK
}
class Integration(val id: UUID, val token: String, val integrationType: IntegrationType)

class IntegratedBot(val id: UUID, val integrations: List<Integration>, val botTemId: String)

@Document
class Project (val id: String, val name: String, val groudId: String, var integrationList: List<Integration>, var bots: List<IntegratedBot>) {
    fun addIntegraion(integration: Integration) {
        this.integrationList += integration;
    }

    fun addBot(integratedBot: IntegratedBot) {
        this.bots += integratedBot;
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Project

        if (id != other.id) return false
        if (integrationList != other.integrationList) return false
        if (bots != other.bots) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + integrationList.hashCode()
        result = 31 * result + bots.hashCode()
        return result
    }


    companion object {
        fun empty(name: String, groudId: String): Project {
            return Project(
                UUID.randomUUID().toString(),
                name,
                groudId,
                emptyList(),
                emptyList()
            )
        }
    }
}
