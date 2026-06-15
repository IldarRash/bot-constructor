---
name: rsocket-collab
description: Add or extend real-time board collaboration over RSocket in Bot Constructor — Spring @ConnectMapping/@MessageMapping server side in client-api plus the browser rsocket-js client. Use when working on presence, live cursors, or live node/edge sync on the flow editor.
disable-model-invocation: true
---

# rsocket-collab

RSocket powers **real-time board collaboration** (presence + live editing of the @xyflow flow), NOT
gateway routing. The server lives in **client-api**, which exposes an RSocket WebSocket endpoint:

```yaml
spring:
  rsocket:
    server:
      mapping-path: /rsocket
      transport: websocket
```

The gateway proxies `/rsocket` (the Vite dev server also proxies it to the gateway). Clients connect
over the `/rsocket` WebSocket.

## Wire protocol — `BoardEvent` (JSON, Jackson)

```kotlin
data class BoardEvent(
    val type: String,        // see set below
    val senderId: String,
    val senderName: String,
    val payload: Any? = null // @xyflow node/edge JSON, roster list, or cursor {x, y}
)
```

`type` is one of: `PRESENCE_JOIN`, `PRESENCE_LEAVE`, `ROSTER`, `NODES_CHANGE`, `EDGES_CHANGE`,
`NODE_ADD`, `NODE_REMOVE`, `EDGE_ADD`, `EDGE_REMOVE`, `CURSOR`. Clients **ignore events whose
`senderId == their own`** (they already applied them optimistically).

## Server (client-api)

### Connection auth — `@ConnectMapping`

Authenticate from the **setup payload**, whose data is JSON `{ "token": "<jwt>" }`. Validate with the
existing `JwtSigner`; reject with `Mono.error(...)` if invalid. Resolve `userId` + a display name.

```kotlin
@ConnectMapping
fun connect(requester: RSocketRequester, @Payload setup: String): Mono<Void> {
    val token = objectMapper.readTree(setup).get("token")?.asText()
        ?: return Mono.error(RejectedSetupException("missing token"))
    return Mono.fromCallable { jwtSigner.parse(token) }          // throws if invalid
        .onErrorMap { RejectedSetupException("invalid token") }
        .doOnNext { principal -> /* associate userId/name with this connection */ }
        .then()
}
```

### Per-board fan-out

Keep one multicast sink per board in a map:

```kotlin
private val boards = ConcurrentHashMap<String, Sinks.Many<BoardEvent>>()
private fun sink(boardId: String) =
    boards.computeIfAbsent(boardId) { Sinks.many().multicast().onBackpressureBuffer() }
```

### Subscribe stream — `@MessageMapping` requestStream

```kotlin
@MessageMapping("board.{boardId}")
fun join(@DestinationVariable boardId: String /*, principal */): Flux<BoardEvent> {
    val sink = sink(boardId)
    // on subscribe: announce self + send current roster to the joiner
    val initial = Flux.just(
        BoardEvent("PRESENCE_JOIN", userId, userName),
        BoardEvent("ROSTER", userId, userName, payload = currentRosterFor(boardId))
    )
    sink.tryEmitNext(BoardEvent("PRESENCE_JOIN", userId, userName)) // fan-out to others
    return initial.concatWith(sink.asFlux())
        .doFinally { sink.tryEmitNext(BoardEvent("PRESENCE_LEAVE", userId, userName)) }
}
```

### Edits — `@MessageMapping` fireAndForget

```kotlin
@MessageMapping("board.{boardId}.edit")
fun edit(@DestinationVariable boardId: String, event: BoardEvent): Mono<Void> {
    sink(boardId).tryEmitNext(event) // fan out to all subscribers of this board
    return Mono.empty()
}
```

## Browser client (rsocket-js)

Use `rsocket-core` + `rsocket-websocket-client`. Connect with the JWT in the setup payload, open the
`board.{boardId}` requestStream, and push edits via `board.{boardId}.edit` fireAndForget. Route the
metadata so the server `@MessageMapping` destination resolves (composite metadata routing). Drop
incoming events whose `senderId` equals the local user id.

```js
import { RSocketConnector } from 'rsocket-core';
import { WebsocketClientTransport } from 'rsocket-websocket-client';

const connector = new RSocketConnector({
  transport: new WebsocketClientTransport({ url: `ws://${location.host}/rsocket` }),
  setup: {
    dataMimeType: 'application/json',
    metadataMimeType: 'message/x.rsocket.composite-metadata.v0',
    payload: { data: Buffer.from(JSON.stringify({ token: getToken() })) },
  },
});
const rsocket = await connector.connect();
// requestStream "board.{boardId}" -> apply events where senderId !== myId
// fireAndForget "board.{boardId}.edit" with a BoardEvent on local node/edge changes + cursor moves
```

## Checklist

- [ ] `@ConnectMapping` validates the setup-payload JWT with `JwtSigner`; rejects invalid tokens
- [ ] One `Sinks.many().multicast().onBackpressureBuffer()` per board, kept in a `ConcurrentHashMap`
- [ ] requestStream emits `PRESENCE_JOIN` + `ROSTER` on subscribe, `PRESENCE_LEAVE` in `doFinally`
- [ ] edit mapping is fireAndForget and fans out via `tryEmitNext`
- [ ] `BoardEvent` JSON shape matches both ends; `type` is from the allowed set
- [ ] Browser client connects over `/rsocket`, sends JWT in setup, ignores self-authored events
