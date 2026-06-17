package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.FlowNode
import reactor.core.publisher.Mono

/**
 * Executes one node type over its input items, producing an [NodeOutput] and appending its
 * [NodeTrace] to the [RunContext]. Split into a synchronous and an asynchronous variant so the
 * engine preserves its "pure nodes resolve inline, only IO suspends the chain" guarantee without any
 * blocking: [SyncExecutor]s run in the scheduler's drain loop, while [AsyncExecutor]s (just
 * `httpRequest`) introduce a single `Mono` boundary the walk awaits.
 */
sealed interface NodeExecutor

/** A pure, non-IO node: resolves synchronously inside the reactive chain. */
fun interface SyncExecutor : NodeExecutor {
    fun run(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput
}

/** An IO node (`httpRequest`): the walk awaits its [Mono] before resuming. */
fun interface AsyncExecutor : NodeExecutor {
    fun run(node: FlowNode, input: NodeInput, ctx: RunContext): Mono<NodeOutput>
}

/**
 * Capability marker (NOT part of the sealed [NodeExecutor] hierarchy): a node that **waits for all of
 * its forward inputs before running once over the gathered items** (Merge). An executor opts in by
 * implementing both [SyncExecutor] and this marker; the scheduler then withholds its enqueue until
 * every non-back forward in-edge has settled (or finalization rescues a dead branch), and runs it once
 * over the full accumulated inbox. Inert for any executor that does not implement it.
 */
interface GatherExecutor

/**
 * Capability marker (NOT part of the sealed [NodeExecutor] hierarchy): a **re-entrant** node that may
 * run multiple times within one walk (Loop / Split-in-Batches). An executor opts in by implementing
 * both [SyncExecutor] and this marker; the scheduler then exempts it from run-once, so each delivery —
 * including via a feedback back-edge — re-enqueues it. Per-node iteration state lives in
 * [RunContext.loopState]. Inert for any executor that does not implement it.
 */
interface LoopExecutor

/** Appends a [NodeTrace] for a node that just produced [output] from [input]. */
internal fun traceStep(
        ctx: RunContext,
        node: FlowNode,
        handle: String?,
        detail: String?,
        input: NodeInput,
        output: NodeOutput,
) {
    ctx.trace.add(NodeTrace(node.id, node.type, handle, detail, input.items, output.buckets))
}

/**
 * Builds the standard `true`/`false` branch [NodeOutput] (both buckets always emitted so each branch's
 * edges route independently) together with the primary trace handle — `"true"` when any item went the
 * true way, else `"false"`. Shared by every two-way branch node (`condition`, `if`) so the partition →
 * emit → handle contract lives in one place.
 */
internal fun branchOutput(
        trueItems: List<ExecutionItem>,
        falseItems: List<ExecutionItem>,
): Pair<NodeOutput, String> {
    val output = NodeOutput(mapOf("true" to trueItems, "false" to falseItems))
    val handle = if (trueItems.isNotEmpty()) "true" else "false"
    return output to handle
}
