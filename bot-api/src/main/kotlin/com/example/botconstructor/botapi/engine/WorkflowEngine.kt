package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.engine.connectors.ConnectorNodes
import com.example.botconstructor.botapi.engine.library.LibraryNodes
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
 * @property trace The per-node execution trace, in execution order (the wire summary projection).
 * @property nodeTraces The rich per-node trace (input/output items per handle), in execution order;
 *   the source the execution-history record is built from. The [trace] field is a summary projection
 *   of these same steps, kept for existing callers/tests.
 * @property vars The final variable snapshot after the walk.
 */
data class MatchResult(
        val reply: String,
        val matched: MatchedSummary?,
        val trace: List<TraceStep>,
        val nodeTraces: List<NodeTrace>,
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
     * [ConnectorNodes.executors] and [LibraryNodes.executors]. Built-ins win on key collision (they
     * are layered last), so a connector or library node can never silently shadow a core node type.
     * New connectors / library nodes register in their own files via [ConnectorNodes] /
     * [LibraryNodes] without touching this class.
     */
    private val registry: Map<String, NodeExecutor> =
            ConnectorNodes.executors + LibraryNodes.executors + builtIns

    /**
     * Mutable scheduler state threaded through the reactive drain. [pending] holds each node's
     * not-yet-consumed input items; [visited] enforces run-once; [ready] is the FIFO work queue
     * (preserving edge order); [steps] is the cycle backstop.
     *
     * Three fields are additive gating for the two capability markers and stay untouched on a normal
     * walk: [forwardInDegree] / [arrived] drive Gather (Merge) enqueue-when-all-inputs-settled, and
     * [gatherExecuted] marks a Gather node as run-once across both the settled and finalization paths.
     * [topology] is precomputed once from (trigger, edges).
     */
    private class Scheduler(triggerId: String, val topology: Topology) {
        val ready: ArrayDeque<String> = ArrayDeque()
        val pending: MutableMap<String, MutableList<ExecutionItem>> = mutableMapOf()
        val visited: MutableSet<String> = mutableSetOf(triggerId)
        var steps: Int = 0

        /** Count of forward (non-back) in-edges of each Gather node that have already settled. */
        val arrived: MutableMap<String, Int> = mutableMapOf()

        /** Gather nodes that have already run (run-once across the settled + finalization paths). */
        val gatherExecuted: MutableSet<String> = mutableSetOf()

        val forwardInDegree: Map<String, Int> get() = topology.forwardInDegree
    }

    /**
     * Per-run graph classification precomputed once from (trigger, edges):
     *  - [backEdges] — edges whose target is on the DFS recursion stack from the trigger (loop
     *    feedback edges). These do NOT count toward forward in-degree and never gate a Gather node.
     *  - [forwardInDegree] — per node, the count of NON-back edges targeting it. A Gather (Merge) node
     *    runs once all that many forward inputs have settled.
     */
    private class Topology(
            val backEdges: Set<String>,
            val forwardInDegree: Map<String, Int>,
            val loopBody: Map<String, Set<String>>,
    )

    /**
     * Classifies back-edges by a single DFS from [triggerId] over [edges]: an edge whose target is
     * currently on the recursion stack is a back-edge (a loop feedback edge). [forwardInDegree] then
     * counts, per node, the non-back in-edges. Edges from nodes unreachable from the trigger keep
     * their default "forward" classification (they cannot form a cycle through the trigger anyway), so
     * their in-edges still count — matching the run-once reachability the normal path already relied on.
     */
    private fun classify(triggerId: String, edges: List<FlowEdge>): Topology {
        val outgoing: Map<String, List<FlowEdge>> = edges.groupBy { it.source }
        val backEdges = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val onStack = mutableSetOf<String>()

        // Iterative DFS (no recursion depth risk on large graphs): each frame walks one node's edges.
        data class Frame(val node: String, val iter: Iterator<FlowEdge>)
        fun dfs(start: String) {
            if (!visited.add(start)) return
            val stack = ArrayDeque<Frame>()
            onStack.add(start)
            stack.addLast(Frame(start, (outgoing[start] ?: emptyList()).iterator()))
            while (stack.isNotEmpty()) {
                val frame = stack.last()
                if (frame.iter.hasNext()) {
                    val edge = frame.iter.next()
                    val target = edge.target
                    when {
                        target in onStack -> backEdges.add(edge.id) // target on the stack => back-edge
                        visited.add(target) -> {
                            onStack.add(target)
                            stack.addLast(Frame(target, (outgoing[target] ?: emptyList()).iterator()))
                        }
                        // already fully visited, not on stack => cross/forward edge, nothing to do
                    }
                } else {
                    onStack.remove(frame.node)
                    stack.removeLast()
                }
            }
        }
        dfs(triggerId)

        val forwardInDegree = edges
                .filter { it.id !in backEdges }
                .groupingBy { it.target }
                .eachCount()
        return Topology(backEdges, forwardInDegree, loopBodies(edges, backEdges))
    }

    /**
     * For each back-edge `tail -> loopNode`, computes the loop's **body**: the nodes on a cycle from
     * `loopNode` back to `tail`, i.e. nodes both reachable from `loopNode` over forward edges and able
     * to reach `tail` over forward edges (the loop node itself excluded). When a [LoopExecutor] re-emits
     * a batch on its `loop` handle, the scheduler clears these body nodes' run-once state so they re-run
     * for the new batch. Keyed by the loop node id; multiple back-edges into one loop node union.
     */
    private fun loopBodies(edges: List<FlowEdge>, backEdges: Set<String>): Map<String, Set<String>> {
        val forward = edges.filter { it.id !in backEdges }
        val outAdj: Map<String, List<String>> = forward.groupBy({ it.source }, { it.target })
        val inAdj: Map<String, List<String>> = forward.groupBy({ it.target }, { it.source })

        fun reach(start: String, adj: Map<String, List<String>>): Set<String> {
            val seen = mutableSetOf<String>()
            val stack = ArrayDeque<String>().apply { addLast(start) }
            while (stack.isNotEmpty()) {
                val n = stack.removeLast()
                for (next in adj[n].orEmpty()) if (seen.add(next)) stack.addLast(next)
            }
            return seen
        }

        val result = mutableMapOf<String, MutableSet<String>>()
        for (edge in edges) {
            if (edge.id !in backEdges) continue
            val loopNode = edge.target
            val tail = edge.source
            // Body = reachable-from-loopNode ∩ can-reach-tail, plus the tail itself; loop node excluded.
            val body = (reach(loopNode, outAdj) intersect reach(tail, inAdj)).toMutableSet()
            body.add(tail)
            body.remove(loopNode)
            result.getOrPut(loopNode) { mutableSetOf() }.addAll(body)
        }
        return result
    }

    /**
     * Walks the graph reactively over [text] and resolves to the [MatchResult]. Synchronous nodes
     * complete inline; the lone async node (`httpRequest`) suspends the chain on [httpCaller] before
     * the walk resumes. [credentialResolver] resolves any node's `data.credentialId` into a decrypted
     * secret (default [CredentialResolver.NONE], so existing callers/tests are unchanged).
     *
     * [pinnedData] (manual-run dev loop only, default empty) maps a `node.id` to a fixed list of
     * output items: when the scheduler is about to run such a node it skips its executor entirely (no
     * HTTP/connector/code side effect) and emits those items on the node's default output, recording a
     * `pinned (N items)` trace. The empty default leaves every existing caller byte-identical.
     */
    fun run(
            text: String,
            nodes: List<FlowNode>,
            edges: List<FlowEdge>,
            fallbackAnswer: String,
            httpCaller: HttpCaller,
            initialVars: Map<String, Any?> = emptyMap(),
            credentialResolver: CredentialResolver = CredentialResolver.NONE,
            pinnedData: Map<String, List<ExecutionItem>> = emptyMap(),
    ): Mono<MatchResult> {
        val ctx = RunContext(text, initialVars, httpCaller, edges, credentialResolver, pinnedData)
        val nodesById = nodes.associateBy { it.id }
        val trigger = nodes.firstOrNull { it.type == "trigger" }
                ?: return Mono.just(result(ctx, fallbackAnswer))

        // The trigger is the entry point: it seeds one item and follows its default output. Record
        // it as the first trace step, then deliver its seed to its children.
        val seed = ExecutionItem(json = ctx.varsView.toMap())
        val triggerOut = NodeOutput.default(listOf(seed))
        ctx.trace.add(NodeTrace(trigger.id, trigger.type, null, "start", listOf(seed), triggerOut.buckets))

        val sched = Scheduler(trigger.id, classify(trigger.id, edges))
        deliver(trigger.id, triggerOut, sched, edges, nodesById)

        return drain(sched, ctx, nodesById, edges).map { result(ctx, fallbackAnswer) }
    }

    private fun result(ctx: RunContext, fallbackAnswer: String): MatchResult {
        val reply = if (ctx.replies.isNotEmpty()) ctx.replies.joinToString("\n") else fallbackAnswer
        return MatchResult(
                reply = reply,
                matched = ctx.firstMatch,
                trace = ctx.trace.map { TraceStep(it.nodeId, it.type, it.handle, it.detail) },
                nodeTraces = ctx.trace.toList(),
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
            if (sched.ready.isEmpty()) {
                // FINALIZATION (Gather only): a Merge node fed partly from a never-taken branch will
                // never reach full forward in-degree. Once the queue is otherwise empty, rescue one
                // un-executed gather node that did receive items and continue; repeat until none
                // remain. Pure additive — a graph with no gather nodes never enters this branch.
                if (!finalizeGather(sched, nodesById)) return DONE
            }
            if (sched.steps >= MAX_STEPS) return DONE
            sched.steps++
            val nodeId = sched.ready.removeFirst()
            val node = nodesById[nodeId] ?: continue
            // A prior keyword already won the turn; abandon remaining sibling guards.
            if (node.type == "keyword" && ctx.turnConsumed) continue

            // Pinned (manual-run dev loop): emit the fixed items on the default output WITHOUT running
            // the node's executor, so an httpRequest/connector/code node performs no outbound call or
            // side effect. Record a `pinned (N items)` trace and route exactly like a normal delivery.
            val pinned = ctx.pinnedData[nodeId]
            if (pinned != null) {
                val input = NodeInput(sched.pending.remove(nodeId) ?: emptyList())
                val out = NodeOutput.default(pinned)
                ctx.trace.add(NodeTrace(node.id, node.type, null, "pinned (${pinned.size} items)", input.items, out.buckets))
                deliver(nodeId, out, sched, edges, nodesById)
                continue
            }

            val exec = registry[node.type] ?: PassThroughExecutor
            // Gather (Merge): run at most once, over the entire accumulated inbox.
            if (exec is GatherExecutor && !sched.gatherExecuted.add(nodeId)) continue

            val input = NodeInput(sched.pending.remove(nodeId) ?: emptyList())
            when (exec) {
                is AsyncExecutor ->
                    return exec.run(node, input, ctx).flatMap { out ->
                        deliver(nodeId, out, sched, edges, nodesById)
                        drain(sched, ctx, nodesById, edges)
                    }
                is SyncExecutor -> {
                    val out = exec.run(node, input, ctx)
                    deliver(nodeId, out, sched, edges, nodesById)
                    // Continue draining synchronously (no IO boundary for this node).
                }
            }
        }
    }

    /**
     * Rescues a stranded Gather (Merge) node when the ready queue would otherwise drain: enqueues one
     * un-executed gather node that has buffered items (its forward in-degree can never complete because
     * some inputs sit on a branch never taken). Returns true if it enqueued one (so the drain loops),
     * false when none remain (the walk is genuinely done). No-op when there are no gather nodes.
     */
    private fun finalizeGather(sched: Scheduler, nodesById: Map<String, FlowNode>): Boolean {
        val candidate = sched.pending.keys.firstOrNull { id ->
            id !in sched.gatherExecuted &&
                    (sched.pending[id]?.isNotEmpty() == true) &&
                    registry[nodesById[id]?.type] is GatherExecutor
        } ?: return false
        sched.ready.addLast(candidate)
        return true
    }

    /** A pre-completed `Mono<Unit>` used as the synchronous terminator of the walk. */
    private val DONE: Mono<Unit> = Mono.just(Unit)

    /**
     * Routes a node's [output] to its outgoing edges, in edge-declaration order, gating each target by
     * its capability marker (the normal path is byte-identical to before):
     *
     *  1. **Normal** target (neither marker): take the bucket matching the edge's `sourceHandle`; an
     *     empty bucket delivers nothing (dead branch). Accumulate items into the inbox and enqueue the
     *     target the first time it receives items (run-once via [Scheduler.visited]); later deliveries
     *     accumulate but do not re-enqueue.
     *  2. **Gather** target ([GatherExecutor], Merge): a forward in-edge *settles* whenever its source
     *     executes — count it in [Scheduler.arrived] whether or not its bucket carried items. Append any
     *     non-empty items, but do NOT enqueue on first delivery; enqueue only once all forward inputs
     *     have settled (`arrived == forwardInDegree`). A back-edge into a gather node never settles it
     *     (loop feedback): it only appends items. Finalization rescues a gather still short of its
     *     in-degree because of a never-taken branch.
     *  3. **Loop** target ([LoopExecutor], Loop): exempt from run-once — every delivery (forward or via
     *     the feedback back-edge) appends items and re-enqueues the node, so it re-runs its batch state
     *     machine. [MAX_STEPS] plus the executor's own iteration cap bound the feedback.
     */
    private fun deliver(
            sourceId: String,
            output: NodeOutput,
            sched: Scheduler,
            edges: List<FlowEdge>,
            nodesById: Map<String, FlowNode>,
    ) {
        // Loop re-entry: when a loop node emits a fresh batch on its `loop` handle, clear the run-once
        // state of every node in its body so the body re-executes for this batch. No-op for the `done`
        // emission (empty `loop` bucket) and for every non-loop source.
        if (registry[nodesById[sourceId]?.type] is LoopExecutor &&
                output.buckets["loop"]?.isNotEmpty() == true) {
            for (bodyNode in sched.topology.loopBody[sourceId].orEmpty()) {
                sched.visited.remove(bodyNode)
                sched.gatherExecuted.remove(bodyNode)
                // Reset the gather accumulator too: without this a Merge in the loop body keeps its
                // previous batch's `arrived` count (== forwardInDegree), so the next batch's first
                // settling forward in-edge re-enqueues it immediately and it gathers a partial batch.
                sched.arrived.remove(bodyNode)
            }
        }
        for (edge in edges) {
            if (edge.source != sourceId) continue
            val targetExec = registry[nodesById[edge.target]?.type]
            val bucket = output.buckets[edge.sourceHandle].orEmpty()

            when {
                targetExec is GatherExecutor -> {
                    // Settle this forward in-edge (back-edges never settle a gather), carrying items.
                    if (bucket.isNotEmpty()) {
                        sched.pending.getOrPut(edge.target) { mutableListOf() }.addAll(bucket)
                    }
                    if (edge.id !in sched.topology.backEdges) {
                        val arrived = (sched.arrived[edge.target] ?: 0) + 1
                        sched.arrived[edge.target] = arrived
                        sched.visited.add(edge.target) // keep it out of the normal enqueue path
                        if (arrived >= (sched.forwardInDegree[edge.target] ?: 0) &&
                                edge.target !in sched.gatherExecuted) {
                            sched.ready.addLast(edge.target)
                        }
                    }
                }
                targetExec is LoopExecutor -> {
                    // Re-entrant: append items and always re-enqueue (run-once exempt).
                    if (bucket.isNotEmpty()) {
                        sched.pending.getOrPut(edge.target) { mutableListOf() }.addAll(bucket)
                    }
                    sched.visited.add(edge.target)
                    sched.ready.addLast(edge.target)
                }
                else -> {
                    // Normal target: unchanged run-once routing.
                    if (bucket.isEmpty()) continue
                    sched.pending.getOrPut(edge.target) { mutableListOf() }.addAll(bucket)
                    if (sched.visited.add(edge.target)) sched.ready.addLast(edge.target)
                }
            }
        }
    }

    /**
     * Resolves `{{ ... }}` expressions in [text] against [vars]. Delegates to [EngineFns.interpolate]
     * (kept here as a stable entry point the engine tests call directly).
     */
    internal fun interpolate(text: String, vars: Map<String, Any?>): String =
            EngineFns.interpolate(text, vars)
}
