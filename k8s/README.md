# Deploying bot-constructor on minikube

Plain Kubernetes manifests for the trimmed stack: **mongo + client-api + gateway + client-ui**.
All resources live in the `bot-constructor` namespace.

## Architecture

```
            ┌────────────── ingress (host: bot.local) ──────────────┐
            │  /      -> client-ui:80   (Vite SPA served by nginx)   │
            │  /api   -> gateway:8080   (routes /api/** -> client-api)│
            └────────────────────────────────────────────────────────┘
client-ui (nginx) ── /api/ proxy ──> gateway:8080 ──> client-api:9000 ──> mongo:27017
```

Two paths reach the API:
- via the **ingress** `/api` rule directly to the gateway, and
- via the **UI's own nginx** (`client-ui/nginx.conf`), which proxies `/api/` to `gateway:8080`.
  This means the frontend works even if the ingress `/api` rule is absent.

## Prerequisites

- `minikube` and `kubectl` installed.
- Docker available to minikube's build (the steps below build images directly inside minikube,
  so no external registry is needed).

## 1. Start minikube and enable ingress

```bash
minikube start
minikube addons enable ingress
```

## 2. Build the images directly into minikube

Run these from the **repo root** (the build context must be the repo root so Gradle can see
`settings.gradle` and every module). `minikube image build` puts the image straight into the
cluster's container runtime, so `imagePullPolicy: IfNotPresent` finds it locally.

```bash
minikube image build -t bot-constructor/client-api:local -f client-api/Dockerfile .
minikube image build -t bot-constructor/gateway:local    -f gateway/Dockerfile .
minikube image build -t bot-constructor/client-ui:local  -f client-ui/Dockerfile .
```

(`auth-server` has a Dockerfile too if you want it:
`minikube image build -t bot-constructor/auth-server:local -f auth-server/Dockerfile .`)

> **Re-deploying after a code change (important):** the manifests use `:local` with
> `imagePullPolicy: IfNotPresent`, so minikube will keep serving the *cached* `:local` image even
> after you rebuild — rolling the deployment is not enough. Either build with a **fresh unique tag**
> and point the deployment at it:
> ```bash
> docker build -t bot-constructor/client-api:v2 -f client-api/Dockerfile .   # host Docker is more reliable than `minikube image build`
> minikube image load bot-constructor/client-api:v2
> kubectl -n bot-constructor set image deployment/client-api client-api=bot-constructor/client-api:v2
> ```
> or remove the cached image first (`minikube image rm bot-constructor/client-api:local`) before
> reloading `:local` and `kubectl rollout restart`.

## 3. Apply the manifests

```bash
kubectl apply -f k8s/
```

This creates the namespace, the mongo Secret/Deployment/Service, and the
Deployments/Services for client-api, gateway, and client-ui, plus the ingress.

## 4. Watch the pods come up

```bash
kubectl get pods -n bot-constructor -w
```

Wait until all pods are `Running` and `READY 1/1`. client-api waits on its
`/actuator/health` readiness probe, so it may take a little longer than the others.

## 5. Map bot.local to the minikube IP

```bash
minikube ip          # e.g. 192.168.49.2
```

Add the line below to your hosts file (`/etc/hosts` on Linux/macOS,
`C:\Windows\System32\drivers\etc\hosts` on Windows), substituting the IP from above:

```
192.168.49.2  bot.local
```

> **Windows/macOS (Docker driver):** the minikube IP is inside Docker's network and is **not**
> routable from the host, so `http://bot.local/` won't connect directly. Run `minikube tunnel` in a
> separate terminal (binds the ingress to `127.0.0.1` — map `bot.local` to `127.0.0.1` instead), or
> skip the ingress and use a port-forward:
> ```bash
> kubectl port-forward -n bot-constructor svc/client-ui 8088:80    # UI  -> http://localhost:8088
> kubectl port-forward -n bot-constructor svc/gateway   18080:8080 # API -> http://localhost:18080/api/...
> ```

## 6. End-to-end smoke test

Open the UI in a browser:

```
http://bot.local/
```

Or curl the API through the ingress:

```bash
curl -i http://bot.local/api/...        # via the ingress /api rule -> gateway -> client-api
```

You can also verify the gateway health from inside the cluster:

```bash
kubectl exec -n bot-constructor deploy/client-ui -- wget -qO- http://gateway:8080/api/...
```

## Teardown

```bash
kubectl delete -f k8s/
# or just: kubectl delete namespace bot-constructor
```
