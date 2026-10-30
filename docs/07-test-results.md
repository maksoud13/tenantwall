# Test Results

> Test results are from the reference integration suites shipped in `examples/`. Each runs against a Dockerized database with seeded data for two tenants (`SHOP_ALPHA`, `SHOP_BETA`).

---

## Summary

| Database | Tests | Pass | Fail | Warn | Duration |
|---|---|---|---|---|---|
| **MySQL 8.0** | 30 | **30** | 0 | 0 | 1.2s |
| **SQL Server 2022** | 29 | **29** | 0 | 0 | 1.5s |
| **Oracle XE 21c** | 29 | **29** | 0 | 0 | 1.8s |
| **Total** | **88** | **88** | **0** | **0** | **4.5s** |

---

## Test Categories

| Category | Tests | Purpose |
|---|---|---|
| **DB State** | 1 / DB | Sanity check seed data |
| **READ isolation** | 3 / DB | List, cross-tenant list, per-tenant count |
| **Get-one isolation** | 4 / DB | Own vs cross, both directions |
| **CREATE + injection** | 3 / DB | `tenant_id` auto-injected, cross check |
| **UPDATE isolation** | 4 / DB | Own update works, cross update → 404, DB unchanged |
| **DELETE isolation** | 4 / DB | Own delete works, cross delete → 404, DB unchanged |
| **CTE reports** | 4 / DB | Complex SQL handled by Python parser, no leaks |
| **Python parser stats** | 1 / DB | `pythonCalls > 0`, `fallbacks = 0` |
| **Cache stats** | 1 / DB | `hits > 0` after repeat queries |

---

## Sample Output (MySQL)

```
═══ STEP 1: DB State ═══

  products:   10
  categories: 5
  suppliers:  5
  SHOP_ALPHA: 6
  SHOP_BETA:  4
✅  DB has both tenants

═══ STEP 2: READ — List ═══

✅ PASS  SHOP_ALPHA products count (expected: 6, got: 6)
✅ PASS  SHOP_BETA products count (expected: 4, got: 4)
✅ PASS  SHOP_ALPHA list has no SHOP_BETA data
✅ PASS  SHOP_BETA list has no SHOP_ALPHA data
✅ PASS  SHOP_ALPHA categories count (expected: 3, got: 3)
✅ PASS  SHOP_BETA categories count (expected: 2, got: 2)
✅ PASS  SHOP_ALPHA suppliers count (expected: 3, got: 3)

═══ STEP 3: READ — Get One (cross-tenant) ═══

✅ PASS  ALPHA GET own (expected: 200, got: 200)
✅ PASS  BETA GET cross (expected: 404, got: 404)
✅ PASS  BETA GET own (expected: 200, got: 200)
✅ PASS  ALPHA GET cross (expected: 404, got: 404)

═══ STEP 4: CREATE (POST) ═══

✅ PASS  DB tenant_id='SHOP_ALPHA' (expected: SHOP_ALPHA, got: SHOP_ALPHA)
✅ PASS  DB tenant_id='SHOP_BETA' (expected: SHOP_BETA, got: SHOP_BETA)
✅ PASS  ALPHA doesn't see BETA's new product

═══ STEP 5: UPDATE (PUT) ═══

✅ PASS  ALPHA UPDATE own (expected: 200, got: 200)
✅ PASS  DB reflects update
✅ PASS  BETA UPDATE cross → 404 (expected: 404, got: 404)
✅ PASS  Not modified by cross

═══ STEP 6: DELETE ═══

✅ PASS  BETA DELETE cross → 404 (expected: 404, got: 404)
✅ PASS  Product survives cross delete (expected: 1, got: 1)
✅ PASS  ALPHA DELETE own (expected: 204, got: 204)
✅ PASS  Product deleted (expected: 0, got: 0)

═══ STEP 7: CTE Report (Python parser) ═══

  SHOP_ALPHA: [["Electronics",5,176210.00],["Books",2,27500.00],["Accessories",2,22480.00]]
  SHOP_BETA:  [["Clothing",5,95580.00],["Shoes",2,85425.00]]
✅ PASS  ALPHA report has Electronics
✅ PASS  ALPHA report has no Clothing
✅ PASS  BETA report has Clothing
✅ PASS  BETA report has no Electronics

═══ STEP 8: Agent Smart Stats ═══

  {"fallbacks":0, "localCalls":124, "pythonCalls":10}
✅ PASS  Python parser used (10 calls)

═══ STEP 9: Agent Cache Stats ═══

  {"hits":77, "size":19, "misses":520, "hitRate":"12.90%"}
✅ PASS  Cache has hits (77)

════════════════════════════════════════════════════════════
SUMMARY
════════════════════════════════════════════════════════════
  PASS: 30
  FAIL: 0
  WARN: 0

🎉 All tests passed!
```

