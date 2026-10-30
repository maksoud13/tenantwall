# Security Model

---

## Guarantees

### 1. Every SELECT is tenant-scoped

No query reaches the database without `tenant_id = ?` in the `WHERE` clause (unless the table is explicitly excluded).

### 2. Every INSERT auto-injects the tenant

```sql
INSERT INTO products (name, price) VALUES (?, ?)
   ↓
INSERT INTO products (name, price, tenant_id) VALUES (?, ?, 'SHOP_ALPHA')
```

### 3. Every UPDATE/DELETE is tenant-scoped

```sql
UPDATE products SET price = 100 WHERE id = 5
   ↓
UPDATE products SET price = 100 WHERE id = 5 AND tenant_id = 'SHOP_ALPHA'
```

### 4. Cross-tenant access returns 404, not 403

No information leak about whether the row exists under a different tenant.

### 5. Fail-closed by default (production)

If no tenant is in context:

- `onMissing=fail` → `SecurityException` (recommended for production)
- `onMissing=skip` → logs warning, executes unchanged (development only)

### 6. Cache invalidation on tenant change

`TenantwallConfig.setFilterColumn("org_id")` clears the query cache automatically.

---

## Attack Vectors Covered

| Attack | Covered? | Mechanism |
|---|---|---|
| Developer forgets `WHERE tenant_id` | ✅ | Auto-injection |
| Native SQL bypass | ✅ | JDBC Statement interceptor |
| ORM bypass (JPQL → SQL) | ✅ | Hibernate interceptor |
| Stored procedure call | ⚠️ | Outer call filtered; internals not |
| `UNION` cross-tenant | ✅ | Each branch filtered independently |
| Subquery cross-tenant | ✅ | Recursive expression processing |
| CTE cross-tenant | ✅ | `WithItemsList` processed |
| `IN` subquery with wrong tenant | ✅ | Handled via `processExpression` |
| SQL injection after filter | ✅ | Parser rejects malformed SQL |
| `OR` clause bypass (`1=1 OR ...`) | ✅ | Parenthesis wrapping |

---

## Threat Model — Out of Scope

> [!WARNING]
> **What TenantWall does NOT protect against:**
>
> - **Direct database access** — bypasses the JVM entirely
> - **Stored procedure internals** — outer call is filtered, internal queries are not
> - **Application-level bugs** — returning the wrong tenant's row after a valid query
> - **JVM tampering** — if an attacker controls the JVM, no library is safe
> - **SQL injection in tenant_id** — the tenant value is treated as a literal, not raw SQL

---

## Defense in Depth

TenantWall should be **one layer** of your security strategy:

```
Layer 1: Authentication (who is this user?)
Layer 2: Authorization (what can they access?)
Layer 3: 🛡️ TenantWall (cross-tenant isolation at SQL layer)
Layer 4: Database constraints (NOT NULL, FK, CHECK)
Layer 5: Audit logging (who did what when?)
```

TenantWall **does not replace** application-level authorization. It's a **safety net** against developer mistakes.

---

## The `onMissing` Decision

This is the single most important config decision.

### Development Mode (`onMissing=skip`)

```properties
tenantwall.filter.onMissing=skip
```

**Behavior:**

- Tenant context is null → execute original SQL
- Log warning: `⚠️ No tenant in context, skipping filter`
- Application continues normally

**Use when:**

- `@PostConstruct` seed data runs before any HTTP request
- Scheduled jobs that span all tenants
- Migration scripts
- Development environment

### Production Mode (`onMissing=fail`)

```properties
tenantwall.filter.onMissing=fail
```

**Behavior:**

- Tenant context is null → throw `SecurityException`
- Request fails with 500
- Application **stops the query** immediately

**Use when:**

- Every query **must** have tenant context
- You want to catch bugs before they leak data
- Compliance requires fail-closed behavior

> [!IMPORTANT]
> **Recommendation:** Start with `skip` in staging. Once you've verified that every code path sets the tenant, migrate to `fail` in production.

---

## SQL Injection Safety

The `tenant_id` value is **never interpolated as raw SQL**. It's always:

```java
new StringValue(value)   // JSqlParser
```

Which renders as:

```sql
WHERE tenant_id = 'SHOP_ALPHA'
```

**Not:**

```sql
WHERE tenant_id = SHOP_ALPHA  -- injection risk!
```

If an attacker controls the `X-Tenant-Id` header, they can only:
- Set the tenant to a **different existing tenant**
- **Not** inject SQL fragments
- **Not** break out of the string literal

**The header value is treated as data, not code.**

---

## Audit Trail

TenantWall logs (with `System.err` for diagnostic visibility):

```
🎯 [Hibernate] Modified: SELECT ... WHERE tenant_id = 'ACME'
```

**For compliance**, redirect agent logs to your audit log aggregation:

```properties
# Log4j2 / Logback config
tenantwall.logger.level=INFO
tenantwall.logger.appender=audit
```

> [!NOTE]
> Agent logs currently use `System.err`. A proper logging integration (`java.util.logging`) is on the roadmap for v1.1.

---

## Verifying Isolation in Production

### 1. Cross-tenant response codes

```bash
# Should return 200 (own product)
curl -H "X-Tenant-Id: ACME" http://app/api/products/1

# Should return 404 (cross-tenant)
curl -H "X-Tenant-Id: WIDGETCO" http://app/api/products/1
```

If you see 200 for the second one, **you have a leak**.

### 2. Test in staging with production data

Copy a sanitized snapshot of production data. Run all endpoints against it. Look for:
- Any response with data from another tenant
- Any 200 where 404 was expected
- Any missing `WHERE tenant_id` in query logs

### 3. Enable database query logging

**MySQL:**

```sql
SET GLOBAL general_log = 'ON';
```

**SQL Server:** Extended Events → `sql_statement_starting`

**Oracle:** `AUDIT SELECT TABLE`

Verify every query has `WHERE tenant_id = ?`.

---

## Security Checklist

Before going to production:

- [ ] All queries have `WHERE tenant_id` (verify via DB query log)
- [ ] `onMissing=fail` is set
- [ ] All endpoints tested for cross-tenant access → 404
- [ ] `excludedTables` list is audited (only metadata tables)
- [ ] Agent logs go to centralized audit system
- [ ] Staging mirror of production data tested
- [ ] Incident response plan documents "tenant leak" scenario

---

**Next:** [Test Results →](./07-test-results.md)