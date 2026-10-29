#!/bin/bash
# ─────────────────────────────────────────────────────────────
#  TenantWall — MySQL Integration Test Suite
#  Usage: ./test-mysql-integration.sh
# ─────────────────────────────────────────────────────────────

set -o pipefail

# ═══ Config ═══
BASE_URL="${BASE_URL:-http://localhost:8080}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-store-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"
MYSQL_DB="${MYSQL_DB:-store_demo}"

# ═══ Colors ═══
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

# ═══ Test counters ═══
PASS=0
FAIL=0
WARN=0

# ═══ Helpers ═══
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
    local haystack="$1" needle="$2" name="$3"
    if [[ "$haystack" == *"$needle"* ]]; then
        printf "${GREEN}✅ PASS${NC}  %s (contains: %s)\n" "$name" "$needle"
        PASS=$((PASS + 1))
    else
        printf "${RED}❌ FAIL${NC}  %s (missing: %s)\n" "$name" "$needle"
        FAIL=$((FAIL + 1))
    fi
}

assert_not_in() {
    local haystack="$1" needle="$2" name="$3"
    if [[ "$haystack" == *"$needle"* ]]; then
        printf "${RED}❌ FAIL${NC}  %s (unexpectedly contains: %s)\n" "$name" "$needle"
        FAIL=$((FAIL + 1))
    else
        printf "${GREEN}✅ PASS${NC}  %s (no leak: %s)\n" "$name" "$needle"
        PASS=$((PASS + 1))
    fi
}

warn() {
    printf "${YELLOW}⚠️  WARN${NC}  %s\n" "$*"
    WARN=$((WARN + 1))
}

# HTTP status helper
http_status() {
    local method="$1" url="$2" tenant="$3" data="$4"
    if [ -n "$data" ]; then
        curl -s -o /dev/null -w "%{http_code}" -X "$method" \
            -H "X-Tenant-Id: $tenant" \
            -H "Content-Type: application/json" \
            -d "$data" "$url"
    else
        curl -s -o /dev/null -w "%{http_code}" -X "$method" \
            -H "X-Tenant-Id: $tenant" "$url"
    fi
}

# JSON count helper
json_count() {
    python -c "import json,sys;print(len(json.load(sys.stdin)))" 2>/dev/null || echo 0
}

# MySQL query helper
mysql_q() {
    docker exec -i "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" -N -B -e "$1" 2>/dev/null
}

# ═══════════════════════════════════════════════════════════
#  START
# ═══════════════════════════════════════════════════════════
echo
printf "${BOLD}${BLUE}╔══════════════════════════════════════════════════════════╗${NC}\n"
printf "${BOLD}${BLUE}║  TenantWall — MySQL Integration Test Suite               ║${NC}\n"
printf "${BOLD}${BLUE}║  %s$(date '+%Y-%m-%d %H:%M:%S')%s  ║${NC}\n" "" ""
printf "${BOLD}${BLUE}╚══════════════════════════════════════════════════════════╝${NC}\n"

# ═══ Pre-flight checks ═══
title "PRE-FLIGHT: Connectivity"
if curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/products" -H "X-Tenant-Id: PING" | grep -q "200"; then
    printf "${GREEN}✅${NC}  App is up at %s\n" "$BASE_URL"
else
    printf "${RED}❌${NC}  App NOT reachable at %s\n" "$BASE_URL"
    printf "${YELLOW}   Start: java -javaagent:... -jar target/tenantwall-store-mysqlexample-1.0.0.jar${NC}\n"
    exit 1
fi

if docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
    printf "${GREEN}✅${NC}  MySQL container '%s' is running\n" "$MYSQL_CONTAINER"
else
    printf "${RED}❌${NC}  MySQL container '%s' not running\n" "$MYSQL_CONTAINER"
    exit 1
fi

# ═══ STEP 1: DB State ═══
title "STEP 1: DB State"
products_count=$(mysql_q "SELECT COUNT(*) FROM products")
categories_count=$(mysql_q "SELECT COUNT(*) FROM categories")
suppliers_count=$(mysql_q "SELECT COUNT(*) FROM suppliers")

