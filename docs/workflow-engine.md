# Workflow Engine — n8n-style flows for Bot Constructor

This document is the **authoritative shared contract** for the workflow-automation feature. Every
layer (client-api persistence/DTOs, bot-api runtime, client-ui editor) MUST agree on the JSON shapes
below. Subagents implementing any slice should treat this file as the source of truth and update it
if the contract changes.

## Motivation

n8n is *node-based workflow automation*: a typed-node graph where a **trigger** starts execution,
data flows node→node as JSON, and nodes branch (If/Switch), call HTTP, transform data (Set), or run
code. The bot today is a flat keyword list with a linear first-match engine and a cosmetic flow
editor (edges carry no meaning). This feature turns it into a real typed-node graph with a
graph-walking runtime and variables passed between nodes.

## Canonical graph shape (JSON)

This mirrors React Flow's native node/edge shape, so the editor sends/receives it verbatim.

```jsonc
{
  "nodes": [
    { "id": "n1", "type": "trigger",     "position": {"x":0,"y":0},   "data": {} },
    { "id": "n2", "type": "keyword",     "position": {"x":0,"y":120}, "data": {"label":"greeting","keyWords":["hi","hello"]} },
    { "id": "n3", "type": "sendMessage", "position": {"x":0,"y":240}, "data": {"text":"Hello {{user.name}}!"} }
  ],
  "edges": [
    { "id": "e1", "source": "n1", "target": "n2", "sourceHandle": null },
    { "id": "e2", "source": "n2", "target": "n3", "sourceHandle": "match" }
  ]
}
```

- `FlowNode.data` is an open `Map<String, Any?>` (per-type config bag). New node types add new `data`
  keys without schema migrations — this is what the `new-flow-node` skill scaffolds.
- `FlowEdge.sourceHandle` selects which output of a multi-output node the edge leaves from
  (e.g. `"match"`/`"nomatch"` for keyword, `"true"`/`"false"` for condition). `null` = default output.

## Node type registry

| `type`        | Phase | `data` shape                                              | Runtime behavior |
|---------------|-------|-----------------------------------------------------------|------------------|
| `trigger`     | 0     | `{}`                                                      | Entry point. Exactly one per flow. Execution starts here on each user message. |
| `keyword`     | 0     | `{label?, keyWords: string[]}`                            | Guard. Matches if any keyword is in the user text (token or substring, case-insensitive — same rule as legacy `BotEngine`). On match, follow `match` handle; else `nomatch` handle. |
| `sendMessage` | 0     | `{text: string}`                                          | Action. Appends `text` (after expression interpolation) to the reply buffer. Follow default output. |
| `condition`   | 1     | `{left: string, op: "eq"\|"neq"\|"contains"\|"gt"\|"lt", right: string}` | Branch. Evaluates `left op right` (both expression-interpolated). Follow `true`/`false` handle. |
| `setVariable` | 1     | `{name: string, value: string}`                          | Sets `vars[name] = interpolate(value)`. Follow default output. |
| `httpRequest` | 2     | `{method, url, headers?: object, body?: string, saveAs: string}` | Calls `url` (interpolated; `method`, `body`, and each header value are interpolated too). Stores the parsed JSON (Map/List) or text response into `vars[saveAs]`, reachable as `{{saveAs.field}}`. On a 2xx response follows the default (`null`) output; on a non-2xx / blocked / timed-out / failed call follows the `error` handle when an edge uses it, else the default output. Never crashes the walk. |
| `code`        | F     | `{code: string}`                                         | Runs guest JavaScript over the input items (`$items` = input item array, `$vars` = variables); the returned array becomes the emitted items. Follows the default (`null`) output normally; on any guest error it passes the original items through and follows the `error` handle when an edge uses it, else the default. Never crashes the walk. |
| `telegramSend`     | C | `{botToken, chatId, text, saveAs: string}`           | Connector. Posts to the Telegram Bot API `sendMessage`. Stores the parsed response plus `ok`/`messageId` convenience keys into `vars[saveAs]` (default `telegram`), reachable as `{{saveAs.ok}}` / `{{saveAs.messageId}}`. `chatId`/`text` are interpolated. 2xx → default output; otherwise `error` (else default). |
| `anthropicMessage` | C | `{apiKey, model, maxTokens, prompt, saveAs: string}` | Connector. Calls the Anthropic Messages API (Claude). Stores `{ text, raw }` into `vars[saveAs]` (default `ai`); read the reply as `{{saveAs.text}}`. `prompt` is interpolated; `maxTokens` is sent as a JSON int (defaults to 1024). 2xx → default output; otherwise `error` (else default). |
| `slackSend`        | C | `{webhookUrl, text, saveAs: string}`                 | Connector. Posts `text` to a Slack Incoming Webhook. Stores `{ ok: true }` into `vars[saveAs]` (default `slack`) on a 2xx. `webhookUrl`/`text` are interpolated. 2xx → default output; otherwise `error` (else default). |
| `discordSend`      | C | `{webhookUrl, content, saveAs: string}`              | Connector. Posts `{ content }` to a Discord webhook (returns 204). Stores `{ ok: true }` into `vars[saveAs]` (default `discord`) on a 2xx. `webhookUrl`/`content` are interpolated. 2xx → default output; otherwise `error` (else default). |
| `if`        | C | `{left, op, right, combine?: "and"\|"or", left2?, op2?, right2?}` | Library. Branch. Evaluates `left op right` (operands interpolated); when `left2`/`op2`/`right2` are **all** non-blank, a 2nd clause is folded in with `combine` (`or`→`\|\|`, else `and`). Both `true` and `false` buckets are always emitted so each branch routes independently. Never throws (unknown ops → false). |
| `switch`    | C | `{value, case0, case1}`                              | Library. Value router. Resolves `value` against the first input item and routes by string equality: `== case0` → handle `0`, `== case1` → handle `1`, else the **default (`null`) output**. Only the chosen lane carries the items. Never throws (unresolvable expr falls through to default). |
| `filter`    | C | `{left, op, right}`                                  | Library. Keeps only items where `left op right` holds (per-item interpolation); dropped items are discarded (no second handle). Kept items leave on the default (`null`) output. Detail `filter kept N/total`. Unknown ops drop everything. |
| `set`       | C | `{assignments: string}`                             | Library. Newline-separated `name=value` block (split on the **first** `=`, names trimmed, blank/invalid lines dropped). Interpolates each value and writes `item.json[name]` and `vars[name]`; later lines can reference earlier ones. Default (`null`) output. Detail `set N field(s)`. |
| `splitOut`  | C | `{field: string}`                                   | Library. **Multiplies items** — for each input item, resolves the array at the dotted path `field` and fans out one output item per element (Map element merges its keys onto the item; scalar element stored under `<field>Item`). A missing/non-array value passes the item through unchanged. Default (`null`) output. Detail `split <field> -> N items`. |
| `noop`      | C | `{}`                                                | Library. Passes input items straight through on the default (`null`) output. Reuses the core `PassThroughExecutor`. No config — a join/label point in the graph. |
| `wait`      | C | `{seconds: string}`                                 | Library. Async. Interpolates `seconds` (against the first item), parses it and **clamps to 0–15s** (unparseable/NaN → 0), then `Mono.delay`s before passing items through unchanged on the default (`null`) output. The 15s ceiling stays under the 20s walk timeout. Detail `waited Xs`. |
| `merge`     | C | `{mode: "append"}`                                  | Library. Fan-in join (gather / wait-all). Several edges may converge on its single input; the scheduler holds it back until every forward input has settled, then runs it **once** over the full accumulated inbox. `mode` is only `append` for now: concatenates the gathered items in arrival order and emits them on the default (`null`) output. Detail `merge N items`. |
| `loop`      | C | `{batchSize: string}`                               | Library. Split-in-Batches. Exempt from run-once: each delivery re-runs it. Two outputs — `loop` (the current batch) and `done` (the full input items, after all batches). First entry stashes the items and a queue of `batchSize`-sized batches (clamped `>= 1`, default 1) and emits the first batch on `loop`; each feedback re-entry emits the next batch, then `done` once exhausted. Capped at 1000 batches per node. Detail `loop batch k/m` or `loop done`. |

