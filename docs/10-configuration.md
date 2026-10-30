# Configuration Reference

---

## All Configuration Keys

| Key | Type | Default | Description |
|---|---|---|---|
| `tenantwall.enabled` | boolean | `true` | Master switch |
| `tenantwall.filter.column` | string | `tenant_id` | Column name to filter on |
| `tenantwall.filter.defaultValue` | string | `null` | Fallback if no tenant in context (single-tenant mode) |
| `tenantwall.filter.onMissing` | enum | `skip` | `skip` (dev) or `fail` (prod) |
| `tenantwall.filter.excludedTables` | CSV | (empty) | Tables to never filter (e.g., `tenants,audit_log`) |
| `tenantwall.parser.url` | string | `null` | Python parser URL (null = disabled, local-only) |
| `tenantwall.parser.dialect` | string | `mysql` | DB dialect (`mysql`, `tsql`, `oracle`, `postgres`, ...) |
| `tenantwall.insert.strategy` | enum | `skip` | `skip`, `append`, or `fail` for INSERT without column list |
| `tenantwall.schema.<table>` | CSV | (empty) | Explicit column list for INSERT hints |

---

## Configuration Sources (Priority Order)

1. **Agent args**: `-javaagent:agent.jar=/path/to/config.properties`
2. **System property**: `-Dtenantwall.config=/path/to/config.properties`
3. **System properties**: `-Dtenantwall.filter.column=org_id`
4. **Environment variables**: `TENANTWALL_FILTER_COLUMN=org_id`
5. **Classpath**: `tenantwall.properties` in `src/main/resources`
6. **Defaults**

> [!TIP]
> Use **agent args** for external config files (production). Use **system properties** for Docker/K8s. Use **environment variables** for CI/CD.

---

## Example Configuration Files

### Minimal (development)

```properties
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.filter.onMissing=skip
```

### Production

```properties
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.filter.onMissing=fail
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history
tenantwall.parser.url=http://parser.internal:8000
tenantwall.parser.dialect=mysql
tenantwall.insert.strategy=fail
```

### Full (all options)

```properties
# ═══ Core ═══
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.filter.defaultValue=
tenantwall.filter.onMissing=skip
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history

# ═══ Parser ═══
tenantwall.parser.url=http://localhost:8000
tenantwall.parser.dialect=mysql

# ═══ INSERT handling ═══
tenantwall.insert.strategy=skip

# ═══ Schema hints (for INSERT without column list) ═══
tenantwall.schema.orders=id,customer_id,product_name,total,tenant_id
tenantwall.schema.customers=id,name,tenant_id
```

---

## Runtime Overrides

```java
// Programmatic (before Spring starts)
TenantwallConfig.setFilterColumn("org_id");
TenantwallConfig.setParserUrl("http://parser.internal:8000");
TenantwallConfig.setOnMissing(TenantwallConfig.MissingValueStrategy.FAIL);
TenantwallConfig.setExcludedTables(List.of("tenants", "audit_log"));
```

> [!NOTE]
> `setFilterColumn()` and `setExcludedTables()` clear the query cache automatically — safe to call at runtime.

---

## Per-Database Configuration

### MySQL

```properties
tenantwall.parser.dialect=mysql
```

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/store_demo
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### SQL Server

```properties
tenantwall.parser.dialect=tsql
```

```yaml
spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=store_demo;encrypt=false;trustServerCertificate=true
    username: sa
    password: "TenantWall123!"
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
```

> [!IMPORTANT]
> `encrypt=false;trustServerCertificate=true` is **required** for SQL Server 2022 (TLS is enabled by default).

### Oracle

```properties
tenantwall.parser.dialect=oracle
```

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@//localhost:1521/XEPDB1
    username: store_demo
    password: "TenantWall123!"
    driver-class-name: oracle.jdbc.OracleDriver
```

> [!NOTE]
> `XEPDB1` is the default PDB name in Oracle XE. `store_demo` is the user *and* schema name.

---

## Environment Variable Mapping

Environment variables map to config keys by:

1. Lowercase
2. Replace `_` with `.`
3. Prefix with `tenantwall.`

Example:

| Environment Variable | Config Key |
|---|---|
| `TENANTWALL_FILTER_COLUMN` | `tenantwall.filter.column` |
| `TENANTWALL_PARSER_URL` | `tenantwall.parser.url` |
| `TENANTWALL_PARSER_DIALECT` | `tenantwall.parser.dialect` |
| `TENANTWALL_FILTER_ONMISSING` | `tenantwall.filter.onMissing` |
| `TENANTWALL_ENABLED` | `tenantwall.enabled` |

### Docker Compose Example

```yaml
services:
  app:
    image: my-app:latest
    environment:
      TENANTWALL_FILTER_COLUMN: "org_id"
      TENANTWALL_PARSER_URL: "http://parser:8000"
      TENANTWALL_PARSER_DIALECT: "tsql"
      TENANTWALL_FILTER_ONMISSING: "fail"
