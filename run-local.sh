#!/usr/bin/env bash
#
# run-local.sh — bring up the Bot Constructor MVP locally (Git Bash on Windows).
#
# Proven recipe from the MVP bring-up. Run from the repo root in your own
# terminal (not via an automation harness, which may reap the JVMs):
#
#   ./run-local.sh
#
# Stack: MongoDB (docker) -> client-api :9000 -> gateway :8090 <- UI :3002
# Open http://localhost:3002  (register -> create a bot -> edit on the canvas).
# NOTE: gateway runs on 8090, not 8080 — another app (ZIO Http) squats 8080 on
# this machine. The UI proxy in client-ui/package.json must match GATEWAY_PORT.
#
# Prerequisites (see also CLAUDE.md):
#   - JDK 17  (Spring Boot 3.3 rejects JDK 16)
#   - flatc 2.0.0 on PATH for the FlatBuffers codegen in client-api
#   - Docker (for MongoDB) and Node/npm (for the UI)
set -euo pipefail
cd "$(dirname "$0")"

# --- toolchain (override via env if your paths differ) ---
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Java/jdk-17.0.1}"
FLATC_DIR="${FLATC_DIR:-/c/Users/ilsac/tools/flatc}"
[ -d "$FLATC_DIR" ] && export PATH="$FLATC_DIR:$PATH"
UI_PORT="${UI_PORT:-3002}"        # 3000/3001 may be taken by other apps
GATEWAY_PORT="${GATEWAY_PORT:-8090}"  # 8080 is squatted by another app here

echo ">> JAVA_HOME=$JAVA_HOME"
command -v flatc >/dev/null 2>&1 || echo "!! flatc not on PATH — client-api build will fail (set FLATC_DIR)"

# --- 1. MongoDB (root/example, auth db admin) ---
if ! docker ps --format '{{.Names}}' | grep -q '^bot-mongo$'; then
  if docker ps -a --format '{{.Names}}' | grep -q '^bot-mongo$'; then
    docker start bot-mongo
  else
    docker run -d --name bot-mongo -p 27017:27017 \
      -e MONGO_INITDB_ROOT_USERNAME=root -e MONGO_INITDB_ROOT_PASSWORD=example mongo:6
  fi
fi
echo ">> mongo: $(docker ps --filter name=bot-mongo --format '{{.Status}}')"

# --- 2. build the JVM jars ---
echo ">> building client-api + gateway ..."
./gradlew :client-api:bootJar :gateway:bootJar --console=plain

JAVA="$JAVA_HOME/bin/java"

# --- 3. launch services (background; logs in .run-*.log) ---
echo ">> starting client-api :9000"
"$JAVA" -jar client-api/build/libs/client-api.jar > .run-client-api.log 2>&1 &
echo ">> starting gateway :$GATEWAY_PORT"
"$JAVA" -jar gateway/build/libs/gateway-0.0.1-SNAPSHOT.jar --server.port="$GATEWAY_PORT" > .run-gateway.log 2>&1 &

# --- 4. UI dev server (CRA proxies /api -> gateway:8080) ---
echo ">> starting UI :$UI_PORT"
( cd client-ui && [ -d node_modules ] || npm install --legacy-peer-deps; \
  BROWSER=none PORT="$UI_PORT" npm start > ../.run-ui.log 2>&1 ) &

cat <<EOF

Stack starting. Wait ~30s, then open:  http://localhost:$UI_PORT
Logs: .run-client-api.log  .run-gateway.log  .run-ui.log
Stop: kill the background java/node processes (jobs -p) and 'docker stop bot-mongo'.
EOF
