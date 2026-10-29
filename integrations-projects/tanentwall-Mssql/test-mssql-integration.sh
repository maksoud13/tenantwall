#!/bin/bash
# ─────────────────────────────────────────────────────────────
#  TenantWall — MSSQL Integration Test Suite (v2 - robust)
# ─────────────────────────────────────────────────────────────
export MSYS_NO_PATHCONV=1
set -o pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MSSQL_CONTAINER="${MSSQL_CONTAINER:-store-mssql}"
MSSQL_USER="${MSSQL_USER:-sa}"
MSSQL_PASS="${MSSQL_PASS:-TenantWall123!}"
MSSQL_DB="${MSSQL_DB:-store_demo}"
DEBUG="${DEBUG:-1}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'; BOLD='\033[1m'

PASS=0; FAIL=0; WARN=0

hr()   { printf '%*s\n' 70 '' | tr ' ' '─'; }
title(){ echo; printf "${BOLD}${CYAN}═══ %s ═══${NC}\n" "$*"; echo; }

assert_eq() {
    local actual="$1" expected="$2" name="$3"
    if [ "$actual" = "$expected" ]; then
        printf "${GREEN}✅ PASS${NC}  %s (expected: %s, got: %s)\n" "$name" "$expected" "$actual"
        PASS=$((PASS + 1))
    else
        printf "${RED}❌ FAIL${NC}  %s (expected: %s, got: %s)\n" "$name" "$expected" "$actual"
        FAIL=$((FAIL + 1))
    fi
}

assert_in() {
    local h="$1" n="$2" name="$3"
    if [[ "$h" == *"$n"* ]]; then
        printf "${GREEN}✅ PASS${NC}  %s\n" "$name"; PASS=$((PASS + 1))
    else
        printf "${RED}❌ FAIL${NC}  %s (missing: %s)\n" "$name" "$n"; FAIL=$((FAIL + 1))
    fi
}

assert_not_in() {
    local h="$1" n="$2" name="$3"
    if [[ "$h" != *"$n"* ]]; then
        printf "${GREEN}✅ PASS${NC}  %s\n" "$name"; PASS=$((PASS + 1))
    else
        printf "${RED}❌ FAIL${NC}  %s (unexpectedly contains: %s)\n" "$name" "$n"; FAIL=$((FAIL + 1))
    fi
}

warn() { printf "${YELLOW}⚠️  WARN${NC}  %s\n" "$*"; WARN=$((WARN + 1)); }

# ═══ Curl wrapper — uses curl.exe on Windows (more reliable) ═══
CURL_BIN="curl"
if command -v curl.exe >/dev/null 2>&1; then
    CURL_BIN="curl.exe"
fi

http_status() {
    local m="$1" u="$2" t="$3" d="$4"
    if [ -n "$d" ]; then
        $CURL_BIN -s -o /dev/null -w "%{http_code}" --max-time 10 -X "$m" \
            -H "X-Tenant-Id: $t" -H "Content-Type: application/json" -d "$d" "$u" 2>/dev/null
    else
        $CURL_BIN -s -o /dev/null -w "%{http_code}" --max-time 10 -X "$m" \
            -H "X-Tenant-Id: $t" "$u" 2>/dev/null
    fi
}

# Get HTTP body with tenant header
http_get() {
    $CURL_BIN -s --max-time 10 -H "X-Tenant-Id: $2" "$1" 2>/dev/null
}

json_count() { python -c "import json,sys;print(len(json.load(sys.stdin)))" 2>/dev/null || echo 0; }

mssql_q() {
    MSYS_NO_PATHCONV=1 docker exec -i "$MSSQL_CONTAINER" \
        /opt/mssql-tools18/bin/sqlcmd \
        -S localhost -U "$MSSQL_USER" -P "$MSSQL_PASS" -C \
        -d "$MSSQL_DB" -h -1 -W -Q "SET NOCOUNT ON; $1" 2>/dev/null \
        | tr -d '\r' | sed '/^$/d'
}

