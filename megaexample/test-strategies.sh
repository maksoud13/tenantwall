#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
#  TenantWall — verify all 5 tenant strategies + validation
#  Post-reseed: 50 tenants × 250K products
#  TENANT_001: 800001-1050000
#  TENANT_002: 900001-1300000
# ═══════════════════════════════════════════════════════════════
set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/target/megaexample.jar"
APP_WIN="$(cygpath -w "$APP" 2>/dev/null || echo "$APP")"
PROPS="$ROOT/tenantwall.properties"
PROPS_WIN="$(cygpath -w "$PROPS" 2>/dev/null || echo "$PROPS")"
BACKUP="$ROOT/tenantwall.properties.bak"
LOG="/tmp/tw-test.log"
BASE="http://localhost:8080"

ID_T1_OWN=800001
ID_T2_OWN=900001

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

_b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

make_jwt() {
    local tenant="$1"
    local hdr='{"alg":"none","typ":"JWT"}'
    local pay="{\"tenant_id\":\"$tenant\",\"sub\":\"test\"}"
    echo "$(printf '%s' "$hdr" | _b64url).$(printf '%s' "$pay" | _b64url).sig"
}

write_props() {
    cp "$BACKUP" "$PROPS"
    cat >> "$PROPS" <<EOF

# ═══ TEST OVERRIDE ═══
$1
EOF
}

start_app() {
    echo "▶ starting app..."
    : > "$LOG"
    java -javaagent:"${AGENT_JAR_WIN}" \
         -Dtenantwall.config="${PROPS_WIN}" \
         -Dfile.encoding=UTF-8 \
         -Xms1g -Xmx2g \
         -jar "$APP_WIN" > "$LOG" 2>&1 &
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

# Cross-tenant: 403 OR 404 both valid
check_blocked() {
    local got="$1"; local label="$2"
    if [[ "$got" == "403" || "$got" == "404" ]]; then
        ok "$label ($got)"
    else
        bad "$label (want=403/404 got=$got)"
    fi
}

# ─── 1) header ───
test_header() {
    echo; echo "════ 1) strategy=header ════"
    write_props "tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id"
    start_app || return

    check_code 200 "$(code -H 'X-Tenant-Id: TENANT_001' $BASE/api/products/$ID_T1_OWN)" "header: TENANT_001 own"
    check_blocked "$(code -H 'X-Tenant-Id: TENANT_001' $BASE/api/products/$ID_T2_OWN)" "header: cross blocked"
    check_code 200 "$(code -H 'X-Tenant-Id: TENANT_002' $BASE/api/products/$ID_T2_OWN)" "header: TENANT_002 own"

    stop_app
}

# ─── 2) fixed ───
test_fixed() {
    echo; echo "════ 2) strategy=fixed ════"
    write_props "tenantwall.tenant.strategy=fixed
tenantwall.tenant.fixedValue=TENANT_001"
    start_app || return

    check_code 200 "$(code $BASE/api/products/$ID_T1_OWN)" "fixed: id=own"
    check_blocked "$(code $BASE/api/products/$ID_T2_OWN)" "fixed: id=cross blocked"

    stop_app
}

# ─── 3) session ───
test_session() {
    echo; echo "════ 3) strategy=session ════"
    write_props "tenantwall.tenant.strategy=session
tenantwall.tenant.sessionKey=TENANT_ID"
    start_app || return

    # Wait for session manager readiness
    sleep 2

    local jar="/tmp/tw-cookies.txt"; rm -f "$jar"

    local session_resp
    session_resp=$(curl -s -c "$jar" -X POST $BASE/api/admin/session/TENANT_001)

    if ! grep -q "JSESSIONID" "$jar" 2>/dev/null; then
        bad "session: cookie NOT saved (create failed: $session_resp)"
        rm -f "$jar"
        stop_app
        return
    fi

    echo "  Session ID: $(grep JSESSIONID "$jar" | awk '{print $NF}')"
    sleep 1

    check_code 200 "$(code -b "$jar" $BASE/api/products/$ID_T1_OWN)" "session: own"
    check_blocked "$(code -b "$jar" $BASE/api/products/$ID_T2_OWN)" "session: cross blocked"

    rm -f "$jar"
    stop_app
}

# ─── 4) cookie ───
test_cookie() {
    echo; echo "════ 4) strategy=cookie ════"
    write_props "tenantwall.tenant.strategy=cookie
tenantwall.tenant.cookieName=tenant_id"
    start_app || return

    check_code 200 "$(code --cookie 'tenant_id=TENANT_001' $BASE/api/products/$ID_T1_OWN)" "cookie: own"
    check_blocked "$(code --cookie 'tenant_id=TENANT_001' $BASE/api/products/$ID_T2_OWN)" "cookie: cross blocked"

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

    check_code 200 "$(code -H "Authorization: Bearer $t1" $BASE/api/products/$ID_T1_OWN)" "jwt: TENANT_001 own"
    check_blocked "$(code -H "Authorization: Bearer $t1" $BASE/api/products/$ID_T2_OWN)" "jwt: cross blocked"
    check_code 200 "$(code -H "Authorization: Bearer $t2" $BASE/api/products/$ID_T2_OWN)" "jwt: TENANT_002 own"

    stop_app
}

# ─── 6) validation ───
test_validation_bad() {
    echo; echo "════ 6) validation: unknown strategy ════"
    write_props "tenantwall.tenant.strategy=bogus"
    : > "$LOG"
    java -javaagent:"${AGENT_JAR_WIN}" \
         -Dtenantwall.config="${PROPS_WIN}" \
         -jar "$APP_WIN" > "$LOG" 2>&1 &
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

# ─── 7) conflict ───
test_conflict_warning() {
    echo; echo "════ 7) conflict: extra strategy keys → warning ════"
    write_props "tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
tenantwall.tenant.cookieName=tenant_id
tenantwall.tenant.sessionKey=TENANT_ID"
    start_app || return

    if grep -q "WARNING: strategy=header but tenantwall.tenant.cookieName\|WARNING: strategy=header but tenantwall.tenant.sessionKey" "$LOG"; then
        ok "conflict: warning printed"
    else
        bad "conflict: no warning"
    fi
    stop_app
}

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