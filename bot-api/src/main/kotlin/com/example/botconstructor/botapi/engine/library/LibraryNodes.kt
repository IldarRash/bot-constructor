package com.example.botconstructor.botapi.engine.library

import com.example.botconstructor.botapi.engine.NodeExecutor
import com.example.botconstructor.botapi.engine.PassThroughExecutor

/**
 * Extension seam for **library** node types (Phase C): the flow-control / data-shaping building
 * blocks (`if`, `switch`, `filter`, `set`, `splitOut`, `noop`, `wait`) that mirror the n8n core
 * nodes. Each library executor lives in its own file and registers here, so adding a library node
 * never edits `WorkflowEngine` — exactly like [com.example.botconstructor.botapi.engine.connectors.ConnectorNodes]
 * does for connectors.
 *
 * The engine merges this map alongside the connectors, under the built-ins (built-ins win on key
 * collision), keeping the core node types authoritative. None of these ids collide with a built-in
 * or a connector, so all seven register cleanly.
 *
 * Notes:
 *  - `noop` reuses the existing core [PassThroughExecutor] object (no dedicated library file): it
 *    passes input items through on the default output and records a single trace step.
 *  - `wait` is the only [com.example.botconstructor.botapi.engine.AsyncExecutor] here (it awaits a
 *    `Mono.delay`); the rest are synchronous and resolve inline in the drain loop.
 *  - `merge` ([MergeExecutor]) and `loop` ([LoopExecutor]) are the flow-control nodes that opt into
 *    the scheduler's gather / re-entry markers (`GatherExecutor` / `LoopExecutor`); they stay plain
 *    `SyncExecutor`s here and the additive scheduler gating drives their special wiring.
 */
object LibraryNodes {
    val executors: Map<String, NodeExecutor> = mapOf(
            "if" to IfExecutor,
            "switch" to SwitchExecutor,
            "filter" to FilterExecutor,
            "set" to SetFieldsExecutor,
            "splitOut" to SplitOutExecutor,
            "noop" to PassThroughExecutor,
            "wait" to WaitExecutor,
            "merge" to MergeExecutor,
            "loop" to LoopExecutor,
    )
}
