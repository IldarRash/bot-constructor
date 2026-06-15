---
name: gen-test
description: Generate tests for Bot Constructor backend code (reactive WebFlux handlers, services, security). Use when asked to write or add tests for a Kotlin/Spring module. Bundles the project's reactive testing conventions (JUnit 5, MockK, reactor-test StepVerifier, WebTestClient).
disable-model-invocation: true
---

# gen-test

Generate tests that match this project's reactive stack: Spring Boot 4 (Spring Framework 7),
Spring Security 7 (lambda DSL), Kotlin 2.3, reactive MongoDB (`spring.mongodb.uri`). `client-api`
already has the needed test dependencies: JUnit 5 (`useJUnitPlatform`), **MockK** (`io.mockk`),
**reactor-test**, and `spring-security-test`. Other modules (e.g. `bot-api`) may only have
`spring-boot-starter-test` — add MockK / reactor-test to that module's `build.gradle` if the new
tests need them. Validation uses `jakarta.validation` (never `javax.*`).

## Steps

1. Identify the unit under test and read it. Decide the level:
   - **Service / pure reactive logic** → unit test with MockK mocks + `StepVerifier`.
   - **Handler + routing** → `WebTestClient` bound to the router, security context stubbed.
2. Place the test under `src/test/kotlin/...` mirroring the source package.
3. Mock collaborators with MockK; return `Mono`/`Flux` from stubs. Never use blocking calls.
4. Cover the happy path **and** the `InvalidRequestException` / error-resume path, since handlers
   translate domain errors to `400`.
5. Run `./gradlew :<module>:test --tests "<FQCN>"` and iterate until green.

## Patterns

Reactive service unit test (MockK + StepVerifier):

```kotlin
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class UserServiceTest {
    private val repo = mockk<UserRepository>()
    private val service = UserService(repo /* + other mocked deps */)

    @Test
    fun `signup returns created user view`() {
        every { repo.save(any()) } returns Mono.just(sampleUser())
        StepVerifier.create(service.signup(sampleRegistrationRequest()))
            .assertNext { assertThat(it.username).isEqualTo("alice") }
            .verifyComplete()
    }

    @Test
    fun `signup rejects duplicate username`() {
        every { repo.findByUsername("alice") } returns Mono.just(sampleUser())
        StepVerifier.create(service.signup(sampleRegistrationRequest()))
            .expectError(InvalidRequestException::class.java)
            .verify()
    }
}
```

Handler/route test (`WebTestClient` against the functional router):

```kotlin
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.server.RouterFunctions

val client = WebTestClient
    .bindToRouterFunction(WebConfig().route(userHandler))
    .build()

client.post().uri("/api/users/login")
    .bodyValue(UserAuthenticationRequest("alice", "pw"))
    .exchange()
    .expectStatus().isOk
```

For authenticated routes, stub `UserSessionProvider` (or use `spring-security-test`'s reactive
support) so `getCurrentUserSessionOrFail()` resolves a known user.

## Reminders

- Match existing package and naming style; keep each test focused on one behavior.
- Do not add tests that merely re-assert framework behavior (e.g. another bare `contextLoads`).
