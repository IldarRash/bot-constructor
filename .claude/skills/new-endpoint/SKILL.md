---
name: new-endpoint
description: Scaffold a new reactive HTTP endpoint in client-api as one vertical change — route in the functional router, handler method, request/response DTOs, and the matching security access rule. Use when adding an API endpoint to client-api.
disable-model-invocation: true
---

# new-endpoint

Add a `client-api` endpoint following the project's functional WebFlux style (router DSL +
`Mono<ServerResponse>` handlers), not `@RestController`. All endpoints live under `/api`. Stack:
Spring Boot 4 (Spring Framework 7) / Spring Security 7 / Kotlin 2.3 / reactive MongoDB
(`spring.mongodb.uri`).

## Steps

1. **DTOs** — add request/response data classes in `dto/` (keep them separate from `model/`
   persistence types). Use **`jakarta.validation`** annotations (e.g. `@field:NotBlank`) if the
   request needs validation — never the old `javax.*` namespace.
2. **Handler** — add a method to the relevant `@Service` handler in `api/` (e.g. `UserHandler`), or
   create a new handler class. Signature: `fun x(serverRequest: ServerRequest): Mono<ServerResponse>`.
   Resolve the current user via `UserSessionProvider.getCurrentUserSessionOrFail()` when the endpoint
   is authenticated. Translate domain errors with `.onErrorResume(::handleInvalidRequestException)`.
3. **Route** — register it in `config/WebConfig.kt` inside the `/api` nest, with the right HTTP verb.
4. **Security** — `config/SecurityConfig.kt` uses the **Spring Security 7 lambda DSL** (no `.and()`).
   Add a `permitAll()` matcher inside the `authorizeExchange { ... }` lambda only if the endpoint must
   be public; otherwise `anyExchange().authenticated()` covers it.
5. **Build & test** — `./gradlew :client-api:build`; add tests with the `gen-test` skill.

## Templates

Handler method:

```kotlin
fun createBot(serverRequest: ServerRequest): Mono<ServerResponse> =
    userSessionProvider.getCurrentUserSessionOrFail()
        .zipWith(serverRequest.bodyToMono(CreateBotRequest::class.java))
        .flatMap { (session, req) -> botService.create(req, session) }
        .flatMap { ok().bodyValue(it) }
        .onErrorResume(::handleInvalidRequestException)
```

Route registration (inside the existing `router { "/api".nest { accept(APPLICATION_JSON).nest { ... } } }`):

```kotlin
POST("/bots", botHandler::createBot)
GET("/bots", botHandler::listBots)
```

Security (lambda DSL — current shape of `securityWebFilterChain`; add public matchers here only):

```kotlin
http.authorizeExchange { ex ->
    ex.pathMatchers(HttpMethod.POST, "/api/users", "/api/users/login").permitAll()
    ex.pathMatchers(HttpMethod.GET, "/actuator/**", "/actuator").permitAll()
    // add new PUBLIC paths above; everything else stays authenticated
    ex.anyExchange().authenticated()
}
```

## Checklist

- [ ] DTOs in `dto/`, `jakarta.validation` annotations added if needed (no `javax.*`)
- [ ] Handler returns `Mono<ServerResponse>`, no blocking calls
- [ ] Route added under `/api` with correct verb
- [ ] Security rule updated (public) inside the lambda DSL, or intentionally left authenticated
- [ ] Error path returns 400 via `InvalidRequestException`
- [ ] `./gradlew :client-api:build` passes + a test added
