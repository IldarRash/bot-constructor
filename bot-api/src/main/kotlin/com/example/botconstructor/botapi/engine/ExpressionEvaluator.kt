package com.example.botconstructor.botapi.engine

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.ResourceLimits
import org.graalvm.polyglot.Value
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Spring-free, **sandboxed** GraalVM JS evaluator for inline expressions (`{{= ... }}`) and the Code
 * node. Security is the point: the guest JS context has zero host reach — no host classes, no IO, no
 * native access, no threads/processes — and a per-evaluation statement limit so a runaway loop aborts
 * instead of pinning a thread. Only plain data (Map/List/String/Number/Boolean/null) crosses the
 * boundary in either direction; host objects are never exposed to the guest, and guest values are
 * deep-copied into Kotlin data before they leave [evalToString]/[evalItems].
 *
 * GraalVM [Context]s are single-thread-at-a-time and expensive to build, so each thread reuses its
 * own [Context] via a [ThreadLocal]; a context is never shared concurrently. Neither entry point ever
 * throws: any guest error (syntax, runtime, statement-limit) degrades to a safe fallback (`""` for
 * inline expressions, the input items unchanged for the Code node) so the engine keeps walking and
 * the caller can route `error`.
 */
object ExpressionEvaluator {

    /** Aborts a single evaluation after this many guest statements (runaway-loop backstop). */
    private const val STATEMENT_LIMIT: Long = 100_000

    /**
     * Wall-clock ceiling per evaluation. [STATEMENT_LIMIT] only bounds statement *count*; this bounds
     * *time* (heavy-but-finite work: big allocations, regex backtracking) by interrupting the guest
     * from a watchdog thread. The engine's 20s walk timeout cannot do this — a `.timeout()` only fires
     * when control returns to the reactor and can't interrupt a thread spinning inside `eval`.
     */
    private val EVAL_TIMEOUT: Duration = Duration.ofSeconds(2)

    /**
     * Single shared GraalVM [Engine] so guest code/AST is cached across the per-thread contexts and
     * native memory stays bounded (rather than one full engine per thread).
     */
    private val sharedEngine: Engine = Engine.newBuilder("js")
            .option("engine.WarnInterpreterOnly", "false")
            .build()

