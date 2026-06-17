# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Bot Constructor is a microservice platform for visually building and managing chat bots. The backend
is Kotlin 2.3 / Spring Boot 4.0 (fully reactive WebFlux), fronted by a Spring Cloud Gateway that
HTTP-routes `/api/**` across two services: `/api/runtime/**` → **bot-api** (the workflow runtime
engine), everything else → **client-api**. The frontend is a React 19 + Vite SPA using React Flow for
the bot editor. Persistence is reactive MongoDB; auth is stateless JWT.

## Build & Test

Multi-module Gradle build (Gradle 9, Java 25 toolchain auto-resolved via the foojay plugin). On
Windows use `gradlew.bat`; examples use `./gradlew`. Running Gradle itself needs JDK 17+; the build
compiles/tests on a JDK 25 toolchain.

```bash
./gradlew build                  # compile + test all backend modules
./gradlew :client-api:test       # run client-api tests
./gradlew :client-api:bootJar    # build the runnable jar (JVM)
./gradlew :client-api:nativeCompile  # GraalVM native executable (needs a GraalVM JDK 25)
```

The JVM jars run on a **JDK 25** runtime (Boot 4 / Java 25 bytecode). The deploy target is **GraalVM
native-image** (see *Native image* below); `gateway`, `client-api`, and `bot-api` apply the
`org.graalvm.buildtools.native` plugin. Frontend (`client-ui`, Vite):

```bash
cd client-ui && npm install
npm run dev        # Vite dev server on :3002 (proxies /api -> gateway)
npm run build      # production build -> dist/
npm test           # Vitest
```

Easiest full local run: `./run-local.sh` (Mongo via Docker + both services + UI). Full stack on
Kubernetes: see `k8s/README.md` (verified on minikube).

`client-api` carries the bulk of the tests (MockK, `reactor-test`, `spring-security-test`,
`WebTestClient`); `bot-api` has engine tests covering the workflow runtime and the sandboxed JS
evaluator (`engine/ExpressionAndCodeTest`, `engine/ExpressionEvaluatorSandboxTest`).

## Module Architecture

The root `build.gradle` applies Kotlin, `kotlin-spring`, Spring Boot 4.0, and the Spring Cloud
2025.1.0 BOM to every subproject, with a Java 25 toolchain and `jvmTarget = JVM_25`. `settings.gradle`
includes `gateway`, `auth-server`, `client-api`, and `bot-api` (the React app is built via npm/Docker,
not Gradle).

- **gateway** — Spring Cloud Gateway (WebFlux). Single HTTP entry point
  (`gateway/src/main/resources/application.yml`, under the `spring.cloud.gateway.server.webflux.*`
  namespace). Order matters — more specific predicates first: `Path=/api/runtime/**` → `bot-api`,
  `Path=/api/**` → `client-api`, and `Path=/rsocket/**` → `client-api` over a `ws://` URI (the
  WebSocket routing filter only proxies the RSocket upgrade when the routed scheme is `ws`/`wss`).
  Dependency is `spring-cloud-starter-gateway-server-webflux`. The frontend talks only to the gateway.
- **client-api** — The main API (users + owner-scoped bots, credentials, executions, scheduling) and
  RSocket board collaboration. Reactive WebFlux + reactive MongoDB + JWT. The largest module; most
  feature work lands here.
- **bot-api** — The **workflow runtime engine** (port 8083). Executes a bot's node/edge flow; serves
  `/api/runtime/**`. Hosts a **sandboxed GraalVM polyglot JS engine** (`engine/ExpressionEvaluator`)
  for inline `{{= … }}` expressions and the Code node. Calls back into client-api (`CLIENT_API_URI`).
  Reactive WebFlux; no MongoDB of its own.
- **auth-server** — Experimental standalone RSocket auth service (`@EnableRSocketSecurity`). **Not on
  the request path** of the running app; auth lives in client-api.
- **client-ui** — React 19 + Vite + React Flow (`@xyflow/react`) + React Router 7.

## Auth & Security

