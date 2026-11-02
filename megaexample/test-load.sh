
#!/bin/bash
# ============================================================
# test-load.sh — TenantWall megaexample load test runner
# ============================================================
set -o pipefail
export MSYS_NO_PATHCONV=1

cd "$(dirname "$0")"

# ─── Paths ───
WIN_HOME="$(cygpath -w "$HOME" 2>/dev/null || echo "$HOME")"
AGENT_JAR="${WIN_HOME}/.m2/repository/com/tenantwall/agent-module/1.0.0-SNAPSHOT/agent-module-1.0.0-SNAPSHOT.jar"
APP_JAR="target/megaexample.jar"
JS_SCRIPT="loadtest/loadtest.js"

# ─── Config ───
STAGE_DURATION="${STAGE_DURATION:-3m}"
VUS="${VUS:-30}"
WARMUP_DURATION="${WARMUP_DURATION:-30s}"
BASE_URL="http://localhost:8080"

SKIP_BASELINE=0
SKIP_INTERCEPTED=0
for arg in "$@"; do
    case "$arg" in
        --skip-baseline)    SKIP_BASELINE=1 ;;
        --skip-intercepted) SKIP_INTERCEPTED=1 ;;
    esac
done

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; NC='\033[0m'; BOLD='\033[1m'

step() { echo -e "\n${CYAN}>> $*${NC}"; }
ok()   { echo -e "${GREEN}OK  $*${NC}"; }
err()  { echo -e "${RED}ERR $*${NC}"; }
warn() { echo -e "${YELLOW}WARN $*${NC}"; }

cleanup_port() {
    local pid=$(netstat -ano 2>/dev/null | grep ":8080" | grep LISTENING | awk '{print $5}' | head -1)
    if [ -n "$pid" ]; then
        warn "Killing PID on port 8080: $pid"
        taskkill //F //PID "$pid" 2>/dev/null || true
        sleep 2
    fi
}

start_app() {
    local mode="$1" log="$2"
    if [ "$mode" = "intercepted" ]; then
        java -javaagent:"${AGENT_JAR}=tenantwall.properties" -Xms2g -Xmx4g -jar "$APP_JAR" > "$log" 2>&1 &
    else
        java -Xms2g -Xmx4g -jar "$APP_JAR" > "$log" 2>&1 &
    fi
    echo $!
}

wait_for_app() {
    local i
    for i in $(seq 1 60); do
        if curl -s --max-time 2 "$BASE_URL/actuator/health" 2>/dev/null | grep -q "UP"; then
            return 0
        fi
        sleep 1
    done
    return 1
}

stop_app() {
    local pid="$1"
    [ -n "$pid" ] || return 0
    kill "$pid" 2>/dev/null || true
    sleep 4
    kill -9 "$pid" 2>/dev/null || true
}

# ─── Smoke-check all endpoints (called after app is up) ───
smoke_endpoints() {
    local tenant="TENANT_001"
    local endpoints=(
        "/api/products/1|200|oltp_lookup"
        "/api/products?page=0&size=20|200|oltp_list"
        "/api/products/by-sku/P-TENANT_001-000000|200|oltp_sku"
        "/api/products/reports/stock-value|200|olap_cte"
        "/api/products/reports/top-categories|200|olap_window"
        "/api/reports/category-revenue|200|olap_cat_revenue (NEW)"
        "/api/reports/customer-ltv|200|olap_cust_ltv (NEW)"
        "/api/reports/low-stock|200|olap_low_stock (NEW)"
    )
    local pass=0 fail=0
    for entry in "${endpoints[@]}"; do
        IFS='|' read -r path expected name <<< "$entry"
        local actual
        actual=$(curl -s -o /dev/null -w "%{http_code}" --max-time 20 \
                 -H "X-Tenant-Id: $tenant" "${BASE_URL}${path}")
        if [ "$actual" = "$expected" ]; then
            echo "    [OK] $name → $actual"
            pass=$((pass+1))
        else
            echo "    [X] $name → $actual (expected $expected)"
            fail=$((fail+1))
        fi
    done
    echo "  Endpoints: $pass passed, $fail failed"
    [ "$fail" -eq 0 ] && return 0 || return 1
}

# ─── Verify cross-tenant isolation ───
verify_isolation() {
    local t1="TENANT_001"
    local t2_id=40001  # belongs to TENANT_002
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
             -H "X-Tenant-Id: $t1" "${BASE_URL}/api/products/${t2_id}")
    if [ "$status" = "403" ] || [ "$status" = "404" ]; then
        echo "    [OK] Cross-tenant: $status (isolated)"
        return 0
    elif [ "$status" = "200" ]; then
        echo "    [X] ISOLATION BROKEN: HTTP 200 — DATA LEAK"
        return 1
    else
        echo "    ? Unexpected status: $status"
        return 1
    fi
}