---

## How to Run Tests

Each example ships with a self-contained test script:

```bash
# MySQL
cd examples/mysql-spring-boot
bash test-mysql-integration.sh

# SQL Server
cd examples/mssql-spring-boot
bash test-mssql-integration.sh

# Oracle
cd examples/oracle-spring-boot
bash test-oracle-integration.sh
```

### Script Behavior

Every script:

1. **Waits for the DB container** to be `healthy` (with 60s timeout)
2. **Waits for the app** to respond `200` on `/api/products` (with 60s timeout)
3. **Runs 29-30 test assertions**
4. **Reports** PASS / FAIL / WARN summary
5. **Exits** with code 0 (all pass) or 1 (any fail)

### Flags

| Flag | Purpose |
|---|---|
| `DEBUG=1` | Show diagnostic info (curl binary, URLs, retries) |
| `BASE_URL=http://host:port` | Override the app URL |
| `NO_OPEN=1` | Don't try to open the PDF/screenshots |

---

## What Each Test Verifies

### DB State (1 test)

- Confirms both tenants have seeded data
- Confirms the total count matches expectations

### READ Isolation (3 tests)

- `GET /api/products` → returns only own tenant's rows
- Cross-tenant check: response body never contains other tenant's ID
- Count matches DB truth

### Get-One Isolation (4 tests)

- `GET /api/products/{id}` with own tenant → `200`
- `GET /api/products/{id}` with other tenant → `404` (not 403, not 200)
- Reverse direction too — **both ways verified**

### CREATE + Injection (3 tests)

- `POST /api/products` with `tenant_id` missing → auto-injected
- Query the DB directly → `tenant_id` matches the request's tenant header
- Cross-tenant visibility → other tenant doesn't see the new row

### UPDATE Isolation (4 tests)

- Own UPDATE → `200`, DB changed
- Cross UPDATE → `404`, DB **unchanged**
- Verifies no silent cross-tenant modification

### DELETE Isolation (4 tests)

- Cross DELETE → `404`, row **survives**
- Own DELETE → `204`, row **deleted**
- Verifies no silent cross-tenant deletion

### CTE Reports (4 tests)

- Complex `WITH` query (CTE) returns correct data per tenant
- Cross-tenant leak check in CTE results
- Verifies the Python parser path (if running)

### Python Parser Stats (1 test)

- `pythonCalls > 0` → the parser was actually used
- `fallbacks = 0` → no Python failures during the run

### Cache Stats (1 test)

- `hits > 0` after repeat queries
- Verifies caching is working

---

## CI Integration

The test suites are CI-friendly:

- **Exit code 0** on all pass → CI passes
- **Exit code 1** on any fail → CI fails
- **No manual intervention** required
- **Docker-compose** handles DB lifecycle

Example GitHub Actions snippet:

```yaml
- name: Test MySQL integration
  run: |
    cd examples/mysql-spring-boot
    docker compose up -d
    mvn clean package -DskipTests
    java -javaagent:tenantwall-agent.jar=tenantwall.properties -jar target/store-app.jar &
    sleep 20
    bash test-mysql-integration.sh
```

---

## Test Coverage vs Claims

Every claim in the [Support Matrix](./05-support-matrix.md) is backed by test results:

| Claim | Test that proves it |
|---|---|
| "SELECT is tenant-scoped" | STEP 2 + STEP 3 |
| "INSERT auto-injects" | STEP 4 |
| "UPDATE is scoped" | STEP 5 |
| "DELETE is scoped" | STEP 6 |
| "CTEs work" | STEP 7 |
| "Python parser works" | STEP 8 |
| "Cache works" | STEP 9 |

**No unverified claims. No marketing language. Just tests.**

---

**Next:** [Engineering Journey →](./08-engineering-journey.md)