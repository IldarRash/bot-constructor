---
name: new-flow-node
description: Scaffold a new workflow node type across all three layers — client-api persistence/validation, bot-api runtime executor, and the client-ui editor (node component + palette + inspector). Use when adding an n8n-style node type (trigger, keyword, sendMessage, condition, setVariable, httpRequest, webhook, schedule, ...) to the bot workflow engine.
disable-model-invocation: true
---

# new-flow-node

Add one workflow node `type` end-to-end. The canonical contract lives in `docs/workflow-engine.md`
— read it first and update the node-type registry table there as part of every change. A node type
is identified by its `type` string and a per-type `data` config bag; nodes connect via `edges` with
named `sourceHandle`s for multi-output (branching) nodes.

## Steps

1. **Contract** — add/append the node to the registry table in `docs/workflow-engine.md`: its `type`
   string, `data` shape, output handles, and runtime behavior.
2. **client-api (persist + validate)** — the graph is stored as open `nodes`/`edges` on `BotTemplate`
   (`data: Map<String, Any?>`), so most node types need **no model change**. Add validation only if
   the type has required `data` keys or handle constraints (validate in `BotService`/a graph
   validator; reject with `InvalidRequestException`). Keep `BotRequest`/`BotView` carrying raw
   `nodes`/`edges`.
3. **bot-api (execute)** — add a `NodeExecutor` for the `type` in the runtime engine package
   (`engine/`). It reads `node.data`, mutates the `ExecutionContext` (`vars`, `replies`), and returns
   the output handle name to follow. Register it in the executor map keyed by `type`. Reactive only
   (`Mono`/`Flux`) for any I/O (e.g. `httpRequest` uses `WebClient`).
4. **client-ui (edit)** — add a node component under `src/components/nodes/<Type>Node.js` with the
   right React Flow `Handle`s (one source handle per output), register it in the editor's `nodeTypes`
   map, add it to the node palette/sidebar, and add an inspector section keyed by `type` that edits
   `data`. Collaboration already fans out generic `NODES_CHANGE`/`EDGE_ADD` events — no RSocket change
   needed for a new type.
5. **Build & test** — `./gradlew :bot-api:build :client-api:build` and `cd client-ui && npm test`;
   add an engine unit test for the new executor (mirror `BotEngineTest`/`WorkflowEngineTest`).

## Conventions

- `data` keys are camelCase and match across all three layers verbatim.
- Multi-output nodes name their handles (`match`/`nomatch`, `true`/`false`, `error`); single-output
  nodes use the default (`null`) handle. The editor `Handle id` MUST equal the `sourceHandle` string.
- String `data` values support `{{expression}}` interpolation at runtime — never pre-interpolate in
  the editor or client-api.
- The engine must stay cycle-safe (visited-set / max-steps cap) — a new node type must not bypass it.

## Checklist

- [ ] Registry row added to `docs/workflow-engine.md`
- [ ] client-api validation added if the `data` shape has required keys (else intentionally skipped)
- [ ] `NodeExecutor` added + registered in the bot-api executor map; reactive I/O only
- [ ] Editor: node component + `nodeTypes` registration + palette entry + inspector section
- [ ] Handle ids match `sourceHandle` strings in the contract
- [ ] Engine unit test for the executor; `:bot-api:build`/`:client-api:build`/`npm test` pass
</content>
