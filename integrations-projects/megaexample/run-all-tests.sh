#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
#  Run ALL TenantWall tests sequentially + generate report
#  ⚠️  DO NOT set MSYS_NO_PATHCONV=1 — breaks mvn bash wrapper
# ═══════════════════════════════════════════════════════════════
set -o pipefail

cd "$(dirname "$0")"

QUICK=0
SKIP_STRATEGIES=0
for arg in "$@"; do
    case "$arg" in
        --quick)           QUICK=1 ;;
        --skip-strategies) SKIP_STRATEGIES=1 ;;
    esac
done

STAGE_DURATION=$([ "$QUICK" = "1" ] && echo "1m" || echo "3m")
VUS=$([ "$QUICK" = "1" ] && echo "10" || echo "30")

WIN_HOME="$(cygpath -w "$HOME" 2>/dev/null || echo "$HOME")"
AGENT_JAR="${WIN_HOME}/.m2/repository/com/tenantwall/agent-module/1.0.0-SNAPSHOT/agent-module-1.0.0-SNAPSHOT.jar"
AGENT_JAR_UNIX="$(cygpath -u "$AGENT_JAR" 2>/dev/null || echo "$AGENT_JAR")"
APP_JAR="target/megaexample.jar"
APP_WIN="$(cygpath -w "$(pwd)/$APP_JAR" 2>/dev/null || echo "$APP_JAR")"
CONFIG_WIN="$(cygpath -w "$(pwd)/tenantwall.properties" 2>/dev/null || echo "tenantwall.properties")"
PARSER_URL="http://127.0.0.1:8000/"
BASE_URL="http://localhost:8080"

MVN_BIN="$(command -v mvn 2>/dev/null || echo '/c/Java/apache-maven-3.9.5-bin/maven/bin/mvn')"

REPORT_DIR="test-reports/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$REPORT_DIR"

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; NC='\033[0m'; BOLD='\033[1m'

step() {
    echo ""
    echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}  $*${NC}"
    echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
}
ok()   { echo -e "${GREEN}✅ $*${NC}"; }
err()  { echo -e "${RED}❌ $*${NC}"; }
warn() { echo -e "${YELLOW}⚠️  $*${NC}"; }

kill_port_8080() {
    local max_attempts="${1:-3}"
    for i in $(seq 1 "$max_attempts"); do
        local pid
        pid=$(netstat -ano 2>/dev/null | grep ":8080 " | grep LISTENING | awk '{print $NF}' | head -1)
        if [ -z "$pid" ]; then
            return 0
        fi
        warn "Killing process on :8080 (pid $pid, attempt $i)"
        taskkill //F //T //PID "$pid" 2>/dev/null || true
        sleep 2
    done
    netstat -ano 2>/dev/null | grep ":8080 " | grep -q LISTENING && return 1 || return 0
}

APP_PID=""
cleanup() {
    if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
        warn "Cleanup: killing app (pid $APP_PID)"
        kill "$APP_PID" 2>/dev/null || true
        sleep 1
        kill -9 "$APP_PID" 2>/dev/null || true
    fi
    kill_port_8080 3
}
trap cleanup EXIT INT TERM

# ═══════════════════════════════════════════════════════════════
#  Preflight
# ═══════════════════════════════════════════════════════════════
step "Preflight"

[ -f "$APP_JAR" ]        || { err "App JAR missing: $APP_JAR"; exit 1; }
[ -f "$AGENT_JAR_UNIX" ] || { err "Agent JAR missing: $AGENT_JAR_UNIX"; exit 1; }
[ -x "$MVN_BIN" ]        || { err "Maven not found: $MVN_BIN"; exit 1; }
command -v k6 >/dev/null || { err "k6 not installed"; exit 1; }
docker ps --format '{{.Names}}' 2>/dev/null | grep -q "mega-mysql" || { err "mega-mysql not running"; exit 1; }

ok "App JAR:   $APP_JAR"
ok "Agent JAR: $AGENT_JAR_UNIX"
ok "Maven:     $MVN_BIN"

if curl -s --max-time 2 "$PARSER_URL" >/dev/null 2>&1; then
    ok "Parser responding on :8000"
else
    warn "Parser NOT responding on :8000 — CTE queries may fall back"
fi

CNT=$(docker exec mega-mysql mysql -uroot -proot -N -e "SELECT COUNT(*) FROM mega_demo.products" 2>/dev/null || echo "0")
ok "DB: $CNT products"

kill_port_8080 3 || warn "Port 8080 still in use"

