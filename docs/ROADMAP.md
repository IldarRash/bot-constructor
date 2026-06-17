# Bot Constructor — n8n functional parity: status & future work

This file records what the workflow-automation feature already does (the goal was to take the
n8n-*style* editor to n8n *functional* parity) and what is intentionally left for later. The
authoritative per-node / per-endpoint contract lives in [`workflow-engine.md`](workflow-engine.md);
this file is the high-level status + roadmap.

## Delivered (A–F)

| Phase | Area | What shipped |
|-------|------|--------------|
| **A** | Item-based engine | Runtime rewritten to n8n's item model — data flows node→node as `ExecutionItem[]`; a dataflow work-queue scheduler with an executor registry. Chat semantics (reply/keyword/`vars`) preserved exactly. |
| **B** | Expressions + connectors | Sandboxed **GraalVM JS** — opt-in `{{= js }}` expressions and a **Code** node (`$items`/`$vars`). Four real connectors over the SSRF-hardened path: **Telegram**, **Claude (Anthropic)**, **Slack**, **Discord**. |
| **C** | Library nodes | **IF**, **Switch**, **Filter**, **Set Fields**, **Split Out** (iterate an array field), **No-Op**, **Wait** (async delay). |
| **C.2** | Merge + Loop | Scheduler markers: **Merge** (`GatherExecutor` — wait for all forward inputs, dead-branch finalization) and **Loop / Split-in-Batches** (`LoopExecutor` — feedback-edge re-entry with per-node state). Normal-node path unchanged. |
| **D** | Execution history | `Execution` Mongo doc (owner-scoped), bot-api fire-and-forget emit → client-api persist, owner-scoped read API, and the n8n signature: **click a node → its input/output items** for a run. |
| **E** | Credentials | AES-256-GCM encrypted, key from env; metadata-only API; an owner-scoped, gateway-blocked internal decrypt-fetch (anti-IDOR); runtime injection into connectors/httpRequest. Secrets never hit history/traces/logs. |
| **F** | Manual run + pinned data | Owner-scoped `POST /api/runtime/bots/{id}/execute` (trigger `manual`, rich per-node response). **Pinned data**: a pinned node skips its executor (no HTTP/connector/code side effects) and emits the pinned output — iterate without re-calling external APIs. |

**Security posture held across all phases:** SSRF hardening (resolver-pinned IP allow-list, no redirects,
256 KB / 5 s caps), per-walk `MAX_HTTP_REQUESTS=5`, 20 s walk timeout, `MAX_STEPS=100`, connector-URL
redaction in traces, credential secret isolation. The GraalVM sandbox runs off the event loop with a
statement limit + a wall-clock watchdog.

## Ops requirements

- **`CREDENTIAL_ENCRYPTION_KEY`** — Base64 of 32 random bytes; **must** be set in every non-local
  environment (without it, client-api logs a loud warning and uses an insecure dev key).
- Runtime needs **JDK 25** to run the jars (Java 25 bytecode); Gradle resolves a JDK 25 toolchain to
  build. Local bring-up: `./run-local.sh` (Mongo via Docker + client-api + bot-api + gateway + Vite UI).

## Future work / deferred

### Engine / nodes
- **Merge**: dynamic modes beyond `append` (by-key / combine); run an empty Merge (a gather that
  settles all inputs but received zero items currently does not fire).
- **Loop**: surface the per-iteration cap explicitly (today `MAX_STEPS` is the effective bound, so the
  `MAX_BATCHES_PER_LOOP` constant is dead); a "collect results" done-mode.
- **Switch**: more than two value cases / rule-based routing with dynamic outputs.
- More connectors (Google Sheets/OAuth, generic REST templates, email/SMTP-over-API), and moving the
  remaining connector secrets fully onto credentials in the UI by default.

### Quality nits carried from reviews (non-blocking)
- bot-api: `ManualRun`/record timestamps typed as `String` vs client-api `Instant` (round-trips via
  Jackson); cap the size of inbound `pinnedData`; pin currently captures only the default output handle;
  fold the "first-item-or-synthetic" helper; trigger trace not via the shared `traceStep` helper.
- client-api: drop the unused `countByBotIdAndOwnerId`; `resolveForBot` decrypt failure should map to
  empty rather than risk a 500.
- client-ui: extract an `<OperatorSelect>` + an `<Inspector>` wrapper to shrink the ~1400-line
  `BotEditor.js` inspector boilerplate.

### Bigger directions
- Distributed scheduler lock (the cron `ScheduledFlowRunner` double-fires across replicas).
- Rate limiting on the public webhook endpoint (gateway `RequestRateLimiter` + Redis).
- Workflow versioning / rollback; sub-workflows.