# ═══ PRE-FLIGHT: Wait for app (up to 60s) ═══
wait_for_app() {
    local tenant="SHOP_ALPHA"
    local url="$BASE_URL/api/products"
    local max_attempts=60
    local status=""
    local curl_cmd="$CURL_BIN -s -o /dev/null -w '%{http_code}' --max-time 5 -H 'X-Tenant-Id: $tenant' $url"

    if [ "$DEBUG" = "1" ]; then
        echo "  Using curl binary: $(command -v $CURL_BIN || echo 'NOT FOUND')"
        echo "  Testing: $url"
        echo "  Command: $curl_cmd"
        echo ""
    fi

    echo -n "  Waiting for app at $BASE_URL "
    for i in $(seq 1 $max_attempts); do
        status=$($CURL_BIN -s -o /dev/null -w "%{http_code}" --max-time 5 \
            -H "X-Tenant-Id: $tenant" "$url" 2>/dev/null)

        if [ "$status" = "200" ]; then
            echo ""
            printf "  ${GREEN}✅${NC}  App responded 200 (attempt ${i}/${max_attempts})\n"
            return 0
        fi
        echo -n "."
        sleep 1
    done

    echo ""
    printf "  ${RED}❌${NC}  App did not respond 200 after ${max_attempts}s\n"
    printf "  Last HTTP status: '%s'\n" "$status"
    printf "  Diagnostic:\n"
    printf "    %s -v -H 'X-Tenant-Id: SHOP_ALPHA' %s\n" "$CURL_BIN" "$url"
    printf "    netstat -ano | grep :8080\n"
    printf "    tasklist | grep -i java\n"
    return 1
}

# ═══ PRE-FLIGHT: Wait for MSSQL container (up to 120s) ═══
wait_for_mssql() {
    local max_attempts=60

    echo -n "  Waiting for MSSQL container '$MSSQL_CONTAINER' "
    for i in $(seq 1 $max_attempts); do
        if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${MSSQL_CONTAINER}$"; then
            local health
            health=$(docker inspect -f '{{.State.Health.Status}}' "$MSSQL_CONTAINER" 2>/dev/null)
            if [ "$health" = "healthy" ]; then
                echo ""
                printf "  ${GREEN}✅${NC}  Container healthy (attempt ${i}/${max_attempts})\n"
                return 0
            fi
        fi
        echo -n "."
        sleep 2
    done

    echo ""
    printf "  ${RED}❌${NC}  Container not healthy after 120s\n"
    printf "  Diagnostic:\n"
    printf "    docker ps -a --filter 'name=%s'\n" "$MSSQL_CONTAINER"
    printf "    docker logs %s --tail 30\n" "$MSSQL_CONTAINER"
    return 1
}

# ═══ HEADER ═══
echo
printf "${BOLD}${BLUE}╔══════════════════════════════════════════════════════════╗${NC}\n"
printf "${BOLD}${BLUE}║  TenantWall — MSSQL Integration Test Suite (v2)          ║${NC}\n"
printf "${BOLD}${BLUE}║  %s  ║${NC}\n" "$(date '+%Y-%m-%d %H:%M:%S')"
printf "${BOLD}${BLUE}╚══════════════════════════════════════════════════════════╝${NC}\n"

# ═══ PRE-FLIGHT ═══
title "PRE-FLIGHT: Connectivity"

wait_for_mssql || exit 1
wait_for_app   || exit 1

# ═══ STEP 1: DB State ═══
title "STEP 1: DB State"
products_count=$(mssql_q "SELECT COUNT(*) FROM products")
categories_count=$(mssql_q "SELECT COUNT(*) FROM categories")
suppliers_count=$(mssql_q "SELECT COUNT(*) FROM suppliers")
alpha_total=$(mssql_q "SELECT COUNT(*) FROM products WHERE tenant_id='SHOP_ALPHA'")
beta_total=$(mssql_q  "SELECT COUNT(*) FROM products WHERE tenant_id='SHOP_BETA'")

echo "  products:   $products_count"
echo "  categories: $categories_count"
echo "  suppliers:  $suppliers_count"
echo "  SHOP_ALPHA: $alpha_total"
echo "  SHOP_BETA:  $beta_total"

if [ "$alpha_total" -gt 0 ] && [ "$beta_total" -gt 0 ]; then
    printf "${GREEN}✅${NC}  DB has both tenants\n"; PASS=$((PASS + 1))
else
    printf "${RED}❌${NC}  DB missing tenant data\n"; FAIL=$((FAIL + 1))
fi

# ═══ STEP 2: READ ═══
title "STEP 2: READ — List"
alpha_list=$(http_get "$BASE_URL/api/products" "SHOP_ALPHA")
beta_list=$(http_get  "$BASE_URL/api/products" "SHOP_BETA")

