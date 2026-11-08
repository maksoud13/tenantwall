#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# TenantWall — verify all 5 tenant strategies + validation
# ═══════════════════════════════════════════════════════════════
set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/target/megaexample.jar"
PROPS="$ROOT/tenantwall.properties"
BACKUP="$ROOT/tenantwall.properties.bak"
LOG="/tmp/tw-test.log"
BASE="http://localhost:8080"

# -- locate agent jar in .m2 --
M2_AGENT="$(cygpath -u "$HOME")/.m2/repository/com/tenantwall/agent-module/1.0.0-SNAPSHOT/agent-module-1.0.0-SNAPSHOT.jar"
WIN_HOME="$(cygpath -w "$HOME")"
AGENT_JAR_WIN="${WIN_HOME}\\.m2\\repository\\com\\tenantwall\\agent-module\\1.0.0-SNAPSHOT\\agent-module-1.0.0-SNAPSHOT.jar"

[[ -f "$M2_AGENT" ]] || { echo "❌ agent jar not found: $M2_AGENT"; exit 1; }
[[ -f "$APP" ]]      || { echo "❌ megaexample.jar not found. Build first."; exit 1; }

cp "$PROPS" "$BACKUP"
PID=""

cleanup() {
    [[ -n "$PID" ]] && kill "$PID" 2>/dev/null
    sleep 1
    cp "$BACKUP" "$PROPS" 2>/dev/null
}
trap cleanup EXIT INT TERM

# ─── helpers ───

# base64url encode helper — MUST be at top level (not inside a function)
_b64url() {
    openssl base64 -A | tr '+/' '-_' | tr -d '='
}

make_jwt() {
    local tenant="$1"
    local hdr='{"alg":"none","typ":"JWT"}'
    local pay="{\"tenant_id\":\"$tenant\",\"sub\":\"test\"}"
    echo "$(printf '%s' "$hdr" | _b64url).$(printf '%s' "$pay" | _b64url).sig"
}

write_props() {
    # $1 = full tenant section text
    cp "$BACKUP" "$PROPS"
    cat >> "$PROPS" <<EOF

# ═══ TEST OVERRIDE ═══
$1
EOF
}

start_app() {
    echo "▶ starting app..."
    : > "$LOG"
    java -javaagent:"${AGENT_JAR_WIN}=tenantwall.properties" \
         -Xms512m -Xmx1g \
         -jar "$APP" > "$LOG" 2>&1 &
    PID=$!

    for i in $(seq 1 40); do
        if grep -q "Started.*Application" "$LOG" 2>/dev/null; then
            echo "✅ app started (pid=$PID)"; return 0
        fi
        if ! kill -0 "$PID" 2>/dev/null; then
            echo "❌ app died during startup"; tail -20 "$LOG"; return 1
        fi
        sleep 1
    done
    echo "❌ app did not start in 40s"; tail -20 "$LOG"; return 1
}

stop_app() {
    if [[ -n "$PID" ]]; then
        kill "$PID" 2>/dev/null
        wait "$PID" 2>/dev/null
    fi
    PID=""
}

code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

PASS=0
FAIL=0
ok()   { echo "✅ $1"; PASS=$((PASS+1)); }
bad()  { echo "❌ $1"; FAIL=$((FAIL+1)); }
check_code() {
    local want="$1"; local got="$2"; local label="$3"
    if [[ "$got" == "$want" ]]; then ok "$label ($got)"; else bad "$label (want=$want got=$got)"; fi
}

# ─── 1) header ───
test_header() {
    echo; echo "════ 1) strategy=header ════"
    write_props "tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id"
    start_app || return

    check_code 200 "$(code -H 'X-Tenant-Id: TENANT_001' $BASE/api/products/1)"       "header: own id=1"
    check_code 403 "$(code -H 'X-Tenant-Id: TENANT_001' $BASE/api/products/40001)"   "header: cross id=40001"
    check_code 200 "$(code -H 'X-Tenant-Id: TENANT_002' $BASE/api/products/40001)"   "header: TENANT_002 own id=40001"

    stop_app
}

# ─── 2) fixed ───
test_fixed() {
    echo; echo "════ 2) strategy=fixed ════"
    write_props "tenantwall.tenant.strategy=fixed
tenantwall.tenant.fixedValue=TENANT_001"
    start_app || return

    check_code 200 "$(code $BASE/api/products/1)"     "fixed: id=1 (forced TENANT_001)"
    check_code 403 "$(code $BASE/api/products/40001)" "fixed: id=40001 blocked"

    stop_app
}

