# Getting Started

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 11+ (21 verified) |
| Spring Boot | 3.x (or any framework using JDBC) |
| Hibernate | 6.2+ (for primary interceptor) |
| Docker | For running the parser sidecar |

---

## Quick Start — 5 Minutes

### Step 1: Add the dependency

```xml
<dependency>
    <groupId>com.tenantwall</groupId>
    <artifactId>agent-module</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

> [!IMPORTANT]
> `provided` scope is **critical** — the agent is loaded via `-javaagent`, not bundled in the fat jar.

---

### Step 2: Add the tenant filter (10 lines)

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        String tenant = ((HttpServletRequest) req).getHeader("X-Tenant-Id");
        try {
            TenantContext.setTenant(tenant);
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();  // always clear
        }
    }
}
```

> [!TIP]
> **Why `finally`?** Thread pools reuse threads. If you don't clear the context, a thread from Tenant A might process a request from Tenant B with stale tenant ID.

---

### Step 3: Add the config file

Create `tenantwall.properties` in your project root:

```properties
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.filter.onMissing=skip
tenantwall.parser.url=http://localhost:8000
tenantwall.parser.dialect=mysql
```

---

### Step 4: Start the Python parser (optional but recommended)

```bash
cd tenantwall-parser
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

> [!NOTE]
> The Python parser handles **complex SQL** (CTEs, window functions, recursive subqueries). If it's not running, TenantWall falls back to local parsing.

---

### Step 5: Run your app with the agent

Build the agent first (one-time):

```bash
cd agent-module
mvn clean install -DskipTests
```

Then run your app:

```bash
java -javaagent:$HOME/.m2/repository/com/tenantwall/agent-module/1.0.0-SNAPSHOT/agent-module-1.0.0-SNAPSHOT.jar=tenantwall.properties \
     -jar your-app.jar
```

**Done.** All SQL queries now include `tenant_id = <current>`.

---

## Verifying It Works

### Start the app

```bash
java -javaagent:tenantwall-agent.jar=tenantwall.properties -jar app.jar
```

### Test tenant isolation

```bash
# In another terminal:
curl -H "X-Tenant-Id: ACME" http://localhost:8080/api/products
curl -H "X-Tenant-Id: WIDGETCO" http://localhost:8080/api/products
```

### Expected in agent logs

```
🛡️ tenantwall Agent Loaded (v4 - Hibernate aware)
[TenantWall] Config: enabled=true, column=tenant_id, parser=http://localhost:8000
🛡️ Interceptors installed (v4)
...
🎯 [Hibernate] Modified: SELECT * FROM products WHERE products.tenant_id = 'ACME'
🎯 [Hibernate] Modified: SELECT * FROM products WHERE products.tenant_id = 'WIDGETCO'
```

**Expected in HTTP response:** each tenant sees only their own products.

---

## Full Examples

See the `examples/` folder for complete working projects:

| Example | Database | Tests |
|---|---|---|
| [`examples/mysql-spring-boot/`](../examples/mysql-spring-boot/) | MySQL 8.0 | 30/30 ✅ |
| [`examples/mssql-spring-boot/`](../examples/mssql-spring-boot/) | SQL Server 2022 | 29/29 ✅ |
| [`examples/oracle-spring-boot/`](../examples/oracle-spring-boot/) | Oracle XE 21c | 29/29 ✅ |

Each example includes:

- Complete `pom.xml`
- `docker-compose.yml` for the DB
- `schema.sql` + `data.sql` (seeded for 2 tenants)
- `tenantwall.properties`
- `test-<db>-integration.sh` (29-30 tests)

---

## Running an Example

```bash
# 1) Build the agent (one-time)
cd agent-module
mvn clean install -DskipTests

# 2) Start a database
cd examples/mysql-spring-boot
docker compose up -d

# 3) Build the app
mvn clean package -DskipTests

