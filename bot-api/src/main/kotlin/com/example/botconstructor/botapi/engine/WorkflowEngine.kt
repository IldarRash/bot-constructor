package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.engine.connectors.ConnectorNodes
import com.example.botconstructor.botapi.model.dto.FlowEdge
import com.example.botconstructor.botapi.model.dto.FlowNode
import com.example.botconstructor.botapi.model.dto.TraceStep
import reactor.core.publisher.Mono

/**
 * The keyword node that fired during a walk.
 *
 * @property label The fired keyword node's label (or its id when no label is set).
 */
data class MatchedSummary(
        val label: String,
)

/**
 * Outcome of walking the graph over a user message.
 *
 * @property reply The answer text to return (joined replies, or the fallback when empty).
 * @property matched The keyword node that fired, or null when the fallback answer was used.
 * @property trace The per-node execution trace, in execution order.
 * @property vars The final variable snapshot after the walk.
 */
data class MatchResult(
        val reply: String,
        val matched: MatchedSummary?,
        val trace: List<TraceStep>,
        val vars: Map<String, Any?>,
)

/**
 * Spring-free, reactive, **item-based** graph-walking engine (n8n model).
 *
 * Data flows node→node as lists of [ExecutionItem]s. Starting from the single `trigger` node — which
 * seeds one item from the user text and initial vars — the engine schedules reachable nodes over a
 * work queue: each node consumes the items delivered to its inbox, runs its [NodeExecutor], and
 * routes the output buckets to outgoing edges by `sourceHandle`. Branch nodes (`keyword`,
 * `condition`, `httpRequest`) emit named buckets so only matching edges carry items; pure nodes emit
 * the default bucket.
 *
 * Each node executes at most once per walk (a visited set), which both bounds cycles and preserves
 * the legacy single-pass semantics; the [MAX_STEPS] cap is a second backstop. Only `httpRequest`
 * introduces an actual `Mono` boundary — pure executors resolve synchronously inside the drain loop.
 * The first keyword that fires consumes the turn (sibling keyword guards are skipped) and is reported
 * as the match; the joined replies form the answer, or [fallbackAnswer] if none.
 *
 * Loop/merge node types (Phase C) build on this same item contract by opting into re-entry and
 * multi-input gathering; the built-in node types here keep the run-once behavior.
 */
object WorkflowEngine {

    private const val MAX_STEPS = 100

    /** The core node types this module ships. `trigger` is handled inline as the entry point. */
    private val builtIns: Map<String, NodeExecutor> = mapOf(
            "keyword" to KeywordExecutor,
            "sendMessage" to SendMessageExecutor,
            "setVariable" to SetVariableExecutor,
            "condition" to ConditionExecutor,
            "httpRequest" to HttpRequestExecutor,
            "code" to CodeNodeExecutor,
    )

    /**
     * Registry of node executors by [FlowNode.type]: the [builtIns] plus any registered in
     * [ConnectorNodes.executors]. Built-ins win on key collision (they are layered last), so a
     * connector can never silently shadow a core node type. New connectors register in their own
     * files via [ConnectorNodes] without touching this class.
     */
    private val registry: Map<String, NodeExecutor> = ConnectorNodes.executors + builtIns

    /**
     * Mutable scheduler state threaded through the reactive drain. [pending] holds each node's
     * not-yet-consumed input items; [visited] enforces run-once; [ready] is the FIFO work queue
     * (preserving edge order); [steps] is the cycle backstop.
     */
    private class Scheduler(triggerId: String) {
        val ready: ArrayDeque<String> = ArrayDeque()
        val pending: MutableMap<String, MutableList<ExecutionItem>> = mutableMapOf()
        val visited: MutableSet<String> = mutableSetOf(triggerId)
        var steps: Int = 0
    }