- **client-api** uses reactive HTTP security (`@EnableWebFluxSecurity`, Spring Security 7 **lambda
  DSL** — no `.and()` chaining). Routes are declared functionally with the Kotlin router DSL in
  `config/WebConfig.kt` (not `@RestController`), all under `/api`. `config/SecurityConfig.kt` permits
  `POST /api/users`, `POST /api/users/login`, and `/actuator/**`; everything else is authenticated.
  A custom `AuthenticationWebFilter` validates JWTs (JJWT 0.12) and puts a `TokenPrincipal` into the
  reactive security context. Resolve the current user via
  `UserSessionProvider.getCurrentUserSessionOrFail()`.
- Bot operations are **owner-scoped**: `BotService` checks `ownerId` against the authenticated user
  and returns `InvalidRequestException("Bot", "not found")` for both missing and not-owned (no IDOR
  leak). Login is by **email** + password; the JWT header is `Authorization: Token <jwt>` (literal
  `Token ` prefix, not Bearer).

When adding a `client-api` endpoint: add the route in `WebConfig.kt`, a handler method returning
`Mono<ServerResponse>`, DTOs in `dto/`, and update `SecurityConfig.kt` only if it must be public.

## Reactive conventions

All backend I/O is non-blocking (`Mono`/`Flux`, `reactor-kotlin-extensions`). Persistence is reactive
MongoDB. Domain validation errors use `InvalidRequestException`, translated to a `400` body by the
handler's `.onErrorResume(::handleInvalidRequestException)` (see `UserHandler`/`BotHandler`).

## Native image (GraalVM)

The request-path services (`gateway`, `client-api`, `bot-api`) target **GraalVM native-image** for
deployment — fast startup (~tens of ms) and a much smaller memory footprint, which is what makes the
local minikube stack stable. Each applies `org.graalvm.buildtools.native` (1.1.x; declared in the
root `build.gradle`, applied per-module — `auth-server` is intentionally excluded). Build with
`:<mod>:nativeCompile` on a **GraalVM JDK 25**, or `:<mod>:bootBuildImage` (Paketo supplies GraalVM).
Native Dockerfiles live alongside the JVM ones as `Dockerfile.native`.

- **`org.graalvm.polyglot` ≠ native-image.** `bot-api` depends on `org.graalvm.polyglot:js-community`
  purely for the **sandboxed JS engine** (`engine/ExpressionEvaluator`). That is an embedded-language
  library, unrelated to whether the service itself is AOT-compiled to a native binary. Don't conflate
  the two.
- **bot-api builds with Oracle GraalVM, not Community.** `ExpressionEvaluator`'s runaway-loop backstop
  uses `ResourceLimits.statementLimit`, an **Oracle GraalVM-only** sandbox feature absent from
  Community (`js-community`). The native image must bundle the JS language (`--language:js`, wired in
  `bot-api/build.gradle`). On Community the statement limit silently no-ops — the wall-clock
  `Context.interrupt` (~2s) is then the only backstop (regression-guarded by
  `ExpressionEvaluatorSandboxTest`). `gateway`/`client-api` build fine on GraalVM CE.
- **Reachability hints.** Spring AOT + the GraalVM Reachability Metadata Repository cover most deps.
  The hand-written gaps live in `client-api` `config/NativeHints.kt`: JJWT 0.12 (reflective/ServiceLoader
  init) and the functionally-routed Jackson DTOs/entities (invisible to AOT since the router DSL hides
  their types). Confirm/extend with the native-image tracing agent during a native spike.

## Gotchas

- **MongoDB config prefix (Boot 4):** Spring Boot 4 moved Mongo properties from `spring.data.mongodb.*`
  to **`spring.mongodb.*`** (the old prefix is silently ignored → `credential=null` / auth failures).
  client-api uses `spring.mongodb.uri` (overridable via `SPRING_MONGODB_URI`).
- **Java 25 required:** sources compile on a JDK 25 toolchain (Gradle resolves it). JVM jars are Java
  25 bytecode and run on a JDK 25 (the legacy `Dockerfile`s use `eclipse-temurin:25`); native images
  build on a GraalVM JDK 25. Native build images have no `native-image` on temurin — see *Native image*.
- **Gateway port:** runs on `:8080` by default; `run-local.sh` uses `:8090` locally because 8080 is
  often taken. The UI's Vite proxy / nginx must target the gateway, never client-api directly.
- **Spring Cloud ↔ Boot compatibility:** Spring Cloud 2025.1.0 pairs with Spring Boot **4.0.x** (not
  4.1.x). Keep them in step or the gateway refuses to start.
