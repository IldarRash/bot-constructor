# Bot Constructor

> A microservice platform for visually building and managing chat bots — a Spring Cloud Gateway
> fronting reactive Kotlin/Spring services, with a React + React Flow editor for designing bots.

![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F?logo=springboot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-Gateway-6DB33F?logo=spring&logoColor=white)
![Java](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-7-646CFF?logo=vite&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-9-02303A?logo=gradle&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-ready-326CE5?logo=kubernetes&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

## What & why

Bot Constructor lets a user register, then visually design bots — each bot is a set of **questions**
(with trigger keywords) and an **answer**, arranged on a drag-and-drop canvas. The system is split
into independently deployable services behind an **API gateway**:

- The **gateway** is the single entry point; it routes `/api/**` to the backend over HTTP.
- The **client-api** owns users and bots: stateless **JWT auth**, **owner-scoped** bot CRUD, and
  reactive persistence to **MongoDB**.
- The **React** single-page app talks only to the gateway and renders the bot editor with
  **React Flow**.

The whole stack is fully reactive (Spring WebFlux, Reactor, reactive MongoDB) and runs locally with
one script or on **Kubernetes** (verified on minikube).

## Architecture

```text
   ┌────────────┐   HTTP    ┌──────────────┐   HTTP /api/**   ┌────────────────┐
   │ client-ui  │ ───────▶  │   gateway     │ ───────────────▶ │   client-api    │
   │ React+Vite │  :8080/   │ Spring Cloud  │                  │ WebFlux + JWT   │
   │  :3000     │   proxy   │   Gateway     │                  │ users + bots    │
   └────────────┘           └──────────────┘                  └───────┬────────┘
                                                                       │ reactive
                                                                       ▼
                                                                ┌────────────┐
                                                                │  MongoDB   │
                                                                └────────────┘
   auth-server (:8081) — standalone RSocket auth service (experimental, not on the request path)
```

| Module        | Stack                                   | Responsibility                              |
|---------------|-----------------------------------------|---------------------------------------------|
| `gateway`     | Spring Cloud Gateway (WebFlux)          | Single entry point; routes `/api/**`        |
| `client-api`  | Spring WebFlux, Security, reactive Mongo| Users, JWT auth, owner-scoped bot CRUD      |
| `auth-server` | Spring Security RSocket                 | Experimental standalone auth (not wired in) |
| `client-ui`   | React 19, Vite, React Flow              | Auth screens + visual bot editor            |

## Tech stack

- **Backend** — Kotlin 2.3, Spring Boot 4.0, Spring Cloud 2025.1.0, Spring WebFlux, Spring Security
  (JWT via JJWT), reactive MongoDB, Java 25, Gradle 9.
- **Frontend** — React 19, Vite 7, React Flow 12 (`@xyflow/react`), React Router 7.
- **Infra** — Docker (multi-stage), Kubernetes manifests, GitHub Actions CI.

## Features

- 🔐 Register / log in with stateless JWT auth.
- 🤖 Create, list, edit and delete bots — every operation scoped to the owner (no cross-user access).
- 🎛️ Visual editor: drag question nodes onto a React Flow canvas, set keywords + the bot's answer,
  choose a platform type (Telegram / Instagram / VK).
- 🚪 Everything routed through the gateway; the SPA never talks to services directly.

## Quick start (local)

Prerequisites: **JDK 25**, **Node 20+**, **Docker** (for MongoDB).

```bash
./run-local.sh
```

This starts MongoDB (Docker), builds and runs `client-api` + `gateway`, and launches the Vite dev
server. Then open **http://localhost:3002** and register → create a bot → design it on the canvas.

> The gateway runs on `:8090` locally (port 8080 may be taken on dev machines); the UI's Vite proxy
> targets it automatically.

### Run on Kubernetes (minikube)

```bash
minikube start
minikube addons enable ingress
# build images straight into the cluster
for s in client-api gateway client-ui; do
  minikube image build -t bot-constructor/$s:local -f $s/Dockerfile .
done
kubectl apply -f k8s/
kubectl -n bot-constructor get pods
```

See [`k8s/README.md`](k8s/README.md) for the full deploy + access steps.

## API

All endpoints are under `/api` and (except signup/login) require `Authorization: Token <jwt>`.

| Method | Path                 | Description                    |
|--------|----------------------|--------------------------------|
| POST   | `/api/users`         | Register (returns a JWT)       |
| POST   | `/api/users/login`   | Log in (returns a JWT)         |
| GET    | `/api/user`          | Current user                   |
| POST   | `/api/bots`          | Create a bot                   |
| GET    | `/api/bots`          | List the current user's bots   |
| GET    | `/api/bots/{id}`     | Get one owned bot              |
| PUT    | `/api/bots/{id}`     | Update an owned bot            |
| DELETE | `/api/bots/{id}`     | Delete an owned bot            |

## Build & test

```bash
./gradlew build                 # compile + test all backend modules (JDK 25 toolchain)
./gradlew :client-api:test      # run client-api tests (MockK + StepVerifier + WebTestClient)
cd client-ui && npm install && npm run build   # build the SPA
```

## Screenshots

> _Add screenshots/GIFs of the login screen and the React Flow bot editor here_
> (`docs/login.png`, `docs/editor.gif`). Capture them from http://localhost:3002 after `run-local.sh`.

## License

[MIT](LICENSE)