run_test() {
    local mode="$1"
    local outdir="results/$mode"
    mkdir -p "$outdir"

    echo ""
    echo "==========================================================="
    echo -e "  ${BOLD}TEST: $mode${NC}"
    echo "==========================================================="

    cleanup_port

    step "Starting app ($mode)"
    local log="$outdir/app.log"
    local pid
    pid=$(start_app "$mode" "$log")
    echo "  PID: $pid  Log: $log"

    if ! wait_for_app; then
        err "App failed to start"
        tail -30 "$log"
        stop_app "$pid"
        return 1
    fi
    ok "App is up"

    # Reset BEFORE warmup
    if [ "$mode" = "intercepted" ]; then
        curl -s -X POST "$BASE_URL/api/admin/reset-stats" > /dev/null
    fi

    # ─── Smoke-check all endpoints ───
    step "Smoke-check endpoints"
    if ! smoke_endpoints; then
        err "One or more endpoints failed"
        stop_app "$pid"
        return 1
    fi
    ok "All endpoints reachable"

    # ─── Isolation check ───
    step "Cross-tenant isolation"
    if ! verify_isolation; then
        err "ISOLATION BROKEN — aborting"
        stop_app "$pid"
        return 1
    fi
    ok "Isolation verified"

    # ─── Warmup ───
    step "Warmup ($WARMUP_DURATION)"
    k6 run --quiet --no-summary \
        -e BASE_URL="$BASE_URL" \
        -e NUM_TENANTS=20 \
        -e PRODUCTS_PER_TENANT=40000 \
        --vus 10 --duration "$WARMUP_DURATION" \
        "$JS_SCRIPT" 2>&1 | tail -3 || true
    ok "Warmup done"

    # Reset counters after warmup (keep cache if endpoint exists)
    if [ "$mode" = "intercepted" ]; then
        curl -s -X POST "$BASE_URL/api/admin/reset-counters" > /dev/null 2>&1 \
            || curl -s -X POST "$BASE_URL/api/admin/reset-stats" > /dev/null
        echo "  Counters reset (post-warmup)"
    fi

    # ─── Load test ───
    step "Load test ($STAGE_DURATION @ $VUS VUs)"
    k6 run \
        -e BASE_URL="$BASE_URL" \
        -e NUM_TENANTS=20 \
        -e PRODUCTS_PER_TENANT=40000 \
        --out json="$outdir/k6-raw.json" \
        --summary-export "$outdir/k6-summary.json" \
        "$JS_SCRIPT" \
        2>&1 | tee "$outdir/k6-output.txt"

    if [ "$mode" = "intercepted" ]; then
        curl -s "$BASE_URL/api/admin/stats" > "$outdir/tenantwall-stats.json"
    fi
    curl -s "$BASE_URL/actuator/prometheus" > "$outdir/prometheus.txt" 2>/dev/null || true

    step "Stopping app"
    stop_app "$pid"
    ok "$mode complete"
}

# ─── Pre-flight ───
echo ""
echo "==========================================================="
echo -e "${BOLD}  Megaexample Load Test Suite${NC}"
echo "==========================================================="
echo "  Stage: $STAGE_DURATION | VUs: $VUS | Warmup: $WARMUP_DURATION"
echo ""

step "Pre-flight"
echo "  AGENT_JAR: $AGENT_JAR"
[ -f "$AGENT_JAR" ] || { err "Agent JAR not found"; exit 1; }
[ -f "$APP_JAR" ]   || { err "App JAR not found (run: mvn package -DskipTests)"; exit 1; }
[ -f "$JS_SCRIPT" ] || { err "loadtest/loadtest.js missing"; exit 1; }
command -v k6 >/dev/null || { err "k6 not installed"; exit 1; }
docker ps --format '{{.Names}}' 2>/dev/null | grep -q "mega-mysql" || { err "MySQL container down"; exit 1; }
ok "All prerequisites met"

rm -rf results/baseline/* results/intercepted/* 2>/dev/null || true

# ─── Run tests ───
if [ "$SKIP_BASELINE" = "0" ]; then
    run_test "baseline" || exit 1
fi
if [ "$SKIP_INTERCEPTED" = "0" ]; then
    run_test "intercepted" || exit 1
fi

# ─── Summary ───
echo ""
echo "==========================================================="
echo -e "${BOLD}  RESULTS${NC}"
echo "==========================================================="
echo ""

if [ -f results/baseline/k6-output.txt ]; then
    echo -e "  ${BOLD}Baseline (no agent):${NC}"
    grep -E "own_tenant_ok|cross_tenant_blocked|isolation_broken|http_reqs\." \
        results/baseline/k6-output.txt | sed 's/^/    /'
    echo ""
fi

if [ -f results/intercepted/k6-output.txt ]; then
    echo -e "  ${BOLD}Intercepted (with agent):${NC}"
    grep -E "own_tenant_ok|cross_tenant_blocked|isolation_broken|not_found_404|server_errors_5xx|http_reqs\." \
        results/intercepted/k6-output.txt | sed 's/^/    /'
    echo ""

    echo -e "  ${BOLD}Per-endpoint latency (intercepted):${NC}"
    for ep in oltp_lookup oltp_list oltp_sku oltp_cte oltp_sku olap_cte olap_window olap_cat_revenue olap_cust_ltv olap_low_stock; do
        line=$(grep "latency_${ep}_ms" results/intercepted/k6-output.txt | head -1)
        [ -n "$line" ] && echo "    $line"
    done
    echo ""

    echo -e "  ${BOLD}3 NEW REPORT ENDPOINTS:${NC}"
    for ep in olap_cat_revenue olap_cust_ltv olap_low_stock; do
        line=$(grep "http_req_duration{type:${ep}}" results/intercepted/k6-output.txt | head -2 | tail -1)
        [ -n "$line" ] && echo "    $line"
    done
    echo ""
fi

if [ -f results/intercepted/tenantwall-stats.json ]; then
    echo -e "  ${BOLD}TenantWall agent:${NC}"
    python3 -c "
import json
try:
    d = json.load(open('results/intercepted/tenantwall-stats.json'))
    print(f'    Cache hit rate: {d.get(\"cache.hitRate\", 0) * 100:.1f}%')
    print(f'    Python calls:   {d.get(\"routing.pythonCalls\", 0):,}')
    print(f'    Local calls:    {d.get(\"routing.localCalls\", 0):,}')
    print(f'    Fallbacks:      {d.get(\"routing.fallbacks\", 0):,}')
except Exception as e:
    print(f'    Error: {e}')
" 2>/dev/null || echo "    (stats file exists but python failed)"
    echo ""
fi

ok "Done"
echo ""
