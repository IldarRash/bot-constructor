package com.example.botconstructor.botapi.engine.library

import com.example.botconstructor.botapi.engine.AsyncExecutor
import com.example.botconstructor.botapi.engine.EngineFns
import com.example.botconstructor.botapi.engine.ExecutionItem
import com.example.botconstructor.botapi.engine.NodeInput
import com.example.botconstructor.botapi.engine.NodeOutput
import com.example.botconstructor.botapi.engine.RunContext
import com.example.botconstructor.botapi.engine.traceStep
import com.example.botconstructor.botapi.model.dto.FlowNode
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * `wait` action — the non-blocking delay node. Interpolates `data.seconds` against the first input
 * item, parses it as a [Double], and pauses the walk for that long before passing its items straight
 * through on the default output.
 *
 * It is an [AsyncExecutor] so the engine awaits a single `Mono` boundary rather than blocking a
 * scheduler thread: the pause is realised with [Mono.delay], which schedules a deferred completion on
 * the default parallel scheduler instead of sleeping. The interpolated duration is **clamped to
 * `[0.0, 15.0]`s** so a single (or NaN/garbage) value cannot dominate the 20s walk timeout. The clamp
 * is **per node, not per walk**: a graph with several `wait` nodes can still sum past the 20s
 * `RuntimeService` walk timeout (which then returns the fallback reply) — bounding cumulative delay is
 * left to that overall timeout. A single default output; never throws.
 */
object WaitExecutor : AsyncExecutor {

    /** Lower bound of the clamp; a negative/NaN `seconds` becomes an immediate pass-through. */
    private const val MIN_SECONDS: Double = 0.0

    /** Upper bound of the clamp; kept safely under the engine's 20s walk timeout. */
    private const val MAX_SECONDS: Double = 15.0

    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): Mono<NodeOutput> {
        val item = input.items.firstOrNull() ?: ExecutionItem(ctx.varsView.toMap())
        val raw = EngineFns.interpolate(node.data["seconds"] as? String ?: "", ctx.itemVars(item))
        // coerceIn also collapses NaN to MIN_SECONDS, so garbage input degrades to no wait.
        val seconds = (raw.toDoubleOrNull() ?: MIN_SECONDS).coerceIn(MIN_SECONDS, MAX_SECONDS)

        val output = NodeOutput.default(input.items)
        // Record the step before the delay resolves so it appears in execution order, then emit the
        // unchanged items once the scheduled completion fires.
        return Mono.delay(Duration.ofMillis((seconds * 1000).toLong()))
                .doFirst { traceStep(ctx, node, null, "waited ${seconds}s", input, output) }
                .thenReturn(output)
    }
}
