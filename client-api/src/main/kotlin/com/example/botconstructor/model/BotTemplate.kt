package com.example.botconstructor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

data class Button(val id: Int, val text: String, val target: Int)

data class Edge(val id: Int, val target: Short, val source: Short) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Edge

        if (target != other.target) return false
        if (source != other.source) return false

        return true
    }

    override fun hashCode(): Int {
        val result = target.toInt();
        return (31 * result + source)
    }
}

data class Node(
        val id: Int,
        val textMessage: String,
        val inputCounts: Short,
        val outputCounts: Short,
        val hasButtons: Boolean,
        val buttons: List<Button>,
        val attachUrl: String
)

@Document
class BotTemplate(
        @Id val id: String,
        var name: String,
        val ownerId: String,
        var nodes: List<Node>,
        var edges: List<Edge>,
        var empty: Boolean
) {

    fun addNode(node: Node) {
        this.nodes += node;
    }

    fun addEdges(edge: Edge) {
        this.edges += edge;
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BotTemplate

        if (id != other.id) return false
        if (name != other.name) return false
        if (ownerId != other.ownerId) return false
        if (nodes != other.nodes) return false
        if (edges != other.edges) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + ownerId.hashCode()
        result = 31 * result + nodes.hashCode()
        result = 31 * result + edges.hashCode()
        return result
    }

    companion object {
        fun empty(name: String, ownerId: String): BotTemplate {
            return BotTemplate(
                    UUID.randomUUID().toString(),
                    name,
                    ownerId,
                    emptyList(),
                    emptyList(),
                    true
            )
        }
    }
}
