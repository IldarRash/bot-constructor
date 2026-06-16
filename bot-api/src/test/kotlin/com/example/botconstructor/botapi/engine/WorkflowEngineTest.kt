package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.FlowEdge
import com.example.botconstructor.botapi.model.dto.FlowNode
import com.example.botconstructor.botapi.model.dto.TraceStep
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

class WorkflowEngineTest {

    private val fallback = "Sorry, I did not understand"

    /** An [HttpCaller] that fails every test that unexpectedly makes an HTTP call. */
    private val noHttp = HttpCaller { _, _, _, _ ->
        throw AssertionError("no HTTP call expected in this test")
    }

    /** Runs the engine and blocks for the result (engine is now reactive). */
    private fun run(
            text: String,
            nodes: List<FlowNode>,
            edges: List<FlowEdge>,
            http: HttpCaller = noHttp,
            initialVars: Map<String, Any?> = emptyMap(),
    ): MatchResult = WorkflowEngine.run(text, nodes, edges, fallback, http, initialVars).block()!!

    /**
     * Builds a graph equivalent to the legacy keyword list:
     * trigger -> keyword(Greeting) --match--> sendMessage("Hello there!")
     *         -> keyword(Pricing)  --match--> sendMessage("It is free.")
     * Trigger edges are declared in question order so first-match-wins is stable.
     */
    private fun graph(): Pair<List<FlowNode>, List<FlowEdge>> {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("kwGreeting", "keyword", data = mapOf("label" to "Greeting", "keyWords" to listOf("hi", "hello"))),
                FlowNode("msgGreeting", "sendMessage", data = mapOf("text" to "Hello there!")),
                FlowNode("kwPricing", "keyword", data = mapOf("label" to "Pricing", "keyWords" to listOf("price", "cost"))),
                FlowNode("msgPricing", "sendMessage", data = mapOf("text" to "It is free.")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "kwGreeting"),
                FlowEdge("e2", "trigger", "kwPricing"),
                FlowEdge("e3", "kwGreeting", "msgGreeting", sourceHandle = "match"),
                FlowEdge("e4", "kwPricing", "msgPricing", sourceHandle = "match"),
        )
        return nodes to edges
    }

    @Test
    fun `matches a keyword node when a key word appears as a whole word`() {
        val (nodes, edges) = graph()
        val result = run("Hi, how are you?", nodes, edges)

        assertThat(result.reply).isEqualTo("Hello there!")
        assertThat(result.matched?.label).isEqualTo("Greeting")
    }

    @Test
    fun `matches case-insensitively`() {
        val (nodes, edges) = graph()
        val result = run("HELLO everyone", nodes, edges)

        assertThat(result.reply).isEqualTo("Hello there!")
        assertThat(result.matched?.label).isEqualTo("Greeting")
    }

    @Test
    fun `matches a key word as a substring of a longer word in the text`() {
        val (nodes, edges) = graph()
        // "cost" is a genuine substring of "costs"
        val result = run("what are the costs", nodes, edges)

        assertThat(result.reply).isEqualTo("It is free.")
        assertThat(result.matched?.label).isEqualTo("Pricing")
    }

    @Test
    fun `returns the first matching keyword in edge order`() {
        val (nodes, edges) = graph()
        val result = run("hi, what is the price?", nodes, edges)

        assertThat(result.reply).isEqualTo("Hello there!")
        assertThat(result.matched?.label).isEqualTo("Greeting")
    }

    @Test
    fun `falls back when no key word matches`() {
        val (nodes, edges) = graph()
        val result = run("totally unrelated message", nodes, edges)

        assertThat(result.reply).isEqualTo(fallback)
        assertThat(result.matched).isNull()
    }

    @Test
    fun `falls back when there is no trigger node`() {
        val result = run("hello", emptyList(), emptyList())

        assertThat(result.reply).isEqualTo(fallback)
        assertThat(result.matched).isNull()
    }

    @Test
    fun `follows the nomatch handle when a keyword does not match`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("kw", "keyword", data = mapOf("label" to "Greeting", "keyWords" to listOf("hi"))),
                FlowNode("msgNo", "sendMessage", data = mapOf("text" to "I did not match.")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "kw"),
                FlowEdge("e2", "kw", "msgNo", sourceHandle = "nomatch"),
        )
        val result = run("goodbye", nodes, edges)

        assertThat(result.reply).isEqualTo("I did not match.")
        assertThat(result.matched).isNull()
    }

    @Test
    fun `condition follows the true handle when the predicate holds`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("cond", "condition", data = mapOf("left" to "{{message}}", "op" to "contains", "right" to "yes")),
                FlowNode("msgTrue", "sendMessage", data = mapOf("text" to "Affirmative.")),
                FlowNode("msgFalse", "sendMessage", data = mapOf("text" to "Negative.")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "cond"),
                FlowEdge("e2", "cond", "msgTrue", sourceHandle = "true"),
                FlowEdge("e3", "cond", "msgFalse", sourceHandle = "false"),
        )
        val result = run("YES please", nodes, edges)

        assertThat(result.reply).isEqualTo("Affirmative.")
        assertThat(result.matched).isNull()
    }

    @Test
    fun `condition follows the false handle when the predicate fails`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("cond", "condition", data = mapOf("left" to "{{message}}", "op" to "eq", "right" to "ping")),
                FlowNode("msgTrue", "sendMessage", data = mapOf("text" to "pong")),
                FlowNode("msgFalse", "sendMessage", data = mapOf("text" to "not ping")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "cond"),
                FlowEdge("e2", "cond", "msgTrue", sourceHandle = "true"),
                FlowEdge("e3", "cond", "msgFalse", sourceHandle = "false"),
        )
        val result = run("pong", nodes, edges)

        assertThat(result.reply).isEqualTo("not ping")
    }

    @Test
    fun `condition does numeric compare for gt when both operands parse as numbers`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("cond", "condition", data = mapOf("left" to "{{message}}", "op" to "gt", "right" to "5")),
                FlowNode("msgTrue", "sendMessage", data = mapOf("text" to "big")),
                FlowNode("msgFalse", "sendMessage", data = mapOf("text" to "small")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "cond"),
                FlowEdge("e2", "cond", "msgTrue", sourceHandle = "true"),
                FlowEdge("e3", "cond", "msgFalse", sourceHandle = "false"),
        )
        // "10" > "5" numerically (lexicographically it would be false).
        val result = run("10", nodes, edges)

        assertThat(result.reply).isEqualTo("big")
    }

    @Test
    fun `setVariable feeds a later sendMessage via interpolation`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("set", "setVariable", data = mapOf("name" to "greeting", "value" to "Hi {{message}}")),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "{{greeting}}!")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "set"),
                FlowEdge("e2", "set", "msg"),
        )
        val result = run("Sam", nodes, edges)

        assertThat(result.reply).isEqualTo("Hi Sam!")
    }

    @Test
    fun `setVariable with a blank name skips the write but follows the default output`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("set", "setVariable", data = mapOf("name" to "  ", "value" to "ignored")),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "reached")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "set"),
                FlowEdge("e2", "set", "msg"),
        )
        val result = run("anything", nodes, edges)

        assertThat(result.reply).isEqualTo("reached")
    }

    @Test
    fun `interpolates a dotted path from a nested map var`() {
        val vars = mapOf<String, Any?>(
                "user" to mapOf("name" to "Ada", "address" to mapOf("city" to "London")),
        )

        assertThat(WorkflowEngine.interpolate("Hello {{ user.name }}!", vars)).isEqualTo("Hello Ada!")
        assertThat(WorkflowEngine.interpolate("{{user.address.city}}", vars)).isEqualTo("London")
    }

    @Test
    fun `interpolation trims whitespace and leaves non-expression text unchanged`() {
        val vars = mapOf<String, Any?>("name" to "Bob")

        assertThat(WorkflowEngine.interpolate("{{   name   }}", vars)).isEqualTo("Bob")
        assertThat(WorkflowEngine.interpolate("plain text, no braces", vars)).isEqualTo("plain text, no braces")
    }

    @Test
    fun `missing var interpolates to an empty string`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "[{{nope}}]")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "msg"),
        )
        val result = run("hi", nodes, edges)

        assertThat(result.reply).isEqualTo("[]")
    }

    @Test
    fun `initialVars seed the context and feed a later sendMessage via interpolation`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "Hi {{name}}!")),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "msg"))
        val result = run("anything", nodes, edges, initialVars = mapOf("name" to "Ada"))

        assertThat(result.reply).isEqualTo("Hi Ada!")
        assertThat(result.vars).containsEntry("name", "Ada")
    }

    @Test
    fun `initialVars cannot shadow the seeded message variable`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "{{message}}")),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "msg"))
        val result = run("real text", nodes, edges, initialVars = mapOf("message" to "spoofed"))

        assertThat(result.reply).isEqualTo("real text")
    }

    @Test
    fun `survives a cycle in the graph without hanging`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("a", "sendMessage", data = mapOf("text" to "A")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "a"),
                FlowEdge("e2", "a", "a"), // self-loop
        )
        val result = run("anything", nodes, edges)

        assertThat(result.reply).isEqualTo("A")
    }

    // --- httpRequest (Phase 2) ---------------------------------------------------------------

    @Test
    fun `httpRequest stores the parsed response then a later sendMessage interpolates a nested field`() {
        val captured = mutableListOf<Triple<String, String, String?>>()
        val http = HttpCaller { method, url, _, body ->
            captured += Triple(method, url, body)
            Mono.just(
                    HttpCallResult(
                            statusCode = 200,
                            body = mapOf("user" to mapOf("name" to "Ada")),
                            ok = true,
                    ),
            )
        }
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("http", "httpRequest", data = mapOf(
                        "method" to "GET",
                        "url" to "https://api.example.com/u/{{message}}",
                        "saveAs" to "resp",
                )),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "Hello {{resp.user.name}}!")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "http"),
                FlowEdge("e2", "http", "msg"),
        )
        val result = run("42", nodes, edges, http)

        assertThat(result.reply).isEqualTo("Hello Ada!")
        // url was interpolated against vars["message"].
        assertThat(captured).singleElement()
                .isEqualTo(Triple("GET", "https://api.example.com/u/42", null))
    }

    @Test
    fun `httpRequest interpolates headers and body`() {
        val captured = mutableListOf<Pair<Map<String, String>, String?>>()
        val http = HttpCaller { _, _, headers, body ->
            captured += headers to body
            Mono.just(HttpCallResult(200, emptyMap<String, Any?>(), true))
        }
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("http", "httpRequest", data = mapOf(
                        "method" to "POST",
                        "url" to "https://api.example.com",
                        "headers" to mapOf("X-Echo" to "{{message}}"),
                        "body" to "{\"q\":\"{{message}}\"}",
                        "saveAs" to "resp",
                )),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "done")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "http"),
                FlowEdge("e2", "http", "msg"),
        )
        run("ping", nodes, edges, http)

        assertThat(captured).singleElement()
                .isEqualTo(mapOf("X-Echo" to "ping") to "{\"q\":\"ping\"}")
    }

    @Test
    fun `httpRequest error response routes to the error handle`() {
        val http = HttpCaller { _, _, _, _ ->
            Mono.just(HttpCallResult(statusCode = 500, body = null, ok = false))
        }
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("http", "httpRequest", data = mapOf(
                        "method" to "GET",
                        "url" to "https://api.example.com",
                        "saveAs" to "resp",
                )),
                FlowNode("ok", "sendMessage", data = mapOf("text" to "ok branch")),
                FlowNode("err", "sendMessage", data = mapOf("text" to "error branch")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "http"),
                FlowEdge("e2", "http", "ok"),
                FlowEdge("e3", "http", "err", sourceHandle = "error"),
        )
        val result = run("hi", nodes, edges, http)

        assertThat(result.reply).isEqualTo("error branch")
    }

    @Test
    fun `httpRequest strips routing and hop-by-hop headers but keeps others`() {
        val captured = mutableListOf<Map<String, String>>()
        val http = HttpCaller { _, _, headers, _ ->
            captured += headers
            Mono.just(HttpCallResult(200, emptyMap<String, Any?>(), true))
        }
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("http", "httpRequest", data = mapOf(
                        "method" to "GET",
                        "url" to "https://api.example.com",
                        "headers" to mapOf(
                                "Host" to "evil.internal",
                                "content-length" to "999",
                                "Connection" to "keep-alive",
                                "Transfer-Encoding" to "chunked",
                                "X-Keep" to "yes",
                        ),
                        "saveAs" to "resp",
                )),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "done")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "http"),
                FlowEdge("e2", "http", "msg"),
        )
        run("ping", nodes, edges, http)

        assertThat(captured).singleElement().isEqualTo(mapOf("X-Keep" to "yes"))
    }

    @Test
    fun `httpRequest nodes beyond the per-walk cap are not dialed and route to error`() {
        var calls = 0
        val http = HttpCaller { _, _, _, _ ->
            calls++
            Mono.just(HttpCallResult(200, emptyMap<String, Any?>(), true))
        }
        // A chain of 7 httpRequest nodes; only the first 5 (MAX_HTTP_REQUESTS) should be dialed.
        val nodes = mutableListOf<FlowNode>(FlowNode("trigger", "trigger"))
        val edges = mutableListOf<FlowEdge>()
        var prev = "trigger"
        for (i in 1..7) {
            val id = "http$i"
            nodes += FlowNode(id, "httpRequest", data = mapOf(
                    "method" to "GET",
                    "url" to "https://api.example.com/$i",
                    "saveAs" to "r$i",
            ))
            edges += FlowEdge("e$i", prev, id)
            prev = id
        }
        nodes += FlowNode("end", "sendMessage", data = mapOf("text" to "reached end"))
        edges += FlowEdge("eEnd", prev, "end")

        val result = run("go", nodes, edges, http)

        assertThat(calls).isEqualTo(5)
        // The 6th+ nodes are treated as failed calls but still follow the default handle, so the
        // chain continues to the terminal sendMessage.
        assertThat(result.reply).isEqualTo("reached end")
    }

    // --- execution trace (Phase 3a) ----------------------------------------------------------

    @Test
    fun `records an ordered trace for trigger to keyword(match) to sendMessage`() {
        val (nodes, edges) = graph()
        val result = run("Hi, how are you?", nodes, edges)

        assertThat(result.trace).containsExactly(
                TraceStep("trigger", "trigger", null, "start"),
                TraceStep("kwGreeting", "keyword", "match", "matched: hi"),
                TraceStep("msgGreeting", "sendMessage", null, "Hello there!"),
        )
        // The losing sibling guard never executes once the turn is consumed.
        assertThat(result.trace.map { it.nodeId }).doesNotContain("kwPricing", "msgPricing")
        assertThat(result.vars).containsEntry("message", "Hi, how are you?")
    }

    @Test
    fun `keyword nomatch records the nomatch handle`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("kw", "keyword", data = mapOf("label" to "Greeting", "keyWords" to listOf("hi"))),
                FlowNode("msgNo", "sendMessage", data = mapOf("text" to "nope")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "kw"),
                FlowEdge("e2", "kw", "msgNo", sourceHandle = "nomatch"),
        )
        val result = run("goodbye", nodes, edges)

        assertThat(result.trace).contains(TraceStep("kw", "keyword", "nomatch", "no match"))
    }

    @Test
    fun `condition trace records the predicate detail and the true handle`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("cond", "condition", data = mapOf("left" to "{{message}}", "op" to "contains", "right" to "yes")),
                FlowNode("msgTrue", "sendMessage", data = mapOf("text" to "Affirmative.")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "cond"),
                FlowEdge("e2", "cond", "msgTrue", sourceHandle = "true"),
        )
        val result = run("YES please", nodes, edges)

        assertThat(result.trace).contains(
                TraceStep("cond", "condition", "true", "\"YES please\" contains \"yes\" -> true"),
        )
    }

    @Test
    fun `setVariable trace records the assignment and the resolved value as a final var`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("set", "setVariable", data = mapOf("name" to "greeting", "value" to "Hi {{message}}")),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "{{greeting}}!")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "set"),
                FlowEdge("e2", "set", "msg"),
        )
        val result = run("Sam", nodes, edges)

        assertThat(result.trace).contains(TraceStep("set", "setVariable", null, "greeting = Hi Sam"))
        assertThat(result.vars).containsEntry("greeting", "Hi Sam")
    }

    @Test
    fun `httpRequest trace records the verb url and status`() {
        val http = HttpCaller { _, _, _, _ ->
            Mono.just(HttpCallResult(statusCode = 200, body = mapOf("ok" to true), ok = true))
        }
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("http", "httpRequest", data = mapOf(
                        "method" to "GET",
                        "url" to "https://api.example.com/u/{{message}}",
                        "saveAs" to "resp",
                )),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "done")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "http"),
                FlowEdge("e2", "http", "msg"),
        )
        val result = run("42", nodes, edges, http)

        assertThat(result.trace).contains(
                TraceStep("http", "httpRequest", null, "GET https://api.example.com/u/42 -> 200"),
        )
    }

    @Test
    fun `httpRequest error trace records the error handle`() {
        val http = HttpCaller { _, _, _, _ ->
            Mono.just(HttpCallResult(statusCode = 500, body = null, ok = false))
        }
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("http", "httpRequest", data = mapOf("url" to "https://api.example.com", "saveAs" to "r")),
                FlowNode("err", "sendMessage", data = mapOf("text" to "error branch")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "http"),
                FlowEdge("e2", "http", "err", sourceHandle = "error"),
        )
        val result = run("hi", nodes, edges, http)

        assertThat(result.trace).contains(
                TraceStep("http", "httpRequest", "error", "GET https://api.example.com -> error"),
        )
    }

    @Test
    fun `httpRequest error falls back to the default handle when there is no error edge`() {
        val http = HttpCaller { _, _, _, _ ->
            Mono.just(HttpCallResult(statusCode = 0, body = null, ok = false))
        }
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("http", "httpRequest", data = mapOf(
                        "url" to "https://api.example.com",
                        "saveAs" to "resp",
                )),
                FlowNode("after", "sendMessage", data = mapOf("text" to "continued")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "http"),
                FlowEdge("e2", "http", "after"),
        )
        val result = run("hi", nodes, edges, http)

        assertThat(result.reply).isEqualTo("continued")
    }
}
