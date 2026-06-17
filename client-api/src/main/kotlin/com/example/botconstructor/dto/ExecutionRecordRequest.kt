package com.example.botconstructor.dto

import java.time.Instant

/**
 * Internal, server-to-server POST body sent by bot-api after a flow run (the gateway 404s every
 * internal path, so this is never client-reachable). Carries no `ownerId`: ownership is resolved
 * in client-api from the bot document, never trusted from the runtime.
 *
 * Item payloads are pre-bounded by the runtime: at most 20 items per node per direction, with a
 * trailing truncation marker when more were produced, and they carry item json (data) only — the
 * node's config bag (connector tokens/keys) is never copied in.
 */
data class ExecutionRecordRequest(
        val botId: String,
        val status: String,
        val trigger: String,
        val startedAt: Instant,
        val finishedAt: Instant,
        val message: String,
        val reply: String,
        val nodes: List<ExecutionNodeRecord> = emptyList(),
)

data class ExecutionNodeRecord(
        val nodeId: String,
        val type: String,
        val handle: String? = null,
        val detail: String? = null,
        val inputItems: List<Map<String, Any?>> = emptyList(),
        val outputs: Map<String, List<Map<String, Any?>>> = emptyMap(),
        val error: String? = null,
)
