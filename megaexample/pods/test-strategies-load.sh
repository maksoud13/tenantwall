#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
#  Sequential load test — one strategy at a time
# ═══════════════════════════════════════════════════════════════
set -uo pipefail
export MSYS_NO_PATHCONV=1

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PODS_DIR="$ROOT/pods"
APP="$ROOT/target/megaexample.jar"
APP_WIN="$(cygpath -w "$APP" 2>/dev/null || echo "$APP")"
K6_SCRIPT_REL="loadtest/loadtest-multi.js"
K6_SCRIPT="$ROOT/$K6_SCRIPT_REL"
K6_SCRIPT_WIN="$(cygpath -w "$K6_SCRIPT" 2>/dev/null || echo "$K6_SCRIPT")"
RESULTS="$ROOT/results/strategies"
BASE="http://localhost:8080"
WIN_HOME="$(cygpath -w "$HOME")"
AGENT_JAR="${WIN_HOME}\\.m2\\repository\\com\\tenantwall\\agent-module\\1.0.0-SNAPSHOT\\agent-module-1.0.0-SNAPSHOT.jar"

# ═══ preflight ═══
echo "════ preflight ════"
echo "  ROOT:             $ROOT"
echo "  APP:              $APP"
echo "  APP_WIN:          $APP_WIN"
echo "  K6_SCRIPT:        $K6_SCRIPT"
echo "  K6_SCRIPT_WIN:    $K6_SCRIPT_WIN"
echo "  AGENT_JAR:        $AGENT_JAR"
echo

[[ -f "$APP" ]]       || { echo "❌ app missing — run mvn package"; exit 1; }
[[ -f "$K6_SCRIPT" ]] || { echo "❌ k6 script missing: $K6_SCRIPT"; exit 1; }
command -v k6 >/dev/null || { echo "❌ k6 not installed"; exit 1; }

# ═══ CRITICAL: cd to ROOT so k6 can resolve relative imports ═══
cd "$ROOT"

if curl -s --max-time 1 "$BASE/api/health" >/dev/null 2>&1; then
    echo "❌ something is already on :8080 — stop it first"
    exit 1
fi

mkdir -p "$RESULTS"
PID=""

stop_pod() {
    if [[ -n "$PID" ]]; then
        kill "$PID" 2>/dev/null
        sleep 3
        kill -9 "$PID" 2>/dev/null
    fi
    PID=""
}
trap stop_pod EXIT INT TERM

wait_health() {
    for i in $(seq 1 90); do
        curl -s --max-time 1 "$BASE/api/health" 2>/dev/null | grep -q '"status":"ok"' && return 0
        sleep 1
    done
    return 1
}

run_one() {
    local strat="$1"
    local outdir="$RESULTS/$strat"
    mkdir -p "$outdir"

    echo
    echo "═══════════════════════════════════════════════════════"
    echo "  STRATEGY: $strat"
    echo "═══════════════════════════════════════════════════════"

    stop_pod
    sleep 1

    local props="$PODS_DIR/$strat.properties"
    [[ -f "$props" ]] || { echo "❌ $props missing — run pods/make-pods.sh"; return 1; }
    local props_win
    props_win="$(cygpath -w "$props" 2>/dev/null || echo "$props")"

    local log="$outdir/app.log"
    : > "$log"

    # ═══ launch app ═══
    java -javaagent:"${AGENT_JAR}=${props_win}" \
         -Dserver.port=8080 \
         -Xms512m -Xmx1024m \
         -jar "$APP_WIN" > "$log" 2>&1 &
    PID=$!
    echo "▶ starting (pid $PID)..."

    if ! wait_health; then
        echo "❌ pod not healthy"
        tail -30 "$log"
        return 1
    fi
    echo "✅ healthy"

    curl -s -X POST "$BASE/api/admin/reset-stats" > /dev/null 2>&1

    # ═══ warmup ═══
    echo "▶ warmup (20s, 5 VUs)..."
    ( cd "$ROOT" && k6 run --quiet --no-summary \
        -e BASE_URL="$BASE" -e STRATEGY="$strat" \
        --vus 5 --duration 20s \
        "$K6_SCRIPT_WIN" ) > "$outdir/warmup.log" 2>&1 || true

    curl -s -X POST "$BASE/api/admin/reset-counters" > /dev/null 2>&1 \
        || curl -s -X POST "$BASE/api/admin/reset-stats" > /dev/null 2>&1

    # ═══ load test ═══
    echo "▶ load test (90s, 20 VUs peak)..."
    if ! ( cd "$ROOT" && k6 run \
        -e BASE_URL="$BASE" -e STRATEGY="$strat" \
        --summary-export "$outdir/k6-summary.json" \
        "$K6_SCRIPT_WIN" ) 2>&1 | tee "$outdir/k6-output.txt"; then
        echo "❌ k6 failed for $strat"
        tail -20 "$outdir/k6-output.txt"
        stop_pod
        return 1
    fi

    if [[ ! -s "$outdir/k6-summary.json" ]]; then
        echo "❌ empty k6-summary.json"
        stop_pod
        return 1
    fi

    curl -s "$BASE/api/admin/stats"     > "$outdir/tenantwall-stats.json" 2>/dev/null || true
    curl -s "$BASE/actuator/prometheus" > "$outdir/prometheus.txt"        2>/dev/null || true

    stop_pod
    echo "✅ $strat done"
}

for s in header fixed session cookie jwt; do
    run_one "$s" || { echo "❌ $s failed — abort"; exit 1; }
done

echo
echo "═══════════════════════════════════════════════════════"
echo "  COMPARISON — summary (sequential load test)"
echo "═══════════════════════════════════════════════════════"
python3 <<'PYEOF'
import json, os, sys

base = 'results/strategies'
rows = []
for strat in ['header', 'fixed', 'session', 'cookie', 'jwt']:
    p = f'{base}/{strat}/k6-summary.json'
    if not os.path.exists(p):
        continue
    d = json.load(open(p))
    m = d.get('metrics', {})
    def v(k, sub='values'):
        return m.get(k, {}).get(sub, {})
    rows.append({
        'strategy': strat,
        'reqs':     int(v('http_reqs').get('count', 0)),
        'rps':      round(v('http_reqs').get('rate', 0), 1),
        'success':  round(v('success_rate').get('rate', 0) * 100, 2),
        'own_ok':   int(v('own_tenant_ok').get('count', 0)),
        'blocked':  int(v('cross_tenant_blocked').get('count', 0)),
        'leaks':    int(v('isolation_broken').get('count', 0)),
        'p50':      round(v('lat_lookup_ms').get('p(50)', 0), 1),
        'p95':      round(v('lat_lookup_ms').get('p(95)', 0), 1),
        'p99':      round(v('lat_lookup_ms').get('p(99)', 0), 1),
    })

hdr = f"{'strategy':<10} {'reqs':>7} {'rps':>6} {'succ%':>6} {'own':>6} {'blk':>5} {'leak':>5} {'p50':>7} {'p95':>7} {'p99':>7}"
print(hdr)
print('-' * len(hdr))
for r in rows:
    print(f"{r['strategy']:<10} {r['reqs']:>7} {r['rps']:>6} {r['success']:>6.2f} {r['own_ok']:>6} {r['blocked']:>5} {r['leaks']:>5} {r['p50']:>7} {r['p95']:>7} {r['p99']:>7}")

print()
if any(r['leaks'] > 0 for r in rows):
    print('🚨 ISOLATION BROKEN — investigate!')
    sys.exit(1)
else:
    print('✅ Zero isolation leaks across all 5 strategies')
PYEOF