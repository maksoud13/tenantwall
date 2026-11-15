#!/usr/bin/env bash
set -u

BASE="http://localhost:8080"
PARSER="http://127.0.0.1:8000"

ID_T1_OWN=800001
ID_T2_OWN=900001

PASS=0
FAIL=0
ok()   { echo "✅ $1"; PASS=$((PASS+1)); }
bad()  { echo "❌ $1"; FAIL=$((FAIL+1)); }

echo "═══ 1. Parser health (no auth) ═══"
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$PARSER/")
[ "$CODE" = "200" ] && ok "parser health ($CODE)" || bad "parser health ($CODE)"

echo ""
echo "═══ 2. Parser without JWT → expect 401 ═══"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$PARSER/api/modify" \
    -H "Content-Type: application/json" \
    -d '{"sql":"SELECT 1","column":"tenant_id","value":"T1","dialect":"mysql"}')
[ "$CODE" = "401" ] && ok "401 without JWT" || bad "expected 401, got $CODE"

echo ""
echo "═══ 3. Megaexample health ═══"
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/health")
[ "$CODE" = "200" ] && ok "health ($CODE)" || bad "health ($CODE)"

echo ""
echo "═══ 4. CTE via megaexample (uses JWT) → expect 200 ═══"
CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Tenant-Id: TENANT_001" \
    "$BASE/api/reports/category-revenue")
[ "$CODE" = "200" ] && ok "CTE ($CODE)" || bad "CTE (expected 200, got $CODE)"

echo ""
echo "═══ 5. Cross-tenant → expect 403 or 404 ═══"
CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Tenant-Id: TENANT_001" \
    "$BASE/api/products/$ID_T2_OWN")
if [ "$CODE" = "403" ] || [ "$CODE" = "404" ]; then
    ok "cross-tenant blocked ($CODE)"
else
    bad "cross-tenant leak! ($CODE)"
fi
echo ""
echo "═══ 6. Own tenant → expect 200 ═══"
CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Tenant-Id: TENANT_001" \
    "$BASE/api/products/$ID_T1_OWN")
[ "$CODE" = "200" ] && ok "own tenant ($CODE)" || bad "own tenant ($CODE)"

echo ""
echo "═══ 7. Stats endpoint ═══"
curl -s "$BASE/api/admin/stats" | head -c 200
echo ""

echo ""
echo "════════════════════════════════════════"
echo "JWT E2E: PASS=$PASS FAIL=$FAIL"
exit $FAIL