# ═══════════════════════════════════════════════════════════════
#  Step 1: Concurrency (FIRST — no app, no file lock)
# ═══════════════════════════════════════════════════════════════
step "Step 1: Concurrency test (no app required)"

CONC_LOG="$REPORT_DIR/concurrency.log"
echo "  Maven: $MVN_BIN"
echo "  Using: mvn test-compile failsafe:integration-test failsafe:verify"
echo "  (skips jar/repackage — no file lock conflict)"
echo ""

# ⚠️ CRITICAL: use test-compile + failsafe only (not verify)
# This avoids rebuilding megaexample.jar while app may be running
"$MVN_BIN" test-compile failsafe:integration-test failsafe:verify \
    -Dit.test=ConcurrencyIsolationIT -DskipITs=false > "$CONC_LOG" 2>&1
CONC_RESULT=$?

if [ "$CONC_RESULT" = "0" ]; then
    ok "Concurrency passed"
    grep -E "Requests:|Rows read:|Leaks:|Errors:|Duration:" "$CONC_LOG" | sed 's/^/  /' || true
else
    err "Concurrency failed — check $CONC_LOG"
    tail -20 "$CONC_LOG"
fi

# ═══════════════════════════════════════════════════════════════
#  Step 2: Start app
# ═══════════════════════════════════════════════════════════════
step "Step 2: Start app (agent + JWT)"

# Verify jar still valid (in case something corrupted it)
if ! unzip -l "$APP_JAR" 2>/dev/null | grep -q "META-INF/MANIFEST.MF"; then
    err "megaexample.jar is corrupted — rebuilding"
    ( cd "$(pwd)" && "$MVN_BIN" clean package -DskipTests -q )
fi

APP_LOG="$REPORT_DIR/app.log"
: > "$APP_LOG"

java -javaagent:"${AGENT_JAR}" \
     -Dtenantwall.config="${CONFIG_WIN}" \
     -Dfile.encoding=UTF-8 \
     -Xms2g -Xmx5g \
     -jar "$APP_WIN" > "$APP_LOG" 2>&1 &
APP_PID=$!
echo "  App PID: $APP_PID"

echo "  Waiting for startup (up to 60s)..."
READY=0
for i in $(seq 1 30); do
    if curl -s --max-time 2 "$BASE_URL/api/health" 2>/dev/null | grep -q "ok"; then
        READY=1; break
    fi
    sleep 2
done

if [ "$READY" != "1" ]; then
    err "App failed to start — last 40 lines:"
    tail -40 "$APP_LOG"
    exit 1
fi
ok "App ready"

# ═══════════════════════════════════════════════════════════════
#  Step 3: JWT E2E
# ═══════════════════════════════════════════════════════════════
step "Step 3: JWT E2E"

JWT_LOG="$REPORT_DIR/jwt-e2e.log"
bash test-jwt-e2e.sh 2>&1 | tee "$JWT_LOG"
JWT_RESULT=${PIPESTATUS[0]}

if [ "$JWT_RESULT" = "0" ]; then
    ok "JWT E2E passed"
else
    err "JWT E2E had failures — check $JWT_LOG"
fi

# ═══════════════════════════════════════════════════════════════
#  Step 4: Load test
# ═══════════════════════════════════════════════════════════════
step "Step 4: Load test (k6 — $STAGE_DURATION @ $VUS VUs)"

LOAD_LOG="$REPORT_DIR/load.log"

echo "  Warmup (15s @ 5 VUs)..."
k6 run --quiet --no-summary \
    -e BASE_URL="$BASE_URL" \
    -e NUM_TENANTS=50 \
    -e PRODUCTS_PER_TENANT=100000 \
    --vus 5 --duration 15s \
    loadtest/loadtest.js > /dev/null 2>&1 || true

curl -s -X POST "$BASE_URL/api/admin/reset-stats" > /dev/null 2>&1 || true

echo "  Main load ($STAGE_DURATION @ $VUS VUs)..."
k6 run \
    -e BASE_URL="$BASE_URL" \
    -e NUM_TENANTS=50 \
    -e PRODUCTS_PER_TENANT=100000 \
    --summary-export "$REPORT_DIR/k6-summary.json" \
    loadtest/loadtest.js 2>&1 | tee "$LOAD_LOG"

curl -s "$BASE_URL/api/admin/stats" > "$REPORT_DIR/tenantwall-stats.json" 2>/dev/null || true
ok "Load test complete"

# ═══════════════════════════════════════════════════════════════
#  Step 5: Stop app + port cleanup
# ═══════════════════════════════════════════════════════════════
step "Step 5: Stop app"