    /**
     * Walks the graph reactively over [text] and resolves to the [MatchResult]. Synchronous nodes
     * complete inline; the lone async node (`httpRequest`) suspends the chain on [httpCaller] before
     * the walk resumes.
     */
    fun run(
            text: String,
            nodes: List<FlowNode>,
            edges: List<FlowEdge>,
            fallbackAnswer: String,
            httpCaller: HttpCaller,
            initialVars: Map<String, Any?> = emptyMap(),
    ): Mono<MatchResult> {
        val ctx = RunContext(text, initialVars, httpCaller, edges)
        val nodesById = nodes.associateBy { it.id }
        val trigger = nodes.firstOrNull { it.type == "trigger" }
                ?: return Mono.just(result(ctx, fallbackAnswer))

        // The trigger is the entry point: it seeds one item and follows its default output. Record
        // it as the first trace step, then deliver its seed to its children.
        val seed = ExecutionItem(json = ctx.varsView.toMap())
        val triggerOut = NodeOutput.default(listOf(seed))
        ctx.trace.add(NodeTrace(trigger.id, trigger.type, null, "start", listOf(seed), triggerOut.buckets))

        val sched = Scheduler(trigger.id)
        deliver(trigger.id, triggerOut, sched, edges)

        return drain(sched, ctx, nodesById, edges).map { result(ctx, fallbackAnswer) }
    }

    private fun result(ctx: RunContext, fallbackAnswer: String): MatchResult {
        val reply = if (ctx.replies.isNotEmpty()) ctx.replies.joinToString("\n") else fallbackAnswer
        return MatchResult(
                reply = reply,
                matched = ctx.firstMatch,
                trace = ctx.trace.map { TraceStep(it.nodeId, it.type, it.handle, it.detail) },
                vars = ctx.varsView.toMap(),
        )
    }

    /**
     * Drains the ready queue. Pure executors run inline in the loop; `httpRequest` returns the
     * recursion wrapped in [Mono.flatMap] so the walk only continues after the call resolves.
     * Returns when the queue drains or the step cap hits.
     */
    private fun drain(
            sched: Scheduler,
            ctx: RunContext,
            nodesById: Map<String, FlowNode>,
            edges: List<FlowEdge>,
    ): Mono<Unit> {
        while (true) {
            if (sched.ready.isEmpty() || sched.steps >= MAX_STEPS) return DONE
            sched.steps++
            val nodeId = sched.ready.removeFirst()
            val node = nodesById[nodeId] ?: continue
            // A prior keyword already won the turn; abandon remaining sibling guards.
            if (node.type == "keyword" && ctx.turnConsumed) continue

            val input = NodeInput(sched.pending.remove(nodeId) ?: emptyList())
            when (val exec = registry[node.type] ?: PassThroughExecutor) {
                is AsyncExecutor ->
                    return exec.run(node, input, ctx).flatMap { out ->
                        deliver(nodeId, out, sched, edges)
                        drain(sched, ctx, nodesById, edges)
                    }
                is SyncExecutor -> {
                    val out = exec.run(node, input, ctx)
                    deliver(nodeId, out, sched, edges)
                    // Continue draining synchronously (no IO boundary for this node).
                }
            }
        }
    }

    /** A pre-completed `Mono<Unit>` used as the synchronous terminator of the walk. */
    private val DONE: Mono<Unit> = Mono.just(Unit)

    /**
     * Routes a node's [output] to its outgoing edges, in edge-declaration order. Each edge takes the
     * bucket matching its `sourceHandle`; empty buckets deliver nothing (dead branches). A target is
     * enqueued the first time it receives items and runs once (run-once visited semantics); later
     * deliveries still accumulate into its inbox but do not re-enqueue it.
     */
    private fun deliver(sourceId: String, output: NodeOutput, sched: Scheduler, edges: List<FlowEdge>) {
        for (edge in edges) {
            if (edge.source != sourceId) continue
            val bucket = output.buckets[edge.sourceHandle] ?: continue
            if (bucket.isEmpty()) continue
            sched.pending.getOrPut(edge.target) { mutableListOf() }.addAll(bucket)
            if (sched.visited.add(edge.target)) sched.ready.addLast(edge.target)
        }
    }

    /**
     * Resolves `{{ ... }}` expressions in [text] against [vars]. Delegates to [EngineFns.interpolate]
     * (kept here as a stable entry point the engine tests call directly).
     */
    internal fun interpolate(text: String, vars: Map<String, Any?>): String =
            EngineFns.interpolate(text, vars)
}
