#!/usr/bin/env bash
#
# run-local.sh — bring up the Bot Constructor stack locally (Git Bash on Windows).
#
# Run from the repo root in YOUR OWN terminal (an automation harness may reap the JVMs):
#
#   ./run-local.sh
#
# Stack:  MongoDB (docker)  ->  client-api :9000   \
#                               bot-api    :8083    ->  gateway :8090  <-  Vite UI :3002
# Open http://localhost:3002  (register -> create a bot -> edit on the canvas -> Run).
#
# The gateway routes /api/runtime/** -> bot-api (the workflow runtime) and /api/** -> client-api.
# Gateway runs on 8090 to match the Vite proxy default (client-ui/vite.config.js).
#
# Prerequisites:
#   - JDK 17+ to RUN Gradle; a JDK 25 to RUN the jars (Boot 4 / Java 25 bytecode).
#   - Docker (MongoDB) and Node/npm (the Vite UI).
set -euo pipefail
cd "$(dirname "$0")"

# --- toolchain (override via env if your paths differ) ---
GRADLE_JAVA_HOME="${GRADLE_JAVA_HOME:-/c/Program Files/Java/jdk-17.0.1}"   # runs Gradle
RUN_JAVA_HOME="${RUN_JAVA_HOME:-/c/Users/ilsac/.jdks/openjdk-25.0.1}"      # runs the Java 25 jars
UI_PORT="${UI_PORT:-3002}"
GATEWAY_PORT="${GATEWAY_PORT:-8090}"
# AES-256-GCM key for credential encryption (Base64 of 32 bytes). Dev-only default; set your own.
export CREDENTIAL_ENCRYPTION_KEY="${CREDENTIAL_ENCRYPTION_KEY:-MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=}"

JAVA="$RUN_JAVA_HOME/bin/java"
echo ">> Gradle JAVA_HOME=$GRADLE_JAVA_HOME"
echo ">> runtime java=$JAVA"
"$JAVA" -version 2>&1 | head -1

# --- 1. MongoDB (root/example, authSource admin) — matches client-api's default SPRING_MONGODB_URI ---
if ! docker ps --format '{{.Names}}' | grep -q '^bot-mongo$'; then
  if docker ps -a --format '{{.Names}}' | grep -q '^bot-mongo$'; then
    docker start bot-mongo
  else
    docker run -d --name bot-mongo -p 27017:27017 \
      -e MONGO_INITDB_ROOT_USERNAME=root -e MONGO_INITDB_ROOT_PASSWORD=example mongo:6
  fi
fi
echo ">> mongo: $(docker ps --filter name=bot-mongo --format '{{.Status}}')"

# --- 2. build the runnable jars ---
echo ">> building client-api + bot-api + gateway jars ..."
JAVA_HOME="$GRADLE_JAVA_HOME" ./gradlew.bat :client-api:bootJar :bot-api:bootJar :gateway:bootJar --console=plain

# --- 3. launch services (background; logs in .run-*.log) ---
echo ">> starting client-api :9000"
"$JAVA" -jar client-api/build/libs/client-api.jar > .run-client-api.log 2>&1 &
echo ">> starting bot-api :8083"
"$JAVA" -jar bot-api/build/libs/bot-api.jar > .run-bot-api.log 2>&1 &
echo ">> starting gateway :$GATEWAY_PORT"
"$JAVA" -jar gateway/build/libs/gateway-0.0.1-SNAPSHOT.jar --server.port="$GATEWAY_PORT" > .run-gateway.log 2>&1 &

# --- 4. Vite UI (proxies /api -> gateway:8090) ---
echo ">> starting UI :$UI_PORT"
( cd client-ui && [ -d node_modules ] || npm install; \
  npm run dev -- --port "$UI_PORT" > ../.run-ui.log 2>&1 ) &

cat <<EOF

Stack starting. Wait ~30s, then open:  http://localhost:$UI_PORT
Health:  curl localhost:9000/actuator/health  localhost:8083/actuator/health  localhost:$GATEWAY_PORT/actuator/health
Logs:    .run-client-api.log  .run-bot-api.log  .run-gateway.log  .run-ui.log
Stop:    kill the background java/node processes (jobs -p) and 'docker stop bot-mongo'.
EOF
