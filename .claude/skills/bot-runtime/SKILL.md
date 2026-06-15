---
name: bot-runtime
description: Work on the bot-api runtime engine — the conversational session service that loads a bot from client-api (forwarding the caller's JWT) and answers user messages via keyword matching. Use when adding/changing runtime sessions, the matching engine, or the service-to-service call to client-api.
disable-model-invocation: true
---

# bot-runtime

`bot-api` is a reactive Spring Boot 4 service (port **8083**, reached via the gateway under
`/api/runtime/**`, all routes require auth) that runs bots interactively: it holds stateful chat
sessions and answers a user's messages from the bot's configured questions.

## HTTP API (all under `/api/runtime`, authenticated)

| Method & path | Body | Response |
|---|---|---|
| `POST /api/runtime/bots/{id}/sessions` | — | `{ sessionId, greeting }` (greeting e.g. `"Hi, I'm <botName>"`) |
| `POST /api/runtime/sessions/{sessionId}/messages` | `{ text }` | `{ reply, matched: { text } \| null }` |

## Loading a bot — service-to-service with JWT forwarding

`bot-api` does **not** own bot data. To create a session it loads the bot from `client-api`:

```
GET {CLIENT_API_URI}/api/bots/{id}
Authorization: Token <jwt>      # forwarded verbatim from the caller's request
```

`client-api` enforces ownership, so `bot-api` must forward the caller's `Authorization: Token <jwt>`
header on the outbound WebClient call. `CLIENT_API_URI` is an env var, default
`http://localhost:9000`.

```kotlin
@Service
class BotLoader(private val webClient: WebClient,
                @Value("\${client-api.uri:http://localhost:9000}") private val clientApiUri: String) {

    fun loadBot(botId: String, authorization: String): Mono<BotView> =
        webClient.get()
            .uri("$clientApiUri/api/bots/{id}", botId)
            .header(HttpHeaders.AUTHORIZATION, authorization) // "Token <jwt>", forwarded as-is
            .retrieve()
            .bodyToMono(BotView::class.java)
}
```

Read the incoming header in the handler (functional WebFlux: `serverRequest.headers().firstHeader(HttpHeaders.AUTHORIZATION)`)
and pass it through. Never re-derive identity in `bot-api`; trust client-api's ownership check.

## Sessions (stateful)

Keep sessions in a `ConcurrentHashMap<String, Session>` (sessionId -> loaded bot + any per-session
state). Generate `sessionId` (e.g. `UUID`). A session caches the bot snapshot loaded at creation so
message handling does not re-call client-api per message.

## Matching engine

For an incoming message:

1. Lowercase the user text and tokenize it.
2. A question **matches if ANY of its `keyWords` appears** in the text, case-insensitive, as a
   whole-word or substring match.
3. Return that question's `answer` as `reply` with `matched: { text: <question text> }`.
4. If nothing matches, return the bot's `fallbackAnswer` with `matched: null`.

```kotlin
fun answer(bot: BotView, userText: String): Match {
    val text = userText.lowercase()
    val q = bot.questions.firstOrNull { question ->
        question.keyWords.any { kw -> text.contains(kw.lowercase()) }
    }
    return if (q != null) Match(reply = q.answer, matchedText = q.text)
           else Match(reply = bot.fallbackAnswer, matchedText = null)
}
```

## Checklist

- [ ] Routes under `/api/runtime`, all authenticated (gateway forwards there to :8083)
- [ ] Session create returns `{ sessionId, greeting }`; greeting names the bot
- [ ] Message endpoint returns `{ reply, matched: { text } | null }`
- [ ] Bot loaded from `client-api` via WebClient, forwarding `Authorization: Token <jwt>`
- [ ] `CLIENT_API_URI` env (default `http://localhost:9000`) used, not hardcoded
- [ ] Matching is case-insensitive ANY-keyword (whole-word/substring); fallback on no match
- [ ] Sessions held in a `ConcurrentHashMap`; reactive, no blocking calls
- [ ] Tests added via the `gen-test` skill