```

### Kubernetes Example

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: tenantwall-config
data:
  TENANTWALL_FILTER_COLUMN: "org_id"
  TENANTWALL_PARSER_DIALECT: "postgres"
  TENANTWALL_FILTER_ONMISSING: "fail"
---
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: app
        envFrom:
        - configMapRef:
            name: tenantwall-config
```

---

## The `tenantwall.filter.onMissing` Decision

This is the single most important config choice.

### `skip` (development)

```properties
tenantwall.filter.onMissing=skip
```

- No tenant in context → execute original SQL
- Logs a warning
- **Use when**: seed data runs in `@PostConstruct`, scheduled jobs, migrations

### `fail` (production)

```properties
tenantwall.filter.onMissing=fail
```

- No tenant in context → throw `SecurityException`
- Request fails with HTTP 500
- **Use when**: every query must have tenant context

> [!IMPORTANT]
> Start with `skip` in staging. Migrate to `fail` in production once you've verified every code path sets the tenant.

---

## The `tenantwall.insert.strategy` Decision

Controls what happens when `INSERT INTO t VALUES (...)` has no explicit column list.

| Value | Behavior |
|---|---|
| `skip` | **Default.** Skip the injection, log error |
| `append` | Append `tenant_id` as **last** column (⚠️ only correct if `tenant_id` is last in the table) |
| `fail` | Throw exception |

**Recommendation:** Use `skip` (default) + explicit column lists in your code. If you can't change the code, use `tenantwall.schema.<table>` instead.

### Schema Hint Example

```properties
tenantwall.schema.orders=id,customer_id,product_name,total,tenant_id
```

Now:

```sql
INSERT INTO orders VALUES (?, ?, ?, ?)
```

Becomes:

```sql
INSERT INTO orders VALUES (?, ?, ?, ?, 'SHOP_ALPHA')
```

(The position of `tenant_id` is determined by the schema hint.)

---

## Cache Configuration

The Caffeine cache is **not user-configurable** in v1.0. Fixed values:

| Property | Value |
|---|---|
| Max size | 10,000 entries |
| TTL | 30 minutes |
| Eviction | Window-TinyLFU |
| Key | `{sql}\|{column}\|{value}` |

**Invalidation triggers:**

- `setFilterColumn()` — column changed
- `setParserUrl()` — Python parser URL changed
- `resetStats()` — manual reset
- TTL expiry (30 min)

Configurable cache tuning is on the roadmap for v1.1.

---

## Logging Configuration

Currently, agent logs use `System.err`. For production:

### Redirect to a file

```bash
java -javaagent:agent.jar=config.properties \
     -jar app.jar \
     2> agent.log
```

### Filter by level

```bash
java -javaagent:agent.jar=config.properties \
     -jar app.jar \
     2>&1 | grep -E "TenantWall|Hibernate.*Modified"
```

### Send to stdout (mixing with Spring logs)

```bash
java -javaagent:agent.jar=config.properties \
     -jar app.jar \
     2>&1 | tee app.log
```

> [!NOTE]
> Proper logging integration (`java.util.logging` / SLF4J) is on the roadmap for v1.1.

---

## Validating Configuration

### Check what the agent loaded

After startup, look for these lines in the logs:

```
🛡️ tenantwall Agent Loaded (v4 - Hibernate aware)
[TenantWall] Loaded config file: tenantwall.properties
[TenantWall] Config: enabled=true, column=tenant_id, parser=http://localhost:8000, excluded=[tenants, audit_log]
🛡️ Interceptors installed (v4)
```

### Confirm from the running app

Hit the stats endpoint:

```bash
curl http://localhost:8080/api/admin/smart-stats
```

Response shows live counters:

```json
{
  "pythonCalls": 10,
  "localCalls": 124,
  "fallbacks": 0
}
```

If `fallbacks > 0` and growing, the Python parser is down — check `tenantwall.parser.url`.

---

## Common Configuration Mistakes

| Mistake | Symptom | Fix |
|---|---|---|
| `tenantwall.enabled=false` | No filters applied | Set to `true` |
| Wrong `filter.column` | SQL errors (column not found) | Use the actual column name |
| Missing `parser.dialect` | Parser errors on complex SQL | Set to DB dialect (`mysql`, `tsql`, `oracle`) |
| `parser.url` trailing slash | 404 from Python | Normalized in v4 (no action needed) |
| `onMissing=fail` in dev | Startup fails on seed data | Use `skip` for dev |
| `excludedTables` missing `flyway_schema_history` | Migration queries fail | Add to excluded list |

---

**Next:** [Roadmap →](./11-roadmap.md)