# ─── 3) session ───
test_session() {
    echo; echo "════ 3) strategy=session ════"
    write_props "tenantwall.tenant.strategy=session
tenantwall.tenant.sessionKey=TENANT_ID"
    start_app || return

    local jar="/tmp/tw-cookies.txt"; rm -f "$jar"

    curl -s -c "$jar" -X POST $BASE/api/admin/session/TENANT_001 > /dev/null

    check_code 200 "$(code -b "$jar" $BASE/api/products/1)"     "session: own id=1"
    check_code 403 "$(code -b "$jar" $BASE/api/products/40001)" "session: cross id=40001"

    rm -f "$jar"
    stop_app
}

# ─── 4) cookie ───
test_cookie() {
    echo; echo "════ 4) strategy=cookie ════"
    write_props "tenantwall.tenant.strategy=cookie
tenantwall.tenant.cookieName=tenant_id"
    start_app || return

    check_code 200 "$(code --cookie 'tenant_id=TENANT_001' $BASE/api/products/1)"     "cookie: own id=1"
    check_code 403 "$(code --cookie 'tenant_id=TENANT_001' $BASE/api/products/40001)" "cookie: cross id=40001"

    stop_app
}

# ─── 5) jwt ───
test_jwt() {
    echo; echo "════ 5) strategy=jwt ════"
    write_props "tenantwall.tenant.strategy=jwt
tenantwall.tenant.jwtClaim=tenant_id
tenantwall.tenant.jwtHeader=Authorization
tenantwall.tenant.jwtPrefix=Bearer "
    start_app || return

    local t1 t2
    t1="$(make_jwt TENANT_001)"
    t2="$(make_jwt TENANT_002)"

    check_code 200 "$(code -H "Authorization: Bearer $t1" $BASE/api/products/1)"     "jwt: TENANT_001 → id=1"
    check_code 403 "$(code -H "Authorization: Bearer $t1" $BASE/api/products/40001)" "jwt: TENANT_001 → cross"
    check_code 200 "$(code -H "Authorization: Bearer $t2" $BASE/api/products/40001)" "jwt: TENANT_002 → id=40001"

    stop_app
}

# ─── 6) validation: bad strategy → must fail fast ───
test_validation_bad() {
    echo; echo "════ 6) validation: unknown strategy ════"
    write_props "tenantwall.tenant.strategy=bogus"
    : > "$LOG"
    java -javaagent:"${AGENT_JAR_WIN}=tenantwall.properties" -jar "$APP" > "$LOG" 2>&1 &
    PID=$!

    local died=0
    for i in $(seq 1 20); do
        if ! kill -0 "$PID" 2>/dev/null; then died=1; break; fi
        grep -q "Started.*Application" "$LOG" && break
        sleep 1
    done

    if [[ $died -eq 1 ]] && grep -qi "Invalid tenantwall.tenant.strategy\|IllegalStateException" "$LOG"; then
        ok "validation: bogus strategy rejected at startup"
    else
        bad "validation: bogus strategy NOT rejected"
        tail -10 "$LOG"
    fi
    stop_app
}

# ─── 7) conflict: header + cookie keys both set → warning only ───
test_conflict_warning() {
    echo; echo "════ 7) conflict: extra strategy keys → warning ════"
    write_props "tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
tenantwall.tenant.cookieName=tenant_id
tenantwall.tenant.sessionKey=TENANT_ID"
    start_app || return

    if grep -q "WARNING: strategy=header but tenantwall.tenant.cookieName\|WARNING: strategy=header but tenantwall.tenant.sessionKey" "$LOG"; then
        ok "conflict: warning printed for non-selected strategies"
    else
        bad "conflict: no warning in log"
    fi
    stop_app
}

# ═══ run all ═══
test_header
test_fixed
test_session
test_cookie
test_jwt
test_validation_bad
test_conflict_warning

echo
echo "════════════════════════════════════════"
echo "PASS=$PASS  FAIL=$FAIL"
if [[ $FAIL -eq 0 ]]; then
    echo "🎉 ALL TESTS PASSED"
else
    echo "⚠️  some tests failed — see log: $LOG"
fi
exit $FAIL