assert_eq "$(echo "$alpha_list" | json_count)" "$alpha_total" "SHOP_ALPHA products count"
assert_eq "$(echo "$beta_list"  | json_count)" "$beta_total"  "SHOP_BETA products count"
assert_not_in "$alpha_list" "SHOP_BETA"  "SHOP_ALPHA list has no SHOP_BETA data"
assert_not_in "$beta_list"  "SHOP_ALPHA" "SHOP_BETA list has no SHOP_ALPHA data"

assert_eq "$(http_get "$BASE_URL/api/categories" "SHOP_ALPHA" | json_count)" \
    "$(mssql_q "SELECT COUNT(*) FROM categories WHERE tenant_id='SHOP_ALPHA'")" \
    "SHOP_ALPHA categories count"
assert_eq "$(http_get "$BASE_URL/api/categories" "SHOP_BETA" | json_count)" \
    "$(mssql_q "SELECT COUNT(*) FROM categories WHERE tenant_id='SHOP_BETA'")" \
    "SHOP_BETA categories count"
assert_eq "$(http_get "$BASE_URL/api/suppliers" "SHOP_ALPHA" | json_count)" \
    "$(mssql_q "SELECT COUNT(*) FROM suppliers WHERE tenant_id='SHOP_ALPHA'")" \
    "SHOP_ALPHA suppliers count"

# ═══ STEP 3: READ Get One ═══
title "STEP 3: READ — Get One (cross-tenant)"
alpha_first_id=$(mssql_q "SELECT TOP 1 id FROM products WHERE tenant_id='SHOP_ALPHA' ORDER BY id")
beta_first_id=$(mssql_q  "SELECT TOP 1 id FROM products WHERE tenant_id='SHOP_BETA'  ORDER BY id")

assert_eq "$(http_status GET "$BASE_URL/api/products/$alpha_first_id" SHOP_ALPHA)" "200" "ALPHA GET own"
assert_eq "$(http_status GET "$BASE_URL/api/products/$alpha_first_id" SHOP_BETA)"  "404" "BETA GET cross"
assert_eq "$(http_status GET "$BASE_URL/api/products/$beta_first_id"  SHOP_BETA)"  "200" "BETA GET own"
assert_eq "$(http_status GET "$BASE_URL/api/products/$beta_first_id"  SHOP_ALPHA)" "404" "ALPHA GET cross"

# ═══ STEP 4: CREATE ═══
title "STEP 4: CREATE (POST)"
test_sku_a="TEST-$(date +%s)-A"
test_sku_b="TEST-$(date +%s)-B"

$CURL_BIN -s -X POST -H "X-Tenant-Id: SHOP_ALPHA" -H "Content-Type: application/json" \
    -d "{\"name\":\"Test A\",\"sku\":\"$test_sku_a\",\"price\":99.00,\"stockQuantity\":10,\"categoryId\":1,\"supplierId\":1}" \
    "$BASE_URL/api/products" > /dev/null
assert_eq "$(mssql_q "SELECT tenant_id FROM products WHERE sku='$test_sku_a'")" "SHOP_ALPHA" "DB tenant_id='SHOP_ALPHA'"

$CURL_BIN -s -X POST -H "X-Tenant-Id: SHOP_BETA" -H "Content-Type: application/json" \
    -d "{\"name\":\"Test B\",\"sku\":\"$test_sku_b\",\"price\":49.00,\"stockQuantity\":20,\"categoryId\":101,\"supplierId\":101}" \
    "$BASE_URL/api/products" > /dev/null
assert_eq "$(mssql_q "SELECT tenant_id FROM products WHERE sku='$test_sku_b'")" "SHOP_BETA" "DB tenant_id='SHOP_BETA'"

assert_not_in "$(http_get "$BASE_URL/api/products" "SHOP_ALPHA")" \
    "$test_sku_b" "ALPHA doesn't see BETA's new product"

# ═══ STEP 5: UPDATE ═══
title "STEP 5: UPDATE (PUT)"
assert_eq "$(http_status PUT "$BASE_URL/api/products/$alpha_first_id" SHOP_ALPHA '{"name":"Updated Name","price":8888.00}')" "200" "ALPHA UPDATE own"
assert_in "$(mssql_q "SELECT name FROM products WHERE id=$alpha_first_id")" "Updated Name" "DB reflects update"
assert_eq "$(http_status PUT "$BASE_URL/api/products/$alpha_first_id" SHOP_BETA '{"name":"HACKED","price":1.00}')" "404" "BETA UPDATE cross → 404"
assert_not_in "$(mssql_q "SELECT name FROM products WHERE id=$alpha_first_id")" "HACKED" "Not modified by cross"

