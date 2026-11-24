# Getting Started

> **From zero to multi-tenant in 2 minutes. Zero code. Zero annotations. Zero filters.**

---

## Prerequisites

| Requirement | Version | Notes |
|:---|:---|:---|
| Java | 21+ | Required — the parent `pom.xml` sets `<maven.compiler.release>21</maven.compiler.release>` and the codebase uses Java 21 features (pattern matching, switch expressions). JDK 11 will not build this project. |
| Spring Boot | 3.x | Required for the starter. Plain JDBC works without it |
| Hibernate | 6.2+ | For the primary interceptor (Layer 1) |
| Docker | 20+ | For running the Python parser sidecar |

---

## Quick Start — 2 Minutes

### Step 1: Add the starter dependency

```xml
<dependency>
    <groupId>io.github.maksoud13</groupId>
    <artifactId>tenantwall-spring-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

> [!IMPORTANT]
> The starter does **not** bundle the agent JAR. The agent is loaded via `-javaagent` — the starter only provides the auto-configured filter and config validation.

---

### Step 2: Add the config file

Create `tenantwall.properties` in your project root:

```properties
# ═══ Core ═══
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.filter.onMissing=skip

# ═══ Tenant Resolution (pick ONE strategy) ═══
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id

# ═══ Parser (optional — enables complex SQL support) ═══
tenantwall.parser.url=http://127.0.0.1:8000
tenantwall.parser.dialect=mysql
tenantwall.parser.enforceAuth=true
tenantwall.parser.secret=YOUR_32_CHAR_SECRET_HERE

# ═══ Excluded tables (no tenant_id column) ═══
tenantwall.filter.excludedTables=countries,currencies,payment_methods,roles,permissions,flyway_schema_history
```

---

### Step 3: Start the Python parser (Docker Hub)

```bash
docker run -d --name tenantwall-parser \
  -p 127.0.0.1:8000:8000 \
  -e TENANTWALL_PARSER_SECRET="YOUR_32_CHAR_SECRET_HERE" \
  maksoud13/tenantwall-parser:latest
```

> [!NOTE]
> `TENANTWALL_PARSER_SECRET` must match `tenantwall.parser.secret` in your properties file. Binding to `127.0.0.1` ensures the parser is not exposed to the network.

---

### Step 4: Run your app with the agent

You can download the agent JAR from [Maven Central](https://central.sonatype.com/artifact/io.github.maksoud13/agent-module), or use the one in your local `.m2` folder after running `mvn install` on the agent-module source.

> [!IMPORTANT]
> Use **v1.2.1 or later**. In v1.2.0 the agent JAR's `Premain-Class` manifest entry pointed to a non-existent class (`io.github.maksoud13.agent.tenantwallAgent` instead of the actual `com.tenantwall.agent.tenantwallAgent`), so `java -javaagent:...` would fail silently with `ClassNotFoundException` at JVM startup. Fixed in v1.2.1.

**Linux / macOS:**

```bash
java -javaagent:tenantwall-agent-1.2.0.jar=tenantwall.properties -jar your-app.jar
```

**Windows (Git Bash):**

```bash
java -javaagent:"C:\path\to\tenantwall-agent-1.2.0.jar=tenantwall.properties" -jar your-app.jar
```

That's it. **Zero code. Zero annotations. Zero filters.**
The Spring Boot Starter auto-registers `TenantWallFilter` at `HIGHEST_PRECEDENCE`. All you write is config.

---

## Verifying It Works

### 1. Check the startup log

You should see these three lines in the console (in this order):

```text
[AGENT] tenantwall Agent Loaded (v4 - Hibernate + Argument)
[AGENT] Interceptors installed (v4)
[AGENT] Auto-registering TenantWallFilter (strategy: header)
```

### 2. Test tenant isolation

```bash
# Should return 200 (own tenant)
curl -H "X-Tenant-Id: TENANT_001" http://localhost:8080/api/products/1

# Should return 403 or 404 (cross-tenant blocked)
curl -H "X-Tenant-Id: TENANT_001" http://localhost:8080/api/products/40001
```

### 3. Check agent rewrite logs

Look for this pattern in your app console:

```text
[SQL] [HIBERNATE] Modified (via org.hibernate...)
```

---

## Schema Requirements

Every table that holds tenant-specific data must have:

- A `tenant_id` column (`VARCHAR(50)` recommended)
- An index on `tenant_id` (for performance)
- A `NOT NULL` constraint

Example (MySQL):

```sql
CREATE TABLE products (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(200)   NOT NULL,
    price     DECIMAL(12,2)  NOT NULL,
    tenant_id VARCHAR(50)    NOT NULL,
    INDEX idx_tenant (tenant_id)
);
```

---

## Next Steps

Once integrated:

1. **Test isolation** — call `/api/products` with two different `X-Tenant-Id` headers.
2. **Verify logs** — check for `[SQL] [HIBERNATE]` messages.
3. **Configure `onMissing=fail`** for production (after verifying all code paths set the tenant).
4. **Monitor stats** — `/api/admin/stats` if using `megaexample`.

**Next:** [Configuration →](./10-configuration.md)