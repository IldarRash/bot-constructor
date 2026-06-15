# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Bot Constructor is a microservice platform for visually building and managing chat bots. The backend
is Kotlin 2.3 / Spring Boot 4.0 (fully reactive WebFlux), fronted by a Spring Cloud Gateway that
HTTP-routes `/api/**` to the services. The frontend is a React 19 + Vite SPA using React Flow for the
bot editor. Persistence is reactive MongoDB; auth is stateless JWT.

## Build & Test

Multi-module Gradle build (Gradle 9, Java 25 toolchain auto-resolved via the foojay plugin). On
Windows use `gradlew.bat`; examples use `./gradlew`. Running Gradle itself needs JDK 17+; the build
compiles/tests on a JDK 25 toolchain.

```bash
./gradlew build                 # compile + test all backend modules
./gradlew :client-api:test      # run client-api tests
./gradlew :client-api:bootJar   # build the runnable jar
```

Run the jars with a **JDK 25** runtime (Boot 4 / Java 25 bytecode). Frontend (`client-ui`, Vite):

```bash
cd client-ui && npm install
npm run dev        # Vite dev server on :3002 (proxies /api -> gateway)
npm run build      # production build -> dist/
npm test           # Vitest
```

Easiest full local run: `./run-local.sh` (Mongo via Docker + both services + UI). Full stack on
Kubernetes: see `k8s/README.md` (verified on minikube).

Only `client-api` carries real tests (MockK, `reactor-test`, `spring-security-test`, `WebTestClient`).

## Module Architecture

The root `build.gradle` applies Kotlin, `kotlin-spring`, Spring Boot 4.0, and the Spring Cloud
2025.1.0 BOM to every subproject, with a Java 25 toolchain and `jvmTarget = JVM_25`. `settings.gradle`
includes only `gateway`, `auth-server`, `client-api` (the React app is built via npm/Docker, not
Gradle).

- **gateway** — Spring Cloud Gateway (WebFlux). Single HTTP entry point; routes `Path=/api/**` to
  `client-api` (`gateway/src/main/resources/application.yml`, under the
  `spring.cloud.gateway.server.webflux.*` namespace). Dependency is
  `spring-cloud-starter-gateway-server-webflux`. The frontend talks only to the gateway.
- **client-api** — The main API (users + owner-scoped bots). Reactive WebFlux + reactive MongoDB +
  JWT. The largest module; most feature work lands here.
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

## Gotchas

- **MongoDB config prefix (Boot 4):** Spring Boot 4 moved Mongo properties from `spring.data.mongodb.*`
  to **`spring.mongodb.*`** (the old prefix is silently ignored → `credential=null` / auth failures).
  client-api uses `spring.mongodb.uri` (overridable via `SPRING_MONGODB_URI`).
- **Java 25 runtime required:** jars are Java 25 bytecode; run them on a JDK 25 (Docker images use
  `eclipse-temurin:25`). Gradle resolves a JDK 25 toolchain to compile.
- **Gateway port:** runs on `:8080` by default; `run-local.sh` uses `:8090` locally because 8080 is
  often taken. The UI's Vite proxy / nginx must target the gateway, never client-api directly.
- **Spring Cloud ↔ Boot compatibility:** Spring Cloud 2025.1.0 pairs with Spring Boot **4.0.x** (not
  4.1.x). Keep them in step or the gateway refuses to start.