echo "  products:   $products_count"
echo "  categories: $categories_count"
echo "  suppliers:  $suppliers_count"

alpha_total=$(mysql_q "SELECT COUNT(*) FROM products WHERE tenant_id='SHOP_ALPHA'")
beta_total=$(mysql_q "SELECT COUNT(*) FROM products WHERE tenant_id='SHOP_BETA'")
echo "  SHOP_ALPHA: $alpha_total"
echo "  SHOP_BETA:  $beta_total"

if [ "$alpha_total" -gt 0 ] && [ "$beta_total" -gt 0 ]; then
    printf "${GREEN}✅${NC}  DB has both tenants\n"
    PASS=$((PASS + 1))
else
    printf "${RED}❌${NC}  DB missing tenant data\n"
    FAIL=$((FAIL + 1))
fi

# ═══ STEP 2: READ (List) ═══
title "STEP 2: READ — List"

alpha_list=$(curl -s -H "X-Tenant-Id: SHOP_ALPHA" "$BASE_URL/api/products")
beta_list=$(curl -s -H "X-Tenant-Id: SHOP_BETA" "$BASE_URL/api/products")

assert_eq "$(echo "$alpha_list" | json_count)" "$alpha_total" "SHOP_ALPHA products count"
assert_eq "$(echo "$beta_list"  | json_count)" "$beta_total"  "SHOP_BETA products count"

# Cross-tenant leak check
assert_not_in "$alpha_list" "SHOP_BETA"  "SHOP_ALPHA list has no SHOP_BETA data"
assert_not_in "$beta_list"  "SHOP_ALPHA" "SHOP_BETA list has no SHOP_ALPHA data"

# Categories
assert_eq "$(curl -s -H "X-Tenant-Id: SHOP_ALPHA" "$BASE_URL/api/categories" | json_count)" \
    "$(mysql_q "SELECT COUNT(*) FROM categories WHERE tenant_id='SHOP_ALPHA'")" \
    "SHOP_ALPHA categories count"

assert_eq "$(curl -s -H "X-Tenant-Id: SHOP_BETA" "$BASE_URL/api/categories" | json_count)" \
    "$(mysql_q "SELECT COUNT(*) FROM categories WHERE tenant_id='SHOP_BETA'")" \
    "SHOP_BETA categories count"

# Suppliers
assert_eq "$(curl -s -H "X-Tenant-Id: SHOP_ALPHA" "$BASE_URL/api/suppliers" | json_count)" \
    "$(mysql_q "SELECT COUNT(*) FROM suppliers WHERE tenant_id='SHOP_ALPHA'")" \
    "SHOP_ALPHA suppliers count"

# ═══ STEP 3: READ (Get One) — Cross-tenant ═══
title "STEP 3: READ — Get One (cross-tenant)"

alpha_first_id=$(mysql_q "SELECT id FROM products WHERE tenant_id='SHOP_ALPHA' ORDER BY id LIMIT 1")
beta_first_id=$(mysql_q  "SELECT id FROM products WHERE tenant_id='SHOP_BETA'  ORDER BY id LIMIT 1")

assert_eq "$(http_status GET "$BASE_URL/api/products/$alpha_first_id" SHOP_ALPHA)" "200" \
    "SHOP_ALPHA GET own product ($alpha_first_id)"

assert_eq "$(http_status GET "$BASE_URL/api/products/$alpha_first_id" SHOP_BETA)" "404" \
    "SHOP_BETA GET cross product ($alpha_first_id)"

assert_eq "$(http_status GET "$BASE_URL/api/products/$beta_first_id" SHOP_BETA)" "200" \
    "SHOP_BETA GET own product ($beta_first_id)"

assert_eq "$(http_status GET "$BASE_URL/api/products/$beta_first_id" SHOP_ALPHA)" "404" \
    "SHOP_ALPHA GET cross product ($beta_first_id)"

# ═══ STEP 4: CREATE ═══
title "STEP 4: CREATE (POST)"

test_sku_a="TEST-$(date +%s)-A"
test_sku_b="TEST-$(date +%s)-B"