# ═══ STEP 6: DELETE ═══
title "STEP 6: DELETE"
del_sku="DEL-$(date +%s)"
$CURL_BIN -s -X POST -H "X-Tenant-Id: SHOP_ALPHA" -H "Content-Type: application/json" \
    -d "{\"name\":\"To Delete\",\"sku\":\"$del_sku\",\"price\":1.00,\"stockQuantity\":1,\"categoryId\":1,\"supplierId\":1}" \
    "$BASE_URL/api/products" > /dev/null
del_id=$(mssql_q "SELECT id FROM products WHERE sku='$del_sku'")

assert_eq "$(http_status DELETE "$BASE_URL/api/products/$del_id" SHOP_BETA)"  "404" "BETA DELETE cross → 404"
assert_eq "$(mssql_q "SELECT COUNT(*) FROM products WHERE id=$del_id")"      "1"   "Product survives cross delete"
assert_eq "$(http_status DELETE "$BASE_URL/api/products/$del_id" SHOP_ALPHA)" "204" "ALPHA DELETE own"
assert_eq "$(mssql_q "SELECT COUNT(*) FROM products WHERE id=$del_id")"      "0"   "Product deleted"

# ═══ STEP 7: CTE Report ═══
title "STEP 7: CTE Report (Python parser)"
alpha_report=$(http_get "$BASE_URL/api/products/reports/stock-value" "SHOP_ALPHA")
beta_report=$(http_get  "$BASE_URL/api/products/reports/stock-value" "SHOP_BETA")
echo "  SHOP_ALPHA: $alpha_report"
echo "  SHOP_BETA:  $beta_report"

assert_in     "$alpha_report" "Electronics" "ALPHA report has Electronics"
assert_not_in "$alpha_report" "Clothing"    "ALPHA report has no Clothing"
assert_in     "$beta_report"  "Clothing"    "BETA report has Clothing"
assert_not_in "$beta_report"  "Electronics" "BETA report has no Electronics"

# ═══ STEP 8: Smart stats ═══
title "STEP 8: Agent Smart Stats"
stats=$(http_get "$BASE_URL/api/admin/smart-stats" "SHOP_ALPHA")
echo "  $stats"
py_calls=$(echo "$stats" | python -c "import json,sys;print(json.load(sys.stdin).get('pythonCalls',0))" 2>/dev/null)
fb_calls=$(echo "$stats" | python -c "import json,sys;print(json.load(sys.stdin).get('fallbacks',0))"   2>/dev/null)

if [ "${py_calls:-0}" -gt 0 ]; then
    printf "${GREEN}✅ PASS${NC}  Python parser used ($py_calls calls)\n"; PASS=$((PASS + 1))
else
    warn "Python parser NOT used"
fi
if [ "${fb_calls:-0}" -gt 0 ]; then warn "Fallbacks detected: $fb_calls"; fi

# ═══ STEP 9: Cache ═══
title "STEP 9: Agent Cache Stats"
cache=$(http_get "$BASE_URL/api/admin/cache-stats" "SHOP_ALPHA")
echo "  $cache"
hits=$(echo "$cache" | python -c "import json,sys;print(json.load(sys.stdin).get('hits',0))" 2>/dev/null)
if [ "${hits:-0}" -gt 0 ]; then
    printf "${GREEN}✅ PASS${NC}  Cache has hits ($hits)\n"; PASS=$((PASS + 1))
else
    warn "Cache has 0 hits"
fi

# ═══ SUMMARY ═══
echo; hr
printf "${BOLD}SUMMARY${NC}\n"; hr
printf "  ${GREEN}PASS: %d${NC}\n" "$PASS"
printf "  ${RED}FAIL: %d${NC}\n" "$FAIL"
printf "  ${YELLOW}WARN: %d${NC}\n" "$WARN"
echo

if [ "$FAIL" -gt 0 ]; then
    printf "${RED}${BOLD}❌ Some tests FAILED${NC}\n"; exit 1
elif [ "$WARN" -gt 0 ]; then
    printf "${YELLOW}${BOLD}⚠️  All passed, with warnings${NC}\n"; exit 0
else
    printf "${GREEN}${BOLD}🎉 All tests passed!${NC}\n"; exit 0
fi