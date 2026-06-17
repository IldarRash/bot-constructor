package com.example.botconstructor.dto

import com.example.botconstructor.model.Execution
import com.example.botconstructor.model.ExecutionNode
import java.time.Instant

/**
 * Lightweight list view of an execution (no heavy item payloads), returned newest-first to the owner.
 *
 * @property nodeCount Number of node traces, so the UI can show run size without shipping the items.
 */
data class ExecutionSummaryView(
        val id: String,
        val botId: String,
        val status: String,
        val trigger: String,
        val startedAt: Instant,
        val finishedAt: Instant,
        val message: String,
        val reply: String,
        val nodeCount: Int,
)

/**
 * Full execution detail, including each node's input/output items, returned to the owner only.
 */
data class ExecutionView(
        val id: String,
        val botId: String,
        val status: String,
        val trigger: String,
        val startedAt: Instant,
        val finishedAt: Instant,
        val message: String,
        val reply: String,
        val nodes: List<ExecutionNodeView>,
)

data class ExecutionNodeView(
        val nodeId: String,
        val type: String,
        val handle: String?,
        val detail: String?,
        val inputItems: List<Map<String, Any?>>,
        val outputs: Map<String, List<Map<String, Any?>>>,
        val error: String?,
)

fun Execution.toSummaryView() = ExecutionSummaryView(
        id = id,
        botId = botId,
        status = status,
        trigger = trigger,
        startedAt = startedAt,
        finishedAt = finishedAt,
        message = message,
        reply = reply,
        nodeCount = nodes.size,
)

fun Execution.toView() = ExecutionView(
        id = id,
        botId = botId,
        status = status,
        trigger = trigger,
        startedAt = startedAt,
        finishedAt = finishedAt,
        message = message,
        reply = reply,
        nodes = nodes.map { it.toView() },
)

fun ExecutionNode.toView() = ExecutionNodeView(
        nodeId = nodeId,
        type = type,
        handle = handle,
        detail = detail,
        inputItems = inputItems,
        outputs = outputs,
        error = error,
)