    /** Daemon watchdog that interrupts evaluations exceeding [EVAL_TIMEOUT]. */
    private val watchdog: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "expr-eval-watchdog").apply { isDaemon = true }
            }

    /**
     * Per-thread, reused JS context built off the shared [sharedEngine]. Locked down: no host access,
     * no host class lookup, no IO, no native/thread creation. The [ResourceLimits] bound applies
     * per-context but is reset between evaluations by [Context.resetLimits], so one expression's
     * statement count never leaks into the next. A context is never shared across threads concurrently.
     */
    private val contexts: ThreadLocal<Context> = ThreadLocal.withInitial {
        val limits = ResourceLimits.newBuilder()
                .statementLimit(STATEMENT_LIMIT, null)
                .build()
        Context.newBuilder("js")
                .engine(sharedEngine)
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup { false }
                .allowAllAccess(false)
                .allowIO(org.graalvm.polyglot.io.IOAccess.NONE)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .allowPolyglotAccess(org.graalvm.polyglot.PolyglotAccess.NONE)
                .allowEnvironmentAccess(org.graalvm.polyglot.EnvironmentAccess.NONE)
                .resourceLimits(limits)
                .build()
    }

    /**
     * Evaluates an inline expression [js] against [bindings] and returns the result's string form, or
     * `""` on ANY error (the call never throws). Each top-level key of [bindings] is bound as a JS
     * global; `$vars` and `$json` expose the whole map (the latter is the chat-view alias), and `$now`
     * is the current ISO-8601 instant. The completion value is coerced: `null`/`undefined` → `""`,
     * everything else → its JS string form.
     */
    fun evalToString(js: String, bindings: Map<String, Any?>): String =
            evaluate { ctx ->
                val global = ctx.getBindings("js")
                bindings.forEach { (k, v) -> global.putMember(k, toGuest(v)) }
                global.putMember("\$vars", toGuest(bindings))
                global.putMember("\$json", toGuest(bindings))
                global.putMember("\$now", Instant.now().toString())
                val result = ctx.eval("js", js)
                if (result.isNull) "" else result.toString()
            } ?: ""

    /**
     * Runs the Code node body [js] over [items] (bound as `$items`) with `$vars` available, and
     * coerces the result into a `List<Map<String,Any?>>`. The body may return an array of objects or
     * mutate/return `$items`; the completion value wins, falling back to `$items` when it is not an
     * array. On ANY error the original [items] are returned unchanged (the call never throws) so the
     * caller can route `error`.
     */
    fun evalItems(
            js: String,
            items: List<Map<String, Any?>>,
            vars: Map<String, Any?>,
    ): List<Map<String, Any?>> =
            evaluate { ctx ->
                val global = ctx.getBindings("js")
                global.putMember("\$items", toGuest(items))
                global.putMember("\$vars", toGuest(vars))
                global.putMember("\$now", Instant.now().toString())
                val result = ctx.eval("js", js)
                // The completion value wins when it is an array; otherwise fall back to the (possibly
                // mutated) $items binding, re-read as a guest Value so both paths share one decoder.
                val array = if (result.hasArrayElements()) result else global.getMember("\$items")
                fromGuestArrayOfMaps(array)
            } ?: items

    /**
     * Borrows this thread's [Context], resets its statement budget, arms the [watchdog] to interrupt a
     * run that overruns [EVAL_TIMEOUT], runs [block], and on any failure (syntax/runtime error,
     * statement-limit, or watchdog interrupt) returns null. Centralizes the "never throw, always reset
     * limits, always bounded in time" contract both entry points depend on. The context survives an
     * interrupt and is reused; only data ever crosses the boundary.
     */
    private fun <T> evaluate(block: (Context) -> T): T? {
        val ctx = contexts.get()
        // Interrupt from a different thread (required by GraalVM) if this eval overruns its budget.
        val alarm = watchdog.schedule(
                { runCatching { ctx.interrupt(Duration.ofSeconds(2)) } },
                EVAL_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS,
        )
        return try {
            ctx.resetLimits()
            block(ctx)
        } catch (_: Throwable) {
            null
        } finally {
            alarm.cancel(false)
        }
    }

    // --- guest <-> host conversion (data only; host objects never cross the boundary) ------------

    /**
     * Converts a Kotlin value into a guest-safe value. Maps and lists become [ProxyObject]/[ProxyArray]
     * over deep-copied data so the guest can read structured bindings without ever touching a host
     * object; scalars (String/Number/Boolean/null) pass through as GraalVM-supported primitives.
     */
    private fun toGuest(value: Any?): Any? = when (value) {
        null -> null
        is Map<*, *> -> org.graalvm.polyglot.proxy.ProxyObject.fromMap(
                value.entries.associate { (k, v) -> k.toString() to toGuest(v) },
        )
        is List<*> -> org.graalvm.polyglot.proxy.ProxyArray.fromList(value.map { toGuest(it) })
        is String, is Boolean, is Number -> value
        else -> value.toString()
    }

    /** Coerces a guest array of objects into a list of plain Kotlin maps, dropping non-object rows. */
    @Suppress("UNCHECKED_CAST")
    private fun fromGuestArrayOfMaps(array: Value): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>()
        val size = array.arraySize
        for (i in 0 until size) {
            val row = fromGuest(array.getArrayElement(i))
            if (row is Map<*, *>) out.add(row as Map<String, Any?>)
        }
        return out
    }

    /**
     * Recursively converts a guest [Value] into plain Kotlin data (Map/List/String/Number/Boolean/
     * null). Only data shapes are converted; host-object handles, functions, etc. degrade to their
     * string form rather than leaking out of the sandbox.
     */
    private fun fromGuest(value: Value): Any? = when {
        value.isNull -> null
        value.isBoolean -> value.asBoolean()
        value.isString -> value.asString()
        value.isNumber -> if (value.fitsInLong()) value.asLong() else value.asDouble()
        value.hasArrayElements() -> (0 until value.arraySize).map { fromGuest(value.getArrayElement(it)) }
        value.hasMembers() -> value.memberKeys.associateWith { fromGuest(value.getMember(it)) }
        else -> value.toString()
    }
}
