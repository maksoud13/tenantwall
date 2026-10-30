# FAQ

---

## General

### Is it production-ready?

**Yes, with caveats:**

- ✅ 88/88 integration tests passing across 3 databases
- ✅ Zero data leaks in tested scenarios
- ✅ Fail-closed mode available (`onMissing=fail`)
- ⚠️ Not yet battle-tested at scale (no public "millions of queries" case study)
- ⚠️ Python parser is optional but recommended for complex SQL

**Recommendation:** Start with `onMissing=skip` in staging, migrate to `onMissing=fail` in production **after** verifying all query paths are covered.

---

### What's the performance overhead?

From benchmarks (10K queries/sec load):

| Scenario | Overhead |
|---|---|
| Cache hit (repeated SQL) | **~0 μs** |
| Cache miss, simple query (local JSqlParser) | **~0.8 ms** |
| Cache miss, complex query (Python sidecar) | **~12 ms** (network RTT dominates) |
| Fail-open fallback (Python down) | **~1.5 ms** |

**The cache is the key** — after warmup, most queries pay **zero** parsing cost.

---

### Does it work with Hibernate 5.x?

**Not verified.** The `StatementPreparerImpl` API differs between Hibernate 5 and 6. The current release targets **Hibernate 6.2+**.

Community contribution welcome — [open an issue](https://github.com/maksoud13/tenantwall/issues) if you want to test Hibernate 5.x.

---

### Can I use it without Spring Boot?

**Yes.** TenantWall integrates at the JDBC layer, not Spring. You need:

- Hibernate 6.2+ (or use plain `Statement`/`PreparedStatement` — Layer 2/3 interceptor)
- A `ThreadLocal` holding the current tenant (you implement `TenantContext` equivalent)
- The agent JAR on the `-javaagent` flag

Spring Boot is convenience, not a requirement.

---

### Does it work with connection pooling?

**Yes**, with caveats:

- **HikariCP:** ✅ Verified (used in all integration tests)
- **Tomcat JDBC:** ✅ Should work (same JDBC spec)
- **C3P0:** ⚠️ Not tested

The key insight: **the rewrite happens before the connection is obtained from the pool**, so pool implementation doesn't affect rewriting.

---

## Architecture & Behavior

### What happens if the Python parser is down?

**Fail-open fallback** to local JSqlParser:

- Simple queries → rewritten correctly ✅
- Complex queries (CTE, window functions) → **attempted** by JSqlParser; if it fails, the original SQL runs unmodified ⚠️

In production, **monitor the `fallbacks` counter** — a steady increase means Python is down.

---

### Is there a per-tenant schema option?

**Not yet.** Current version supports **row-level isolation** (shared schema, `tenant_id` column).

**Schema-per-tenant** (each tenant has own schema) is on the roadmap for v2.0.

---

### How do I handle the `tenants` table itself?

Exclude it in config:

```properties
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history
```

The `tenants` table typically stores tenant metadata (not per-tenant data), so it shouldn't be filtered.

---

### What about multi-tenant queries intentionally spanning tenants?

E.g., a billing job that sums across all tenants:

```java
List<Object[]> allRevenue = repo.getTotalRevenueAcrossAllTenants();
```

**Options:**

1. Mark the query as **excluded** by table name (`excludedTables=billing_view`)
2. Use a **system tenant** (`TenantContext.setTenant("SYSTEM")`)
3. Bypass the agent for **admin services** (separate JVM without `-javaagent`)

There's no single answer — the design intent is **isolation by default**, with explicit opt-outs.

---

### How do I debug why a query isn't filtered?

1. **Check the logs** — you should see `🎯 [Hibernate] Modified: ...`
2. **Verify tenant context** — `TenantContext.getTenant()` should not be null
3. **Check config** — `tenantwall.enabled=true`
4. **Check the layer** — Hibernate queries go through Layer 1; native JDBC through Layer 2
5. **Enable MySQL/MSSQL/Oracle general log** — see what reaches the DB

If the log shows `Modified SQL` but the DB receives the original, you're on Layer 3 (Javassist proxy issue) — see [Engineering Journey](./08-engineering-journey.md).

---

## Configuration

### What's the difference between `onMissing=skip` and `onMissing=fail`?

| Mode | Behavior | Use Case |
|---|---|---|
| `skip` | Executes original SQL, logs warning | Development, staging |
| `fail` | Throws `SecurityException` | **Production** |

> [!IMPORTANT]
> In production, always use `onMissing=fail`. If a query fires without tenant context, that's a bug — better to fail than leak.

---

### Why does `@PostConstruct` seed data fail with `onMissing=fail`?

`@PostConstruct` runs **before** any HTTP request, so `TenantContext` is empty. The agent refuses to filter — which is correct behavior.

**Options:**

1. Use `onMissing=skip` during development
2. Manually set the tenant in `@PostConstruct`:
   ```java
   @PostConstruct
   public void init() {
       TenantContext.setTenant("SEED");
       try {
           // seed data
       } finally {
           TenantContext.clear();
       }
   }
   ```
3. Use a separate `data.sql` / Flyway migration for seed data

---

### Can I change the filter column name?

**Yes.** Set:

```properties
tenantwall.filter.column=org_id
```

Common alternatives:

- `tenant_id` (default)
- `org_id`
- `account_id`
- `customer_id`
- `workspace_id`

The agent will inject `WHERE org_id = '...'` instead.

---

### How do I disable TenantWall temporarily?

```properties
tenantwall.enabled=false
```

Or:

```bash
-Dtenantwall.enabled=false
```

Or remove the `-javaagent` flag entirely.

> [!CAUTION]
> **Never disable in production.** Disabling removes all tenant filters — silent data leaks will happen.

---

## Troubleshooting

### Error: `NoClassDefFoundError: TenantContext`

**Cause:** Missing `-javaagent` flag — the agent classes aren't on the classpath.

**Fix:**

```bash
java -javaagent:tenantwall-agent.jar=tenantwall.properties -jar app.jar
```

---

### Error: `Cannot open database` (SQL Server)

**Cause:** Database doesn't exist.

**Fix:**

```bash
docker exec -i store-mssql /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "TenantWall123!" -C \
  -Q "CREATE DATABASE store_demo;"
```

---

### Error: Oracle `invalid username/password`

**Cause:** Wrong credentials or the user wasn't created.

**Fix:** Check the `docker-compose.yml`:

```yaml
environment:
  ORACLE_PASSWORD: "TenantWall123!"
  APP_USER: store_demo
  APP_USER_PASSWORD: "TenantWall123!"
```

The `APP_USER` is created automatically on first startup. If it's not, `docker compose down -v` and restart (⚠️ deletes data).

---

### Query has `WHERE tenant_id` but DB got original

**Cause:** You're on Layer 3 (Javassist proxy) — `@Advice.AllArguments(readOnly=false)` doesn't propagate.

**Fix:** Use Hibernate 6.2+ so Layer 1 (Hibernate SPI) is used instead. See [Engineering Journey](./08-engineering-journey.md).

---

### Emoji garbled in Windows console

**Cause:** Console code page doesn't support UTF-8.

**Fix:**

```powershell
chcp 65001
```

Or configure Windows Terminal to use UTF-8 by default.

---

## Security

### Can an attacker exploit the `X-Tenant-Id` header?

**No.** The tenant value is treated as **data**, not code:

```java
new StringValue(value)   // JSqlParser — creates a string literal
```

Which renders as:

```sql
WHERE tenant_id = 'attacker-controlled-value'
```

An attacker can:
- Set their own tenant (if they have credentials — that's auth)
- **Cannot** inject SQL fragments
- **Cannot** break out of the string literal
- **Cannot** access another tenant's data (the WHERE clause still applies)

**Still:** Validate `X-Tenant-Id` against your auth system — don't trust it blindly. TenantWall prevents SQL injection, not auth bypass.

---

### Does TenantWall protect against insider threats?

**Partially.** It ensures no query can accidentally leak data across tenants. But it doesn't prevent:

- An authenticated user with valid tenant ID from accessing that tenant's data (that's expected)
- A DBA with direct database access (out of scope)
- Application-level auth bugs (TenantWall is one layer of defense)

**Defense in depth** is required — TenantWall is not a replacement for proper auth.

---

### How do I audit which queries ran for which tenant?

1. **Agent logs** — `🎯 [Hibernate] Modified: ... WHERE tenant_id = 'ACME'`
2. **Redirect to file**: `2> agent.log`
3. **Ship to your log aggregator** (Splunk, Datadog, ELK)
4. **Database query log** (MySQL `general_log`, SQL Server XEvents)

The agent logs are informational — for compliance, also use DB-side audit.

---

## Migration & Adoption

### How do I migrate from single-tenant to multi-tenant?

1. **Schema migration** — add `tenant_id` column to all data tables (default to existing tenant's ID)
2. **Add index** — `CREATE INDEX idx_tenant ON table(tenant_id)`
3. **Deploy TenantWall** — add dependency + config + `-javaagent` flag
4. **Add TenantFilter** — read tenant from session/JWT, set `TenantContext`
5. **Test** — cross-tenant access should return 404
6. **Enable `onMissing=fail`** in production

---

### How do I add TenantWall to an existing app?

**Minimal changes:**

- `pom.xml` — add `agent-module` dependency (scope `provided`)
- One `TenantFilter` class (10 lines)
- `tenantwall.properties` config file
- `-javaagent` flag on deployment

**No changes to:**

- Controllers
- Repositories
- Entities (unless you want `@TenantId` for extra safety)
- SQL queries

---

### Can I test TenantWall without deploying?

**Yes.** Each example in `examples/` is a complete test environment:

```bash
cd examples/mysql-spring-boot
docker compose up -d
mvn clean package -DskipTests
java -javaagent:tenantwall-agent.jar=tenantwall.properties -jar target/store-app.jar &
bash test-mysql-integration.sh
```

Expected: 30/30 passing in under 5 seconds after startup.

---

## Comparison

### TenantWall vs Hibernate `@TenantId`

| Feature | TenantWall | Hibernate `@TenantId` |
|---|---|---|
| Code changes | Zero | Annotate every entity |
| Native SQL | ✅ Covered | ❌ Not covered |
| Complex queries | ✅ CTEs, window functions | ⚠️ ORM-only |
| Framework lock-in | None | Hibernate |
| DB support | Any JDBC | Any (but ORM-bound) |

**Use TenantWall if:** You want zero code changes and full coverage.

**Use Hibernate `@TenantId` if:** You're already 100% ORM and want the safest possible path.

**Use both if:** You want maximum safety (defense in depth).

---

### TenantWall vs Row-Level Security (RLS)

| Feature | TenantWall | RLS |
|---|---|---|
| DBA involvement | None | Required |
| Code changes | Zero | Some (session context) |
| Cross-DB support | ✅ Single config | ❌ Per-DB syntax |
| Performance | Cache + parse | Native |
| Debugging | Agent logs | DB logs |

**Use TenantWall if:** You want DB-agnostic, zero-code isolation.

**Use RLS if:** You're on a single DB, want max performance, and have DBA support.

---

**Next:** [License →](./13-license.md)