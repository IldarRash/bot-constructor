# Bot Constructor

> A microservice platform for visually building and managing bots, with an RSocket API gateway fronting Spring Boot services and a React UI.

![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-Gateway-6DB33F?logo=spring&logoColor=white)
![RSocket](https://img.shields.io/badge/RSocket-transport-1f6feb)
![FlatBuffers](https://img.shields.io/badge/FlatBuffers-IDL-009688)
![React](https://img.shields.io/badge/React-UI-61DAFB?logo=react&logoColor=black)
![Gradle](https://img.shields.io/badge/Gradle-multi--module-02303A?logo=gradle&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)

## What & why

Bot Constructor is a constructor for creating bots, split into independent backend services behind an API gateway. The gateway uses **RSocket** to route requests to the appropriate services, authentication is isolated in a dedicated `auth-server`, and bot/template data models are defined once as **FlatBuffers** schemas (`bots-model-idl`, `client-model-idl`) so producers and consumers share a single binary contract. A React frontend (`client-ui`) lets users create and manage bots. The split keeps auth, routing, and the client API independently buildable and deployable.

## Architecture

```text
        ┌────────────┐        ┌──────────────┐
        │ client-ui  │  HTTP  │   gateway    │   API gateway
        │  (React)   │ ─────▶ │  (RSocket    │   routes requests to services
        └────────────┘        │   routing)   │
                              └──────┬───────┘
                       ┌─────────────┼──────────────┐
                       ▼             ▼              ▼
              ┌──────────────┐ ┌────────────┐ ┌────────────┐
              │ auth-server  │ │ client-api │ │  bot-api   │
              │  (authn)     │ │ users/bots │ │ (planned)  │
              └──────────────┘ └────────────┘ └────────────┘

  Shared binary contracts (FlatBuffers IDL):
    bots-model-idl    — schema for bot events
    client-model-idl  — schema for bot templates
```

- **gateway** — API gateway; uses RSocket to route requests to the backend services.
- **auth-server** — handles user authentication.
- **client-api** — the main API for managing users and bots.
- **bot-api** — bot-specific logic (to be implemented).
- **client-ui** — React-based UI for creating and managing bots.
- **bots-model-idl / client-model-idl** — FlatBuffers schemas shared across services.

Built on Spring Boot 3.3 with Spring Cloud, Kotlin 2.0 (JVM target 17), and Prometheus RSocket metrics.

## Getting started

### Prerequisites

- Java 17+
- Node.js 14+ and npm 6+ (for `client-ui`)
- Docker / Docker Compose (optional, to run the full stack)

### Build the backend

```bash
./gradlew build
```

### Run the frontend

```bash
cd client-ui
npm install
npm start
```

### Run everything with Docker Compose

```bash
docker compose up --build
```

| Service     | Port |
|-------------|------|
| gateway     | 8080 |
| auth-server | 8081 |
| client-api  | 8082 |
| client-ui   | 3000 |

Individual backend services can also be run from your IDE or via their generated JARs.

## Project structure

```text
gateway/           Spring Cloud API gateway (RSocket routing)
auth-server/       user authentication service
client-api/        main client API (users + bots)
bot-api/           bot-specific logic (to be implemented)
bots-model-idl/    FlatBuffers schema for bot events
client-model-idl/  FlatBuffers schema for bot templates
client-ui/         React frontend
docker-compose.yml full-stack local run
```