# 4) Run with agent
java -javaagent:$HOME/.m2/repository/com/tenantwall/agent-module/1.0.0-SNAPSHOT/agent-module-1.0.0-SNAPSHOT.jar=tenantwall.properties \
     -jar target/store-app.jar

# 5) Test (in another terminal)
bash test-mysql-integration.sh
```

**Expected:** `PASS: 30, FAIL: 0, WARN: 0` 🎉

---

## Schema Requirements

Every table that holds tenant-specific data must have:

1. A `tenant_id` column (`VARCHAR(50)` recommended)
2. An index on `tenant_id` (for performance)
3. **NOT** nullable (for data integrity)

Example (MySQL):

```sql
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    INDEX idx_tenant (tenant_id)
);
```

**Tables without `tenant_id`:**

Add them to `tenantwall.filter.excludedTables`:

```properties
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history
```

---

## Spring Boot Configuration

If using Spring Boot, you can also configure via `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/store_demo
    username: root
    password: root
  jpa:
    hibernate:
      ddl-auto: none
```

**TenantWall config is separate** — it's read from `tenantwall.properties` by the agent at JVM startup, before Spring loads.

---

## Common Pitfalls

| Issue | Fix |
|---|---|
| `NoClassDefFoundError: TenantContext` | Missing `-javaagent` flag |
| Queries not filtered | Check `tenantwall.properties` loaded (see startup logs) |
| Cross-tenant 404 not working | Hibernate layer not intercepted — verify Hibernate 6.2+ |
| Python parser 404 | Trailing slash in `tenantwall.parser.url` (normalized in v4) |
| `onMissing=fail` throws in `@PostConstruct` | Set `onMissing=skip` for seed data |
| `Cannot open database` (MSSQL) | Create the DB manually: `CREATE DATABASE store_demo;` |
| Oracle `invalid username/password` | Use `APP_USER` from docker-compose |
| `INSERT` misses `tenant_id` | Table not auto-detected — add to `tenantwall.schema.<table>` |

---

## Configuring the Agent Path

The `-javaagent` path must point to the built jar. Options:

### Option 1: Maven local repository (recommended)

```bash
java -javaagent:$HOME/.m2/repository/com/tenantwall/agent-module/1.0.0-SNAPSHOT/agent-module-1.0.0-SNAPSHOT.jar=tenantwall.properties \
     -jar app.jar
```

### Option 2: Copy the jar to your project

```bash
cp $HOME/.m2/repository/com/tenantwall/agent-module/1.0.0-SNAPSHOT/agent-module-1.0.0-SNAPSHOT.jar ./agent.jar
java -javaagent:./agent.jar=tenantwall.properties -jar app.jar
```

### Option 3: Use `mvn spring-boot:run` with `<agents>`

In `pom.xml`:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <agents>
            <agent>${user.home}/.m2/repository/com/tenantwall/agent-module/1.0.0-SNAPSHOT/agent-module-1.0.0-SNAPSHOT.jar</agent>
        </agents>
        <systemPropertyVariables>
            <tenantwall.config>${project.basedir}/tenantwall.properties</tenantwall.config>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

Then:

```bash
mvn spring-boot:run
```

---

## Next Steps

Once integrated:

1. **Test tenant isolation** — `/api/products` with two different `X-Tenant-Id` headers
2. **Verify logs** — check for `🎯 [Hibernate] Modified:` messages
3. **Run cross-tenant tests** — try to `GET` another tenant's data → should return 404
4. **Configure `onMissing=fail`** for production
5. **Monitor the stats endpoint** — `/api/admin/smart-stats`

---

## Getting Help

- **Documentation**: this folder
- **Issues**: [GitHub Issues](https://github.com/maksoud13/tenantwall/issues)
- **Discussions**: [GitHub Discussions](https://github.com/maksoud13/tenantwall/discussions)

---

**Next:** [Configuration →](./10-configuration.md)