if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID" 2>/dev/null || true
    sleep 2
    kill -9 "$APP_PID" 2>/dev/null || true
    APP_PID=""
fi

if kill_port_8080 5; then
    ok "App stopped, port 8080 free"
else
    warn "Port 8080 still in use — strategies may fail"
fi

# ═══════════════════════════════════════════════════════════════
#  Step 6: Strategies
# ═══════════════════════════════════════════════════════════════
STRAT_RESULT="skipped"
if [ "$SKIP_STRATEGIES" = "0" ]; then
    step "Step 6: Strategies (7 restarts)"
    STRAT_LOG="$REPORT_DIR/strategies.log"
    bash test-strategies.sh 2>&1 | tee "$STRAT_LOG"
    STRAT_RESULT=${PIPESTATUS[0]}
fi

# ═══════════════════════════════════════════════════════════════
#  Step 7: Generate report
# ═══════════════════════════════════════════════════════════════
step "Step 7: Generating report"

REPORT="$REPORT_DIR/report.md"

cat > "$REPORT" <<HEADER
# TenantWall Test Report

**Generated:** $(date)
**DB:** $(docker exec mega-mysql mysql -uroot -proot -N -e "SELECT COUNT(*) FROM mega_demo.products" 2>/dev/null || echo "?") products
**Report dir:** \`$REPORT_DIR/\`

---

## 1. Concurrency Test

\`\`\`
$(grep -E "Requests:|Threads:|Completed:|Rows read:|Leaks:|Errors:|Duration:|Tests run:|BUILD" "$REPORT_DIR/concurrency.log" 2>/dev/null | head -10)
\`\`\`

---

## 2. JWT E2E

\`\`\`
$(grep -E "PASS|FAIL|HTTP" "$REPORT_DIR/jwt-e2e.log" 2>/dev/null | tail -20)
\`\`\`

---

## 3. Load Test ($STAGE_DURATION @ $VUS VUs)

### Metrics
\`\`\`
$(grep -E "isolation_broken|cross_tenant_blocked|own_tenant_ok|success_rate|http_reqs\.|http_req_duration" "$REPORT_DIR/load.log" 2>/dev/null | head -15)
\`\`\`

### Latency by Endpoint
\`\`\`
$(grep -E "latency_.*_ms" "$REPORT_DIR/load.log" 2>/dev/null | head -15)
\`\`\`

---

## 4. Strategies Test

$([ "$SKIP_STRATEGIES" = "0" ] && echo "\`\`\`
$(grep -E "PASS=|FAIL=|ALL TESTS|❌" "$REPORT_DIR/strategies.log" 2>/dev/null | tail -10)
\`\`\`" || echo "_Skipped_")

---

## 5. Agent Stats

\`\`\`
$(cat "$REPORT_DIR/tenantwall-stats.json" 2>/dev/null | head -5)
\`\`\`

---

## Verdict

| Test | Status |
|------|:------:|
| Concurrency | $([ "$CONC_RESULT" = "0" ] && echo "✅" || echo "❌") |
| JWT E2E | $([ "$JWT_RESULT" = "0" ] && echo "✅" || echo "❌") |
| Load test | $(grep -q "isolation_broken.*: 0" "$REPORT_DIR/load.log" 2>/dev/null && echo "✅" || echo "⚠️") |
| Strategies | $([ "$STRAT_RESULT" = "skipped" ] && echo "_skip_" || ([ "$STRAT_RESULT" = "0" ] && echo "✅" || echo "❌")) |

**Artifacts:** \`$REPORT_DIR/\`
HEADER

ok "Report: $REPORT"

echo ""
echo "════════════════════════════════════════════════════════════"
echo "  Summary"
echo "════════════════════════════════════════════════════════════"
echo "  Concurrency:  $([ "$CONC_RESULT" = "0" ] && echo "PASS" || echo "FAIL")"
echo "  JWT E2E:      $([ "$JWT_RESULT" = "0" ] && echo "PASS" || echo "FAIL")"
echo "  Load test:    $(grep -q "isolation_broken.*: 0" "$REPORT_DIR/load.log" 2>/dev/null && echo "PASS (0 leaks)" || echo "check log")"
echo "  Strategies:   $([ "$STRAT_RESULT" = "skipped" ] && echo "skipped" || ([ "$STRAT_RESULT" = "0" ] && echo "PASS" || echo "FAIL"))"
echo ""
echo "  Report:       $REPORT"
echo "════════════════════════════════════════════════════════════"