create_a=$(curl -s -X POST \
    -H "X-Tenant-Id: SHOP_ALPHA" -H "Content-Type: application/json" \
    -d "{\"name\":\"Test A\",\"sku\":\"$test_sku_a\",\"price\":99.00,\"stockQuantity\":10,\"categoryId\":1,\"supplierId\":1}" \
    "$BASE_URL/api/products")
create_a_id=$(echo "$create_a" | python -c "import json,sys;print(json.load(sys.stdin).get('id','?'))" 2>/dev/null)

if [ "$create_a_id" != "?" ] && [ -n "$create_a_id" ]; then
    printf "${GREEN}✅ PASS${NC}  CREATE product for SHOP_ALPHA (id=$create_a_id)\n"
    PASS=$((PASS + 1))
else
    printf "${RED}❌ FAIL${NC}  CREATE product for SHOP_ALPHA\n"
    echo "  Response: $create_a"
    FAIL=$((FAIL + 1))
fi

# DB verification — tenant_id injected
db_tenant=$(mysql_q "SELECT tenant_id FROM products WHERE sku='$test_sku_a'")
assert_eq "$db_tenant" "SHOP_ALPHA" "DB has tenant_id='SHOP_ALPHA' for new product"

create_b=$(curl -s -X POST \
    -H "X-Tenant-Id: SHOP_BETA" -H "Content-Type: application/json" \
    -d "{\"name\":\"Test B\",\"sku\":\"$test_sku_b\",\"price\":49.00,\"stockQuantity\":20,\"categoryId\":101,\"supplierId\":101}" \
    "$BASE_URL/api/products")
create_b_id=$(echo "$create_b" | python -c "import json,sys;print(json.load(sys.stdin).get('id','?'))" 2>/dev/null)

db_tenant_b=$(mysql_q "SELECT tenant_id FROM products WHERE sku='$test_sku_b'")
assert_eq "$db_tenant_b" "SHOP_BETA" "DB has tenant_id='SHOP_BETA' for new product"

# Cross-tenant visibility after create
assert_not_in "$(curl -s -H "X-Tenant-Id: SHOP_ALPHA" "$BASE_URL/api/products")" \
    "$test_sku_b" "SHOP_ALPHA doesn't see SHOP_BETA's new product"

# ═══ STEP 5: UPDATE ═══
title "STEP 5: UPDATE (PUT)"

# Update own product
upd_status=$(http_status PUT "$BASE_URL/api/products/$alpha_first_id" SHOP_ALPHA \
    '{"name":"Updated Name","price":8888.00}')
assert_eq "$upd_status" "200" "SHOP_ALPHA UPDATE own product ($alpha_first_id)"

db_name=$(mysql_q "SELECT name FROM products WHERE id=$alpha_first_id")
assert_in "$db_name" "Updated Name" "DB reflects SHOP_ALPHA's update"

# Cross-tenant update — should 404
cross_upd=$(http_status PUT "$BASE_URL/api/products/$alpha_first_id" SHOP_BETA \
    '{"name":"HACKED","price":1.00}')
assert_eq "$cross_upd" "404" "SHOP_BETA UPDATE cross product ($alpha_first_id) → 404"

# Verify not modified
db_name_after=$(mysql_q "SELECT name FROM products WHERE id=$alpha_first_id")
assert_not_in "$db_name_after" "HACKED" "Product not modified by cross-tenant"

# ═══ STEP 6: DELETE ═══
title "STEP 6: DELETE"

# Create a throwaway for delete
del_sku="DEL-$(date +%s)"
curl -s -X POST -H "X-Tenant-Id: SHOP_ALPHA" -H "Content-Type: application/json" \
    -d "{\"name\":\"To Delete\",\"sku\":\"$del_sku\",\"price\":1.00,\"stockQuantity\":1,\"categoryId\":1,\"supplierId\":1}" \
    "$BASE_URL/api/products" > /dev/null
del_id=$(mysql_q "SELECT id FROM products WHERE sku='$del_sku'")

# Cross-tenant delete — should 404
cross_del=$(http_status DELETE "$BASE_URL/api/products/$del_id" SHOP_BETA)
assert_eq "$cross_del" "404" "SHOP_BETA DELETE cross product ($del_id) → 404"

