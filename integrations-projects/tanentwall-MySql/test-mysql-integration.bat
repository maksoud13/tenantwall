@echo off
REM ─────────────────────────────────────────────────────────────
REM  TenantWall — MySQL Integration Test Suite (Windows CMD)
REM ─────────────────────────────────────────────────────────────
setlocal EnableDelayedExpansion

set BASE_URL=http://localhost:8080
set MYSQL_CONTAINER=store-mysql
set MYSQL_USER=root
set MYSQL_PASS=root
set MYSQL_DB=store_demo

set PASS=0
set FAIL=0

echo.
echo ╔══════════════════════════════════════════════════════════╗
echo ║  TenantWall — MySQL Integration Test Suite               ║
echo ╚══════════════════════════════════════════════════════════╝
echo.

REM ═══ Pre-flight ═══
echo ─── PRE-FLIGHT ───
curl -s -o NUL -w "App HTTP: %%{http_code}\n" -H "X-Tenant-Id: PING" "%BASE_URL%/api/products"

docker ps --format "{{.Names}}" | findstr /C:"%MYSQL_CONTAINER%" > NUL
if errorlevel 1 (
    echo [FAIL] MySQL container not running
    exit /b 1
)
echo [OK] MySQL container running
echo.

REM ═══ STEP 1: DB State ═══
echo ─── STEP 1: DB State ───
docker exec -i %MYSQL_CONTAINER% mysql -u%MYSQL_USER% -p%MYSQL_PASS% %MYSQL_DB% -e "SELECT 'products' AS t, COUNT(*) AS c FROM products UNION ALL SELECT 'categories', COUNT(*) FROM categories UNION ALL SELECT 'suppliers', COUNT(*) FROM suppliers;"
echo.

REM ═══ STEP 2: READ ═══
echo ─── STEP 2: READ ───
curl -s -H "X-Tenant-Id: SHOP_ALPHA" "%BASE_URL%/api/products" > "%TEMP%\alpha.json"
curl -s -H "X-Tenant-Id: SHOP_BETA"  "%BASE_URL%/api/products" > "%TEMP%\beta.json"

for /f %%c in ('python -c "import json;print(len(json.load(open(r'%TEMP%\alpha.json'))))"') do set ALPHA_COUNT=%%c
for /f %%c in ('python -c "import json;print(len(json.load(open(r'%TEMP%\beta.json'))))"')  do set BETA_COUNT=%%c

echo SHOP_ALPHA products: %ALPHA_COUNT%
echo SHOP_BETA  products: %BETA_COUNT%
echo.

REM ═══ STEP 3: Get One (cross-tenant) ═══
echo ─── STEP 3: GET one (cross-tenant) ───
for /f %%i in ('docker exec -i %MYSQL_CONTAINER% mysql -u%MYSQL_USER% -p%MYSQL_PASS% %MYSQL_DB% -N -B -e "SELECT id FROM products WHERE tenant_id='SHOP_ALPHA' ORDER BY id LIMIT 1"') do set AID=%%i
for /f %%i in ('docker exec -i %MYSQL_CONTAINER% mysql -u%MYSQL_USER% -p%MYSQL_PASS% %MYSQL_DB% -N -B -e "SELECT id FROM products WHERE tenant_id='SHOP_BETA' ORDER BY id LIMIT 1"') do set BID=%%i

echo SHOP_ALPHA own product %AID%:
curl -s -o NUL -w "  HTTP %%{http_code}\n" -H "X-Tenant-Id: SHOP_ALPHA" "%BASE_URL%/api/products/%AID%"
echo SHOP_BETA cross product %AID%:
curl -s -o NUL -w "  HTTP %%{http_code}\n" -H "X-Tenant-Id: SHOP_BETA"  "%BASE_URL%/api/products/%AID%"
echo.

REM ═══ STEP 4: CREATE ═══
echo ─── STEP 4: CREATE ───
set SKU=TEST-%RANDOM%-BAT
curl -s -X POST -H "X-Tenant-Id: SHOP_ALPHA" -H "Content-Type: application/json" -d "{\"name\":\"Batch Test\",\"sku\":\"%SKU%\",\"price\":50.00,\"stockQuantity\":5,\"categoryId\":1,\"supplierId\":1}" "%BASE_URL%/api/products"
echo.
docker exec -i %MYSQL_CONTAINER% mysql -u%MYSQL_USER% -p%MYSQL_PASS% %MYSQL_DB% -e "SELECT id, name, tenant_id FROM products WHERE sku='%SKU%';"
echo.

REM ═══ STEP 5: UPDATE (cross) ═══
echo ─── STEP 5: UPDATE (cross-tenant) ───
echo SHOP_BETA attempts UPDATE on SHOP_ALPHA product %AID%:
curl -s -o NUL -w "  HTTP %%{http_code} (expect 404)\n" -X PUT -H "X-Tenant-Id: SHOP_BETA" -H "Content-Type: application/json" -d "{\"name\":\"HACKED\",\"price\":1.00}" "%BASE_URL%/api/products/%AID%"
echo.

REM ═══ STEP 6: DELETE (cross) ═══
echo ─── STEP 6: DELETE (cross-tenant) ───
echo SHOP_BETA attempts DELETE on SHOP_ALPHA product %AID%:
curl -s -o NUL -w "  HTTP %%{http_code} (expect 404)\n" -X DELETE -H "X-Tenant-Id: SHOP_BETA" "%BASE_URL%/api/products/%AID%"
echo.

REM ═══ STEP 7: CTE Report ═══
echo ─── STEP 7: CTE Report ───
echo SHOP_ALPHA:
curl -s -H "X-Tenant-Id: SHOP_ALPHA" "%BASE_URL%/api/products/reports/stock-value"
echo.
echo SHOP_BETA:
curl -s -H "X-Tenant-Id: SHOP_BETA" "%BASE_URL%/api/products/reports/stock-value"
echo.

REM ═══ STEP 8: Stats ═══
echo ─── STEP 8: Agent Stats ───
echo Smart stats:
curl -s "%BASE_URL%/api/admin/smart-stats"
echo.
echo Cache stats:
curl -s "%BASE_URL%/api/admin/cache-stats"
echo.

echo ═══════════════════════════════════════════════════════════
echo  DONE
echo ═══════════════════════════════════════════════════════════

endlocal