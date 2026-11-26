#!/usr/bin/env bash
# يوقف كل الـ pods اللي start-pods.sh شغّلها
set -u

PODS_DIR="$(cd "$(dirname "$0")" && pwd)"
PIDS_FILE="$PODS_DIR/pids.txt"

if [[ ! -f "$PIDS_FILE" ]]; then
    echo "⚠️ no pids.txt — killing anything on 8081-8085"
    for p in 8081 8082 8083 8084 8085; do
        pid=$(netstat -ano 2>/dev/null | grep ":$p " | grep LISTENING | awk '{print $NF}' | head -1)
        [[ -n "$pid" ]] && taskkill //F //PID "$pid" 2>/dev/null && echo "  killed :$p (pid $pid)"
    done
    exit 0
fi

echo "▶ stopping pods..."
while read -r pid; do
    [[ -z "$pid" ]] && continue
    if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null
        echo "  kill $pid"
    fi
done < "$PIDS_FILE"

sleep 2

while read -r pid; do
    [[ -z "$pid" ]] && continue
    if kill -0 "$pid" 2>/dev/null; then
        kill -9 "$pid" 2>/dev/null
        echo "  kill -9 $pid"
    fi
done < "$PIDS_FILE"

rm -f "$PIDS_FILE"
echo "✅ all pods stopped"