db_exists=$(mysql_q "SELECT COUNT(*) FROM products WHERE id=$del_id")
assert_eq "$db_exists" "1" "Product survives cross-tenant DELETE attempt"

# Own delete — should 204
own_del=$(http_status DELETE "$BASE_URL/api/products/$del_id" SHOP_ALPHA)
assert_eq "$own_del" "204" "SHOP_ALPHA DELETE own product ($del_id)"

db_exists=$(mysql_q "SELECT COUNT(*) FROM products WHERE id=$del_id")
assert_eq "$db_exists" "0" "Product deleted successfully"

# ═══ STEP 7: CTE Report (Python parser) ═══
title "STEP 7: CTE Report (Python parser)"

alpha_report=$(curl -s -H "X-Tenant-Id: SHOP_ALPHA" "$BASE_URL/api/products/reports/stock-value")
beta_report=$(curl -s -H "X-Tenant-Id: SHOP_BETA" "$BASE_URL/api/products/reports/stock-value")

echo "  SHOP_ALPHA: $alpha_report"
echo "  SHOP_BETA:  $beta_report"

if [ -z "$alpha_report" ] || [ "$alpha_report" = "[]" ]; then
    warn "SHOP_ALPHA report is empty"
else
    assert_in "$alpha_report" "Electronics" "SHOP_ALPHA report has Electronics"
    assert_not_in "$alpha_report" "Clothing" "SHOP_ALPHA report has no Clothing (SHOP_BETA)"
fi

if [ -z "$beta_report" ] || [ "$beta_report" = "[]" ]; then
    warn "SHOP_BETA report is empty"
else
    assert_in "$beta_report" "Clothing" "SHOP_BETA report has Clothing"
    assert_not_in "$beta_report" "Electronics" "SHOP_BETA report has no Electronics (SHOP_ALPHA)"
fi

# Smart stats
title "STEP 8: Agent Smart Stats"
stats=$(curl -s "$BASE_URL/api/admin/smart-stats")
echo "  $stats"

python_calls=$(echo "$stats" | python -c "import json,sys;print(json.load(sys.stdin).get('pythonCalls',0))" 2>/dev/null)
local_calls=$(echo "$stats"  | python -c "import json,sys;print(json.load(sys.stdin).get('localCalls',0))"  2>/dev/null)
fallbacks=$(echo "$stats"    | python -c "import json,sys;print(json.load(sys.stdin).get('fallbacks',0))"   2>/dev/null)

if [ "$python_calls" -gt 0 ]; then
    printf "${GREEN}✅ PASS${NC}  Python parser was used ($python_calls calls)\n"
    PASS=$((PASS + 1))
else
    warn "Python parser NOT used (0 calls) — CTE report may have fallen back to local"
fi

if [ "$fallbacks" -gt 0 ]; then
    warn "Fallbacks detected: $fallbacks — Python service may be down or failing"
fi

# Cache stats
title "STEP 9: Agent Cache Stats"
cache=$(curl -s "$BASE_URL/api/admin/cache-stats")
echo "  $cache"

hits=$(echo "$cache" | python -c "import json,sys;print(json.load(sys.stdin).get('hits',0))" 2>/dev/null)
if [ "$hits" -gt 0 ]; then
    printf "${GREEN}✅ PASS${NC}  Cache has hits ($hits)\n"
    PASS=$((PASS + 1))
else
    warn "Cache has 0 hits — expected after multiple identical queries"
fi

# ═══ Summary ═══
echo
hr
printf "${BOLD}SUMMARY${NC}\n"
hr
printf "  ${GREEN}PASS: %d${NC}\n" "$PASS"
printf "  ${RED}FAIL: %d${NC}\n" "$FAIL"
printf "  ${YELLOW}WARN: %d${NC}\n" "$WARN"
echo

if [ "$FAIL" -gt 0 ]; then
    printf "${RED}${BOLD}❌ Some tests FAILED — review above${NC}\n"
    exit 1
elif [ "$WARN" -gt 0 ]; then
    printf "${YELLOW}${BOLD}⚠️  All tests passed, with warnings${NC}\n"
    exit 0
else
    printf "${GREEN}${BOLD}🎉 All tests passed!${NC}\n"
    exit 0
fi