### Library nodes (Phase C)

`if`, `switch`, `filter`, `set`, `splitOut`, `noop`, `wait`, `merge`, and `loop` are the
**flow-control / data-shaping** building blocks (n8n-style core nodes), registered via
`engine/library/LibraryNodes.kt` — the same extension seam pattern as connectors, so they never edit
`WorkflowEngine`. All branch/default outputs follow the standard handle contract (`if` uses
`true`/`false`; `switch` uses `0`/`1` + the `null` default; `loop` uses `loop`/`done`; the rest are
default-only). Notable: **`splitOut` multiplies the item list** (downstream nodes then run once per
element on the existing run-once contract), and **`wait` clamps its delay to 15s** so a single wait
can never trip the 20s walk timeout.

**`merge` is the fan-in gather (wait-all).** Unlike a plain join that would pop after its first
arriving input, `merge` implements the scheduler's gather contract: it is held back until every
forward input edge has settled, then runs exactly once over the full accumulated inbox and emits the
concatenated items on its default output. This is the "gather all converging branches before
continuing" point in a graph.

**`loop` is the feedback-edge re-entry pattern (Split-in-Batches).** It is exempt from run-once, so
it can run multiple times in one walk. Wire it as: `loop` output goes to your per-batch processing
nodes, and an edge runs from the end of that processing chain back to the `loop` node — each such
re-entry advances to the next batch. When the batch queue is exhausted, the node emits the full input
items on its `done` output instead, which continues the rest of the flow. The handle ids `loop` and
`done` are the engine's output bucket keys; an editor handle whose id does not match silently carries
nothing. A per-node 1000-batch cap (under the engine's `MAX_STEPS` backstop) keeps a misconfigured
feedback edge from spinning forever.

**Connectors share the `httpRequest` HTTP path.** `telegramSend`, `anthropicMessage`, `slackSend`,
and `discordSend` delegate every outbound call to `ConnectorSupport.postJson`, so they go through the
same SSRF-hardened `HttpCaller` (`WebClientHttpCaller`) and count against the per-walk
`MAX_HTTP_REQUESTS = 5` cap as `httpRequest` (a connector call past the cap is not dialed and routes
`error`/default). They inherit denied-header stripping, 2xx-vs-`error` routing, and the trace step.

**Secrets move out of the node (Phase E).** A connector / `httpRequest` node can still carry its
secret inline (masked password inputs, stored in `FlowNode.data`) as the fallback, but the preferred
path is a reusable, encrypted **credential** referenced by `data.credentialId` — see
[Credentials (Phase E)](#credentials-phase-e). When a `credentialId` is set the resolved secret
overrides the inline field; the inline value is only used when no credential is referenced.


**Triggers are bot-level, not node types.** Beyond the single in-graph `trigger` (reached on each
user message / webhook call), a bot carries two optional bot-level trigger mechanisms that run the
**same** flow from its `trigger`:
- **webhook** — every bot has an unguessable `webhookToken`; an inbound `POST /api/runtime/webhooks/{token}`
  runs the flow (see [Webhook invocation](#webhook-invocation-phase-3b)).
- **schedule** — an optional bot-level `schedule` cron string runs the flow on a cadence (see
  [Schedule trigger](#schedule-cron-trigger-phase-3c)). Implemented by invoking the webhook path.

## Execution model (bot-api) — item-based (n8n model)

Data flows node→node as **lists of items** (`ExecutionItem{ json: Map<String,Any?>, binary? }`), the
n8n model. A node receives the items delivered to its inbox, runs its `NodeExecutor`, and emits a
`NodeOutput` — a map of **output handle → items** (the `null` key is the default output; named keys
are branch handles like `match`/`true`/`error`). This is what makes map/filter/branch/merge/loop
expressible on a single contract.

Per incoming user message, `WorkflowEngine.run(...)` walks from the single `trigger` node:

1. Build a `RunContext` (holds the SSRF `httpCaller`, the graph `edges`, the reply buffer, the
   trace, and a chat-compat `varsView`). `varsView` is seeded from `initialVars` then
   `message = userText` (so `message` can't be shadowed).
2. The `trigger` seeds **one item** whose `json` is the initial `varsView`, and follows its default
   output.
3. A **work-queue scheduler** drains ready nodes (FIFO, preserving edge order): each node consumes
   its gathered input items, its executor produces output buckets, and the scheduler routes each
   outgoing edge by its `sourceHandle`. Only edges whose handle has a **non-empty** bucket carry
   items — dead branches receive nothing (this replaces "follow only the matching handle").
4. **Run-once semantics:** a node executes at most once per walk (a visited set). A node reached from
   several edges runs a single time over the **gathered** items (so e.g. `sendMessage` emits one
   reply per item). This both preserves the legacy single-pass behavior and bounds cycles; a
   `MAX_STEPS=100` cap is a second backstop. (Loop/merge node types in Phase C opt into re-entry /
   multi-input gathering on top of this same contract.)
5. `keyword` consumes the turn on its first `match` (sibling keyword guards are skipped); it is
   reported as `matched`. `condition` partitions items into `true`/`false` buckets and never
   consumes the turn.
6. Return the joined `replies` (or `fallbackAnswer` if none), `matched`, the projected `trace`, and
   the final `varsView` snapshot as `vars`.

Expressions resolve against the **current item's `json` layered over `varsView`** (item wins);
`setVariable`/`httpRequest` mirror their writes into `varsView` so the chat `vars` snapshot and the
`{{…}}` namespace stay equivalent to the pre-item engine for single-lineage flows. Phase 0 legacy
behavior (`trigger → keyword → sendMessage`, first matching keyword wins, else `fallbackAnswer`) is
preserved exactly.

### Reactive walk

`WorkflowEngine.run(...)` returns `reactor.core.publisher.Mono<MatchResult>`. The drain loop runs
`SyncExecutor` nodes (`keyword`/`sendMessage`/`setVariable`/`condition` + the trigger seed) inline,
and the lone `AsyncExecutor` (`httpRequest`) introduces a `Mono` boundary — the walk awaits its call
(via `flatMap`) before resuming. Node types are pluggable via a `NodeExecutor` registry keyed by
`FlowNode.type`; the pure helpers (interpolation, condition eval, keyword match, HTTP routing) live
in `EngineFns`. Edge-order routing, the visited-set + `MAX_STEPS=100` guard, and the keyword
turn-consuming rule are unchanged.

The richer per-node `NodeTrace` (input items + output buckets per handle) is captured during the walk
for the future execution-history data inspector (Phase D); it is projected down to the wire
`TraceStep{nodeId,type,handle,detail}` for the current response, byte-for-byte as before.

The engine stays Spring-free: outbound HTTP goes through a `fun interface HttpCaller`
(`call(method, url, headers, body): Mono<HttpCallResult>`, where `HttpCallResult` is
`{statusCode, body, ok}`). `run(...)` takes an `HttpCaller` parameter; tests pass a fake, production
passes the `@Component WebClientHttpCaller`. `RuntimeService` injects the bean and flatMaps the
engine's `Mono`.

### `httpRequest` safety (SSRF hardening)

`httpRequest` lets a bot owner trigger server-side requests, so `WebClientHttpCaller` enforces:

- **Scheme allow-list:** only `http`/`https`.
- **Resolver-pinned address allow-list (anti-rebinding):** a custom Netty `AddressResolverGroup`
  (`SsrfFilteringResolverGroup`) wraps the JDK default resolver and **fails resolution** whenever any
  resolved address is non-public. Because the request is issued against the original URI and
  Reactor-Netty re-uses that SAME resolver at connect time, the IP that gets validated is exactly the
  IP that gets dialed. This closes the DNS-rebinding / TOCTOU hole that a separate pre-flight
  `getAllByName` check leaves open (a TTL-0 name returning a public IP at check time and
  `169.254.169.254` / `127.0.0.1` at connect time). A pre-flight `isBlockedHost` literal check remains
  as a cheap fast-fail but is **not** the only defense.
- **Block-list (both layers):** loopback, RFC1918 private (`10/8`, `172.16/12`, `192.168/16`),
  link-local (`169.254/16`, incl. the `169.254.169.254` cloud-metadata IP), wildcard/any-local,
  site-local, multicast, and **IPv6 unique-local ULA `fc00::/7`** (`fc00::` and `fd00::`, detected via
  `(octet0 & 0xFE) == 0xFC` since JDK `isSiteLocalAddress` only knows the deprecated `fec0::/10`).
  Bracketed IPv6 literals from `URI.host` (`[fd00::1]`) are unbracketed before resolution. Dotless
  integer IPv4 literals (`2130706433` == `127.0.0.1`) stay blocked. Unresolvable hosts are blocked.
- **No redirects** (a public URL can't 30x to a private host), **256 KB** response-body cap
  (`maxInMemorySize`), and a **5s** per-request timeout (treated as an error).
- **Amplification / DoS caps:** the engine performs at most **`MAX_HTTP_REQUESTS = 5`** `httpRequest`
  calls per walk (further `httpRequest` nodes are not dialed and route to `error`/default), and
  `RuntimeService.handleMessage` wraps the whole walk in a **20s** overall timeout that maps to the
  bot's safe `fallbackAnswer` (never a 5xx). Request headers `Host`, `Content-Length`, `Connection`,
  and `Transfer-Encoding` are stripped (case-insensitive) before the call.
- Any blocked/failed request returns a non-ok `HttpCallResult` (`statusCode = 0`) routed to
  `error`/default — it never throws out of the walk.

## Execution trace (Phase 3a — debugger)

The runtime records a per-node **execution trace** so the editor can show an n8n-style "what each
node did" view. `bot-api`'s `POST /api/runtime/sessions/{sessionId}/messages` response carries it.

`MessageResponse` shape (client-ui mirrors this verbatim):

```jsonc
{
  "reply": "Hello there!",
  "matched": { "text": "Greeting" },          // or null on fallback
  "trace": [
    { "nodeId": "trigger",    "type": "trigger",     "handle": null,    "detail": "start" },
    { "nodeId": "kwGreeting", "type": "keyword",     "handle": "match", "detail": "matched: hi" },
    { "nodeId": "msgGreeting","type": "sendMessage", "handle": null,    "detail": "Hello there!" }
  ],
  "vars": { "message": "Hi", "greeting": "Hi Sam" }   // final variable snapshot
}
```

`TraceStep = { nodeId: string, type: string, handle: string|null, detail: string|null }` where
`handle` is the output handle the walk **followed** from that node (`match`/`nomatch`/`true`/`false`/
`error`/`null`). One step is appended per node **as it executes**, in execution order. `vars` is the
final `ExecutionContext.vars` snapshot.

`detail` format per node type:

| `type`        | `handle`               | `detail`                                                  |
|---------------|------------------------|----------------------------------------------------------|
| `trigger`     | `null`                 | `start`                                                   |
| `keyword`     | `match` / `nomatch`    | `matched: <keyword>` (the first keyword that hit) / `no match` |
| `sendMessage` | `null`                 | the interpolated reply text                              |
| `setVariable` | `null`                 | `<name> = <interpolated value>` (or `skipped (blank name)`) |
| `condition`   | `true` / `false`       | `"<left>" <op> "<right>" -> <true\|false>` (operands interpolated) |
| `httpRequest` | `null` / `error`       | `<METHOD> <url> -> <status>` on 2xx, else `-> error` (non-2xx), `-> blocked` (no response, status 0), or `-> cap exceeded` (past `MAX_HTTP_REQUESTS`) |

The engine builds steps directly as the wire `TraceStep` DTO (it already imports the dto package for
`FlowNode`/`FlowEdge`), so `RuntimeService` passes them through without mapping. The **20s walk
timeout** fallback path returns the safe `fallbackAnswer` with an **empty** `trace` and `vars`.

## Execution history & data inspection (Phase D)

The per-message `trace` above is ephemeral. Phase D **persists** every walk as an owner-scoped
history record carrying not just *what* each node did but *the data it saw and emitted* — n8n's
per-node input/output inspector. The runtime emits one record after each walk; client-api stores it
and serves it back to the bot's owner; the editor maps a canvas node click to that node's recorded
items.

### Record shape (bot-api → client-api, internal)

After every walk `bot-api` builds an `ExecutionRecordRequest` from the rich `NodeTrace`s and POSTs it
to client-api. The wire body:

```jsonc
{
  "botId": "abc123",
  "status": "success",                 // or "error" only on the 20s timeout fallback
  "trigger": "message",                // or "webhook" (the schedule path arrives as a webhook)
  "startedAt": "2026-06-16T10:00:00Z", // ISO-8601 (java.time.Instant in the runtime)
  "finishedAt": "2026-06-16T10:00:01Z",
  "message": "Hi",                     // the bounded (4000) user/input text
  "reply": "Hello there!",
  "nodes": [
    {
      "nodeId": "kwGreeting",
      "type": "keyword",
      "handle": "match",               // primary handle followed, or null
      "detail": "matched: hi",
      "inputItems": [ { "json": { "message": "Hi" } } ],   // item wrappers: { json, binary? }
      "outputs": { "default": [ { "json": { "message": "Hi" } } ] },  // null handle key → "default"
      "error": null
    }
  ]
}
```

Each item is the n8n item wrapper `{ json, binary? }` (from `ExecutionItem`), **not** a bare json
map; the inspector reads `item.json`. The **null** output handle is serialized as the literal string
key `"default"` (named buckets keep their handle: `match`/`nomatch`/`true`/`false`/`error`). The
client-api DTO models each item as an open `Map`, so the wrapper round-trips verbatim — stored as-is,
returned as-is — with no unwrap step on either side.

### Fire-and-forget emit (bot-api)

`RuntimeService.emitExecution(...)` builds the record and posts it **fire-and-forget**: it subscribes
on its own `boundedElastic` worker (`subscribeOn`) so the POST can never block, delay, or fail the
user's `MessageResponse`, and any error is logged and swallowed. Both the success path and the
20s-timeout fallback path emit (the latter as `status:"error"` with an **empty** `nodes` list). The
post never carries an identity — see the gateway boundary below.

### Internal write endpoint (gateway-blocked)

```
POST /api/internal/executions          // permitAll in client-api SecurityConfig
```

Same server-to-server boundary as the webhook lookup: the **gateway 404s every `/api/internal/**`
path**, so this POST is never client-reachable; only bot-api inside the cluster reaches it. The body
carries **no `ownerId`** — client-api resolves the owner from the **bot document**
(`ExecutionService.persist` looks up `botTemplateRepository.findById(botId)` and copies `bot.ownerId`
onto the `Execution`), so a run can only ever be attributed to the bot's real owner. A record for an
**already-deleted bot** is silently dropped (the chain completes empty) rather than 500-ing a
server-to-server call. The stored `Execution` document adds an `ownerId` and a server-side `createdAt`
to the wire fields.

### Owner-scoped read endpoints (through the gateway, JWT)

Both reads resolve the current user via `UserSessionProvider.getCurrentUserSessionOrFail()` and are
strictly owner-scoped with the same opaque not-found semantics as `BotService` (no IDOR leak between
missing and not-owned):

```
GET /api/bots/{id}/executions?limit=&offset=   // newest-first SUMMARIES for one owned bot
GET /api/executions/{execId}                   // the FULL run, including per-node items
```

- **List** verifies the bot is owned first (forged/foreign bot id → the same `Bot not found` as
  `BotService`), then streams `findByBotIdAndOwnerId` paged via the shared
  `lib/OffsetBasedPageable` carrying a total sort (`startedAt` desc, `id` desc tiebreaker so offset
  paging is stable) (`limit` coerced to 1..100, default 20; `offset` ≥ 0). Items are
  **summaries** — `{ id, botId, status, trigger, startedAt, finishedAt, message, reply, nodeCount }` —
  with **no heavy item payloads**, so the list stays cheap.
- **Get** returns the full `ExecutionView` (`nodes[].inputItems`/`outputs` included) only when the
  execution's `ownerId` equals the current user; otherwise the same opaque `Execution not found`
  whether the id is unknown or simply owned by someone else.

### Size caps (bounded Mongo documents)

`bot-api` applies the caps **before sending**, so the stored documents stay bounded:

- At most **20 items per node per direction** — `inputItems` and **each** output bucket independently.
- When a direction has more, keep the **first 20** and append a single marker item
  `{ "json": { "__truncated__": <omittedCount> } }`.
- **Never persist secrets**: the record carries item **json (data)** only, never `node.data` (the
  per-node config bag where connector tokens/keys live). Items and config are different shapes;
  nothing copies config into items. (Credentials move out of `node.data` entirely in Phase E.)

### Per-node inspect UI (client-ui)

The editor's **Executions** panel (`ExecutionsPanel`) lists a saved bot's runs newest-first with
load-more paging; selecting a row fetches the full `ExecutionView` and lifts it into the editor.
While an execution is "active", clicking a node on the canvas renders `ExecutionNodeInspect` for the
matching `nodeId` — its `error`, the **input** items, and the **output** items per handle, each as
formatted JSON. A node that never ran in that execution shows an explicit empty state. The client-ui
`getExecution`/`listExecutions` helpers hit exactly the two owner-scoped URLs above.

## Manual execution & pinned data (Phase F)

Phase D persists and inspects runs the user *happened to trigger*. Phase F adds an explicit editor
**Run** button: it runs the **saved** flow on demand, returns the rich per-node data inline (no second
fetch), persists the run as trigger `manual`, and supports **pinned data** — the n8n
iterate-without-recalling dev loop where you pin a node's last output and re-run the rest of the flow
without re-calling any external API.

### Owner-scoped manual execute endpoint (bot-api, through the gateway)

```
POST /api/runtime/bots/{id}/execute    // AUTHENTICATED + owner-scoped
```

Reached through the gateway like the existing `/api/runtime/*` session routes. It is **owner-scoped
exactly like `startSession`**: `RuntimeHandler.runManual` forwards the caller's `Authorization`
header to `clientApiClient.fetchBot(id, authHeader)`, so only the bot's owner can run it — a non-owner
gets the **same upstream not-found** the session path returns (client-api's opaque `Bot not found`,
no IDOR leak). The run uses the same 20s walk timeout and SSRF/caps as the webhook path.

Request `ManualRunRequest`:

```jsonc
{
  "message": "Hi",                 // optional; seeds the engine's user text (vars["message"])
  "vars": { "lang": "en" },        // optional; initial variables (message cannot be overridden here)
  "pinnedData": {                  // optional; see "Pinned data" below
    "httpNode1": [ { "json": { "status": 200, "body": { "ok": true } } } ]
  }
}
```

Response `ManualRunResponse`:

```jsonc
{
  "reply": "Hello there!",
  "matched": { "text": "greeting" } | null,
  "vars": { "message": "Hi" },
  "trace": [ { "nodeId": "...", "type": "...", "handle": "...", "detail": "..." } ],
  "nodes": [                       // the rich per-node data, SAME shape Phase D persists
    {
      "nodeId": "httpNode1",
      "type": "httpRequest",
      "handle": null,
      "detail": "pinned (1 items)",
      "inputItems": [ { "json": { "message": "Hi" } } ],
      "outputs": { "default": [ { "json": { "status": 200, "body": { "ok": true } } } ] },
      "error": null
    }
  ]
}
```

`nodes[]` is projected from `MatchResult.nodeTraces` with the **same size caps** the persisted record
uses (20 items/node/direction, 32 KiB/item, the null handle serialized as `"default"`), so the editor
shows each node's input/output items inline without a second fetch. The editor's
`ExecutionNodeInspect` (the Phase D inspector) renders these `nodes[]` directly — clicking a canvas
node after a Run shows that node's recorded input and per-handle outputs. The run is also
**persisted fire-and-forget** as trigger `manual` via the same `emitExecution` path the
message/webhook runs use; the post can never block, delay, or fail the Run response.

### Pinned data (engine support)

`WorkflowEngine.run(..., pinnedData: Map<String, List<ExecutionItem>> = emptyMap())` threads a
per-node pin map (default empty, so every existing caller and test is byte-identical). It is carried
on `RunContext.pinnedData`. When the scheduler is about to run a node whose id is in `pinnedData`, it
**does NOT execute that node's executor** — no HTTP/connector/code side effect — and instead emits
`NodeOutput.default(pinnedData[nodeId])`, recording a `NodeTrace` with detail `pinned (N items)`. The
pin applies on the node's **default** output, which covers the common case of pinning an
`httpRequest` / connector / `code` result. Pinning therefore simply **skips a node's outbound call**:
no new SSRF or secret surface, since the executor (and its credential resolution) never runs.

`RuntimeService` threads the request's `pinnedData` into `run` for the **manual path only**: each wire
`{ json }` is parsed into an `ExecutionItem` (`parsePinnedData`), and the map only ever affects the
caller's own owner-scoped run and is returned only to them. The dev loop: Run once, pin a node's
captured default output, then re-Run — the pinned node is skipped while the rest of the flow
re-executes against its pinned items, so you iterate downstream nodes without re-calling external APIs.

### Pin/unpin round-trip (client-ui)

The editor's **Run** button (`BotEditor.onRun`) saves the current graph in place, then calls
`executeWorkflow(botId, { message, pinnedData })` (`api/runtime.js`) → `POST .../execute`. On success
it enters inspect mode over the response `nodes[]`. **Pin** (`pinSelected`) captures the selected
node's `outputs.default` items from the last run into `pinnedData[nodeId]`; **unpin** removes the key.
That map round-trips verbatim back into the next request's `pinnedData` (`{ "<nodeId>": [ { json } ] }`),
so the pinned node is skipped on the following Run. Pinned nodes get a visible badge on the canvas
without mutating the persisted graph.

> **client-api accepts trigger `manual`.** The execution `trigger` is a free string end-to-end
> (`ExecutionRecordRequest.trigger`, `Execution.trigger`, copied through `ExecutionService.persist`) —
> no enum or validation constrains it, so `manual` persists with no client-api change.

## Webhook invocation (Phase 3b)

A bot's flow can be triggered by an external caller **without a user JWT**, statelessly (no session):

```
POST /api/runtime/webhooks/{token}
Content-Type: application/json

{ "message": "hi there", "vars": { "caller": "Ada" } }     // body optional
```

- **bot-api route** (`RuntimeWebConfig`, under the `/api/runtime` nest): public by design — the
  `{token}` is the credential, verified at the data layer. No `Authorization` header is read.
- **Request shape** (`WebhookRequest`): `{ message?: string, vars?: object }`. `message` seeds the
  engine's user text (`vars["message"]`), defaulting to the empty string when null/absent; `vars`
  are merged into the execution context as **initial variables** before the walk (the seeded
  `message` always wins and cannot be shadowed). An empty/absent body runs the flow with no message
  and no extra vars. `message` is bounded by the same `MAX_MESSAGE_LENGTH` (4000) guard as session
  messages.
- **Resolution**: bot-api calls client-api's internal lookup
  `GET /api/internal/bots/by-webhook/{token}` (**permitAll**, no auth header), which returns the same
  `BotView` JSON as `GET /api/bots/{id}` (`nodes`/`edges`/`fallbackAnswer`/`name`), or **404** for an
  unknown token. bot-api maps that upstream 404 to a **404** response.
- **Execution**: runs `WorkflowEngine.run(...)` **once** with the same injected SSRF-hardened
  `httpCaller`, the same per-walk HTTP cap, and the same **20s** overall walk timeout (timeout →
  safe `fallbackAnswer`). The response is the standard `MessageResponse` (`reply` + `matched` +
  `trace` + `vars`). No session is created or stored. The token is never logged.

The engine's `run(...)` gained an optional `initialVars: Map<String, Any?> = emptyMap()` parameter
(folded into `ExecutionContext` before `message` is seeded); all existing callers/tests are
unchanged via the default.

### Webhook token contract (client-api)

The `{token}` above is an unguessable, **server-generated** secret that client-api owns:

- **Generation**: `BotTemplate.webhookToken` is **32 cryptographically-random bytes** (256 bits) from
  `java.security.SecureRandom`, encoded URL-safe Base64 **without padding** (`generateWebhookToken()`
  in `model/BotTemplate.kt`). A fresh token is minted on **create** (`BotRequest.toBotTemplate`), and
  lazily on **read/update** for legacy/token-less documents (`BotTemplate.normalized()` mints one when
  absent; the persisted value converges on the next save). The token is high-entropy enough that the
  internal lookup needs no other credential.
- **Client cannot set it**: `BotRequest` has **no token field**, so the public create/update body can
  never choose or change the token. `BotRequest.applyTo` preserves the existing bot's token via
  `copy(...)`; `update` normalizes the stored bot first so legacy bots gain (and keep) a token.
- **Owner exposure**: `BotView.webhookToken` is returned to the **authenticated owner** on the
  owner-scoped create/get/update responses so they can build the `POST /api/runtime/webhooks/{token}`
  URL. It is **stripped from the list response** (`BotService.list`) so a single bulk call can't hand
  out every bot's live token. It is never exposed to non-owners.
- **Internal lookup**: `GET /api/internal/bots/by-webhook/{token}` (client-api, `BotHandler`,
  **permitAll** in `SecurityConfig`) is the only **unauthenticated** way to read a bot. It resolves
  via the repository's exact-match `findByWebhookToken(token)` (a blank token short-circuits to a
  miss so it can never match a legacy null/blank token) and returns the same `BotView` JSON, or an
  **opaque 404** for any unknown token — no enumeration signal and **no ownership check** (knowing the
  token already authorizes reading/running that one bot's flow). This is the conscious security
  trade-off of the feature: the token *is* the bearer credential for a single bot.

## Schedule (cron) trigger (Phase 3c)

A bot can carry a **cron schedule** so its flow runs periodically — a bot-level trigger, the
time-based sibling of the [webhook](#webhook-invocation-phase-3b). It reuses the webhook runtime path
end-to-end: the scheduler simply *calls the public webhook endpoint*, so there are **no engine
changes**.

### Schedule field & validation (client-api)

- **Model**: `BotTemplate.schedule: String?` — a Spring cron expression (6 fields,
  `second minute hour day-of-month month day-of-week`, e.g. `0 0 * * * *` = top of every hour).
  Null/blank means **not scheduled**.
- **Owner-controlled** (unlike the webhook token): `BotRequest.schedule` is accepted on
  create/update, threaded through `toBotTemplate`/`applyTo`, and returned on `BotView.schedule`. A
  blank value is normalized to `null` (not scheduled).
- **Validation**: when non-blank, `BotService` validates it with
  `org.springframework.scheduling.support.CronExpression.isValidExpression(...)` in the same
  `validateGraph` path used by both create and update; an invalid expression is rejected with
  `InvalidRequestException("Bot", "invalid cron schedule")` → `400`. Null/blank is always allowed.
- **Repository**: `BotTemplateRepository.findByScheduleNotNull(): Flux<BotTemplate>` streams the
  candidate scheduled bots.

### Single-instance scheduler (`ScheduledFlowRunner`, client-api)

A reactive `@Component` in `scheduling/` drives scheduled runs without Spring's blocking
`@Scheduled` annotation (it fits the WebFlux model):

- **Tick**: `Flux.interval(Duration.ofSeconds(60))` (the `TICK` constant), subscribed once at startup
  (`@PostConstruct`). Each tick re-queries `findByScheduleNotNull()`.
- **Due check** (pure, unit-tested `isDue(cron, lastFire, now)` / `dueBots(...)`): a bot is due when
  `CronExpression.parse(cron).next(lastFire ?: now - TICK)` exists and is **not after** `now`. The
  per-bot last fire time lives in an in-memory `ConcurrentHashMap<botId, Instant>`; a due bot's
  last-fire is advanced to `now` so it cannot double-fire within the same cron window even though the
  tick is coarser than the cron resolution. Each bot's evaluation is wrapped in a try/catch, so a
  single bad cron can never break the loop (it is treated as not-due).
- **Fire** = `POST {BOT_API_URI}/api/runtime/webhooks/{webhookToken}` with an empty JSON body `{}`
  via `WebClient` (`@Value("\${BOT_API_URI:http://localhost:8083}")`). This is exactly the public
  webhook path, so the existing stateless webhook runtime executes the flow — no session, no engine
  change. Firing is fire-and-forget (subscribed; debug on success, warn on error). The **webhook
  token is never logged**, and bots with a null/blank token are skipped.

### Known limitation — multi-replica double-fire

The last-fire state is **per-instance and in-memory with no distributed lock**. In a multi-replica
deployment each replica would independently fire every scheduled bot (once per replica per window).
This is **acceptable for the MVP** and documented here; a distributed scheduler lock (e.g. a Mongo
leader-election / ShedLock-style lease, or moving the trigger to an external scheduler) is the
recommended follow-up.

## Expressions (Phase 1+)

`{{ ... }}` placeholders in string config are interpolated against `vars` before use (implemented in
`bot-api` `WorkflowEngine.interpolate`). Grammar:

- A placeholder is `{{` ... `}}`; its inner text is **trimmed** (`{{ name }}` == `{{name}}`).
- The trimmed inner text is a **dotted path**: split on `.` and traversed through nested
  `Map<*, *>` values (`{{user.name}}`, `{{http.body.field}}`).
- The resolved value's **string form** (`toString()`) is substituted.
- A missing/unresolvable path (any segment absent, or a non-map value encountered mid-path) resolves
  to an **empty string**.
- Text outside `{{ }}` is left unchanged.
- Pure map/path traversal only — **no code execution, no reflection**.

### Sandboxed JS expressions (`{{= ... }}`, Phase B)

A placeholder whose trimmed inner text starts with `=` is evaluated as **sandboxed JavaScript** via
`ExpressionEvaluator` (GraalVM polyglot) instead of dotted-path traversal — e.g.
`{{= message.toUpperCase() }}`, `{{= $json.user.name }}`, `{{= $now }}`. Inside the JS each `vars`
key is a global, plus `$vars`/`$json` (the whole map) and `$now` (ISO-8601). The result's string form
is substituted; **any** error (syntax, runtime, statement-limit, time-limit) → empty string, so the
walk never breaks. The plain `{{ path }}` form is unchanged, so JS only runs when the author opts in
with `=`. Sandbox: `HostAccess.NONE`, no IO/host-classes/threads/process, a per-eval statement limit
(100k) **and** a watchdog wall-clock interrupt (~2s); guest code runs off the event loop
(`RuntimeService` schedules the engine drain on a bounded-elastic worker). The **`code` node**
(`data.code`) runs JS over the whole item list (`$items`, `$vars`) on the same sandbox and emits the
returned array as new items.

### Condition operator semantics (`condition` node)

`left` and `right` are interpolated first, then `left op right` is evaluated over the resulting
strings:

- `eq` / `neq` — string (in)equality.
- `contains` — `left.contains(right)`, **case-insensitive**.
- `gt` / `lt` — **numeric** compare when both operands parse as `Double`, otherwise **lexicographic**
  string compare.
- Unknown operators evaluate to `false`.

The `true` handle is followed when the predicate holds, else the `false` handle. `condition` is an
automatic branch point and does **not** consume the turn (only `keyword` does).

### `setVariable` semantics

`vars[name] = interpolate(value)`, then follow the default (`null`) output. A **blank** `name` skips
the write but still follows the default output.

## Backward compatibility

Stored `bot_template` documents using the legacy `questions` field convert to a graph on read:
each `Question(text, keyWords, answer)` → `trigger → keyword{label:text, keyWords} → sendMessage{text:answer}`.
Implement as a converter in client-api so old bots keep working.

## Layer responsibilities

- **client-api** — `BotTemplate` stores `nodes`/`edges`; `BotRequest`/`BotView` carry them; validation
  rejects graphs without exactly one `trigger`. Legacy `questions` converter on read.
- **bot-api** — `BotSummary` carries `nodes`/`edges`; `WorkflowEngine` (replaces `BotEngine`) walks
  the graph reactively (`Mono<MatchResult>`); `RuntimeService` keeps per-session `vars` and injects
  the `HttpCaller`. Outbound HTTP is the SSRF-hardened `WebClientHttpCaller`.
- **client-ui** — editor sends/loads `{nodes, edges}` directly (stop flattening to `questions`); node
  palette to add typed nodes; per-node inspector keyed by `type`; real edges with handles.

## Security posture & known limitations

- **`/api/internal/**` is gateway-blocked.** The webhook-token lookup is server-to-server only
  (bot-api → client-api directly via `CLIENT_API_URI`). A `WebFilter` in the gateway returns 404 for
  any `/api/internal/` path so it is never reachable from the internet through the broad `/api`
  catch-all route.
- **SSRF**: outbound `httpRequest` calls go through `WebClientHttpCaller`, which pins the connect-time
  resolution to a validated IP (custom Netty `AddressResolverGroup`) — blocking loopback, RFC1918,
  link-local/metadata (`169.254.169.254`), IPv6 ULA (`fc00::/7`), and embedded-IPv4-in-IPv6 forms —
  disables redirects, caps the response body (256 KB), and times out at 5s. Per-walk HTTP calls are
  capped (`MAX_HTTP_REQUESTS = 5`) and the whole walk times out at 20s.
- **Known limitation — no rate limiting on the public webhook.** `POST /api/runtime/webhooks/{token}`
  is unauthenticated (token-guarded). A leaked token could be used to drive repeated flow runs /
  outbound HTTP fan-out. A gateway `RequestRateLimiter` (Redis) keyed by token/IP is the recommended
  follow-up; not yet implemented (needs Redis infra).

## Credentials (Phase E)

Connector and `httpRequest` secrets move **out of inline `node.data`** into reusable, owner-scoped,
encrypted **credentials**. A node opts in with `data.credentialId`; the inline secret field stays the
fallback for when no credential is referenced.

### Credential types

Each type names a secret shape and fixes the **exact** set of secret field keys its `data` map must
carry (validated on create/update — no more, no fewer). A node accepts the type(s) matching its
integration.

| Type id          | Secret fields                | Used by node(s)                  |
|------------------|------------------------------|----------------------------------|
| `telegramApi`    | `botToken`                   | `telegramSend`                   |
| `anthropicApi`   | `apiKey`                     | `anthropicMessage`               |
| `slackWebhook`   | `webhookUrl`                 | `slackSend`                      |
| `discordWebhook` | `webhookUrl`                 | `discordSend`                    |
| `httpHeaderAuth` | `headerName`, `headerValue`  | `httpRequest` (adds one header)  |
| `httpBearerAuth` | `token`                      | `httpRequest` (`Authorization: Bearer <token>`) |

These field names are the single shared contract across client-api validation, the bot-api injection,
and the client-ui selector/forms (`client-ui/src/credentials/credentialTypes.js`).

### Encryption at rest (AES-256-GCM)

The Mongo `credential` document is `{ id, ownerId, name, type, encrypted, createdAt }`. The secret
field map (e.g. `{botToken: "..."}`) is serialized to JSON, then encrypted with **AES-256-GCM** by
`CredentialCipher`; only the resulting `encrypted` blob is stored. Each encryption uses a **fresh
random 12-byte IV** prepended to the ciphertext+tag and Base64-encoded as one blob (`IV || ct`), so
encrypting the same plaintext twice yields different blobs.

The 256-bit key comes from env `CREDENTIAL_ENCRYPTION_KEY` (Base64 of 32 random bytes). It is held
only in memory — **never** stored in Mongo, **never** logged, **never** returned. When the env var is
absent the cipher logs one **loud startup warning** and falls back to a fixed, clearly-named INSECURE
dev key so local dev works without configuration; that key must never be relied on in production.
Decryption happens **only inside client-api**.

### Metadata-only responses (owner-scoped CRUD, through the gateway, JWT)

The five CRUD routes are JWT-authenticated and owner-scoped (current user via
`UserSessionProvider.getCurrentUserSessionOrFail()`). Responses carry **metadata only** —
`CredentialView = { id, name, type, createdAt }`. **Secrets are never returned**: there is no read
path back to a stored secret value, so changing a secret means re-submitting it.

| Method & path                  | Body                                            | Result |
|--------------------------------|-------------------------------------------------|--------|
| `POST /api/credentials`        | `{ name, type, data: {<secret fields>} }`       | `200 CredentialView` (validates, encrypts, stores) |
| `GET /api/credentials`         | —                                               | `[CredentialView]` for the owner |
| `GET /api/credentials/{id}`    | —                                               | `CredentialView` (404 for missing/foreign, like `BotService`) |
| `PUT /api/credentials/{id}`    | `{ name?, data? }`                              | `CredentialView` (renames; re-encrypts when `data` present; `type` is immutable) |
| `DELETE /api/credentials/{id}` | —                                               | `204` |

`data` must have **exactly** the declared type's fields (and no blank values); otherwise the shared
`InvalidRequestException` → `400` envelope. Missing and foreign ids return the same opaque
`Credential not found`, so not-found and not-owned are indistinguishable (no IDOR leak).

### Internal decrypted fetch (the anti-IDOR boundary)

`GET /api/internal/credentials/{credId}?botId={botId}` — the **only** place a decrypted secret leaves
client-api. It is server-to-server: the gateway 404s every internal path, so it is never
client-reachable; `SecurityConfig` permits **only** this `GET` (the CRUD routes stay authenticated).

client-api loads the bot by `botId` to learn its `ownerId`, loads the credential by `credId`, and
returns the decrypted secret **only when `credential.ownerId == bot.ownerId`** — otherwise an opaque
`404`. This is the anti-IDOR rule: a bot owner can never resolve another user's credential by
referencing its id in their graph. The response is
`CredentialSecretView = { type, data: { <decrypted fields> } }`.

bot-api calls this through `ClientApiClient.fetchCredential(credId, botId)` (**not** the engine's
SSRF-filtered `HttpCaller` — client-api may be a private host the SSRF filter would block). A `404`
maps to an empty `Mono`, so an unresolvable reference is indistinguishable from a missing one.

### Engine seam & node behavior

The engine stays Spring-free. `WorkflowEngine.run(...)` gains a trailing
`credentialResolver: CredentialResolver = CredentialResolver.NONE` (so existing callers/tests are
unchanged); `RunContext` carries it and **memoizes** resolved ids per walk so a repeated reference
never refetches. `RuntimeService` builds the real resolver bound to the **current `botId`**, delegating
to `ClientApiClient.fetchCredential`.

- **Connectors** (`telegramSend`/`anthropicMessage`/`slackSend`/`discordSend`): when `credentialId`
  is set, the resolved secret field replaces the inline one (Telegram `botToken`, Anthropic `apiKey`,
  Slack/Discord `webhookUrl`). A set-but-**unresolvable** `credentialId` routes the node's
  `error`/default handle and does **not** dial (never sends an unauthenticated request); a blank
  `credentialId` uses the inline field.
- **`httpRequest`**: when `credentialId` resolves an `httpHeaderAuth` it adds
  `{ headerName: headerValue }`; an `httpBearerAuth` adds `Authorization: Bearer <token>`. The auth
  header is merged on top of the configured headers, before the SSRF call.

### Secret-leak invariants

Decrypted secret values are used **only** to build the outbound HTTP call. They must **never** be
written into `item.json`, **never** appear in a `NodeTrace` detail / input / output, **never** in the
persisted execution record, and **never** logged. Connector traces label the URL with a redacted
constant (e.g. a Telegram URL with the token shown as `bot***`) because `MatchResult.trace` is
returned to the API caller.
