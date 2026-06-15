---
name: run-stack
description: Launch and verify the Bot Constructor stack — backend services, the Vite UI, or the full Docker/minikube stack — so a feature can be confirmed working end-to-end. Use when verifying a change in the running app or bringing services up locally.
disable-model-invocation: true
---

# run-stack

Bring the system up and exercise a feature. Used to gate a feature as "done" (verified running, not
just compiling). On Windows use `gradlew.bat`; examples use `./gradlew`. The toolchain is **JDK 25**.

## Topology & ports

```
client-ui (Vite, :3002) ──/api──▶ gateway (HTTP :8090) ──▶ client-api (:9000)
                         └/rsocket▶ gateway ──▶ client-api RSocket (board collaboration)
                                            └──▶ bot-api (:8083) under /api/runtime/**
```

| Service     | Port (local) | Notes |
|-------------|--------------|-------|
| gateway     | 8090         | the only entry point the UI uses; spring-cloud-starter-gateway-server-webflux |
| auth-server | 8081         | RSocket security / token issuing |
| client-api  | 9000         | reactive WebFlux + MongoDB; exposes RSocket `/rsocket` for board collaboration |
| bot-api     | 8083         | runtime engine; reached via gateway under `/api/runtime/**` |
| client-ui   | 3002         | Vite dev server (proxies `/api` and `/rsocket` to gateway) |

HTTP routes are configured under `spring.cloud.gateway.server.webflux.*`. The gateway also proxies
the `/rsocket` WebSocket for real-time **board collaboration** (RSocket is no longer used for routing
between gateway and services). `client-api` needs a reachable MongoDB (configured via
`spring.mongodb.uri`).

## Options

### Individual backend service
```bash
./gradlew :gateway:bootRun
./gradlew :auth-server:bootRun
./gradlew :client-api:bootRun     # needs MongoDB available
./gradlew :bot-api:bootRun        # runtime engine; calls client-api with forwarded JWT
```

### Run a built jar with JDK 25 (when gradle's toolchain is not on PATH)
```bash
C:/Users/ilsac/.jdks/openjdk-25.0.1/bin/java -jar client-api/build/libs/client-api-*.jar
```

### Frontend dev server
```bash
cd client-ui && npm install   # first run only
npm run dev                   # http://localhost:3002
```

### Full stack (Docker)
```bash
docker compose up --build
```

### minikube (k8s)
```bash
minikube start
# build/push images, then apply the k8s manifests, then:
minikube service gateway --url        # get the gateway URL to curl/point the UI at
kubectl get pods                      # confirm all services are Running/Ready
kubectl logs deploy/<service>         # check logs
```

## Verify a feature

1. Bring up the layers the feature touches (UI → gateway → service; include `auth-server`/MongoDB if
   auth or persistence is involved, `bot-api` for runtime, RSocket for collaboration).
2. Exercise the path:
   - login through the gateway:
     `curl -i -X POST http://localhost:8090/api/users/login -H 'Content-Type: application/json' -d '{"email":"a@b.com","password":"pw"}'`
   - authenticated call with the returned token (note `Token`, not `Bearer`):
     `curl -i http://localhost:8090/api/user -H "Authorization: Token <token>"`
   - bot runtime: `POST /api/runtime/bots/{id}/sessions` then
     `POST /api/runtime/sessions/{sessionId}/messages` (see the `bot-runtime` skill).
   - board collaboration: open the editor in **two** browser tabs at `:3002` and confirm presence +
     live node/edge changes propagate over `/rsocket` (see the `rsocket-collab` skill).
   - or drive it in the browser at `:3002`.
3. Confirm the expected status/body and check service logs for errors.
4. Tear down (`Ctrl-C`, `docker compose down`, or `minikube stop`).

## Reporting

State exactly what you launched, the command(s) used to exercise the feature, the observed
result (status codes / UI behavior / log lines), and whether it is **verified** or **blocked** (with
the concrete blocker). Compilation/tests passing alone is not "verified".
