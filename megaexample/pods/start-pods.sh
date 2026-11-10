#!/usr/bin/env bash
# يشغّل 5 JVMs بالتوازي — كل واحد بـ strategy + port مختلف
set -eu
export MSYS_NO_PATHCONV=1

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PODS_DIR="$ROOT/pods"
APP="$ROOT/target/megaexample.jar"
WIN_HOME="$(cygpath -w "$HOME")"
AGENT_JAR="${WIN_HOME}\\.m2\\repository\\com\\tenantwall\\agent-module\\1.0.0-SNAPSHOT\\agent-module-1.0.0-SNAPSHOT.jar"

# ⚙️ Pool per pod — 5 × 20 = 100 (safe under MySQL 151)
HIKARI_MAX_POOL=20
HIKARI_MIN_IDLE=5

[[ -f "$APP" ]] || { echo "❌ app missing — run mvn package"; exit 1; }
[[ -f "${AGENT_JAR//\\//}" ]] || { echo "❌ agent missing: $AGENT_JAR"; exit 1; }

for s in header fixed session cookie jwt; do
    [[ -f "$PODS_DIR/$s.properties" ]] || { echo "❌ $s.properties missing — run make-pods.sh"; exit 1; }
done

# ports check
for p in 8081 8082 8083 8084 8085; do
    if curl -s --max-time 1 "http://localhost:$p/api/health" >/dev/null 2>&1; then
        echo "❌ Port $p already in use. Run stop-pods.sh first."; exit 1
    fi
done

mkdir -p "$PODS_DIR/logs"

declare -A PORTS=( [header]=8081 [fixed]=8082 [session]=8083 [cookie]=8084 [jwt]=8085 )
PIDS=()

echo "▶ launching 5 pods (pool=$HIKARI_MAX_POOL per pod)..."
for s in header fixed session cookie jwt; do
    port="${PORTS[$s]}"
    log="$PODS_DIR/logs/$s.log"
    : > "$log"
    java -javaagent:"${AGENT_JAR}=${PODS_DIR}/${s}.properties" \
         -Dserver.port="$port" \
         -Dspring.datasource.hikari.maximum-pool-size="$HIKARI_MAX_POOL" \
         -Dspring.datasource.hikari.minimum-idle="$HIKARI_MIN_IDLE" \
         -Dspring.datasource.hikari.pool-name="pool-$s" \
         -Xms512m -Xmx1024m \
         -jar "$APP" > "$log" 2>&1 &
    PID=$!
    PIDS+=("$PID")
    echo "  $s → :$port  pid $PID  log $log"
done

echo
echo "▶ waiting for all pods to become healthy (up to 120s)..."
for s in header fixed session cookie jwt; do
    port="${PORTS[$s]}"
    ok=0
    for i in $(seq 1 120); do
        if curl -s --max-time 1 "http://localhost:$port/api/health" 2>/dev/null | grep -q '"status":"ok"'; then
            ok=1; break
        fi
        sleep 1
    done
    if [[ $ok -eq 1 ]]; then
        echo "  ✅ $s (:$port) healthy"
    else
        echo "  ❌ $s (:$port) NOT healthy — last 20 lines:"
        tail -20 "$PODS_DIR/logs/$s.log"
        exit 1
    fi
done

printf '%s\n' "${PIDS[@]}" > "$PODS_DIR/pids.txt"
echo
echo "🎉 all 5 pods UP — pids in $PODS_DIR/pids.txt"