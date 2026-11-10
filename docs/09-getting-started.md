# Getting Started

> **From zero to multi-tenant in 2 minutes. Zero code. Zero annotations. Zero filters.**

---

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| **Java** | 11+ (21 verified) | Any JDK that supports `-javaagent` |
| **Spring Boot** | 3.x | Required for the starter. Plain JDBC works without it |
| **Hibernate** | 6.2+ | For the primary interceptor (Layer 1) |
| **Maven** | 3.8+ | For building the agent |
| **Docker** | 20+ | Optional — for the Python parser sidecar |
| **Python** | 3.10+ | Optional — for complex SQL (CTEs, window functions) |

---

## Quick Start — 2 Minutes

### Step 1: Add the starter dependency

```xml
<dependency>
    <groupId>com.tenantwall</groupId>
    <artifactId>tenantwall-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
[!IMPORTANT]
The starter does NOT bundle the agent JAR. The agent is loaded via -javaagent — the starter only provides the auto-configured filter and config validation.

This separation is intentional: it keeps your fat jar small and gives you control over the agent lifecycle.

Step 2: Add the config file
Create tenantwall.properties in your project root:

properties
# ═══ Core ═══
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.filter.onMissing=skip

# ═══ Tenant Resolution (pick ONE strategy) ═══
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id

# ═══ Parser (optional — enables complex SQL support) ═══
tenantwall.parser.url=http://localhost:8000
tenantwall.parser.dialect=mysql

# ═══ Excluded tables (no tenant_id column) ═══
tenantwall.filter.excludedTables=countries,currencies,payment_methods,roles,permissions,flyway_schema_history
Step 3: Build the agent (one-time)
bash
cd tenantwall/agent-module
mvn clean install -DskipTests
This installs agent-module-1.0.0-SNAPSHOT.jar to your local Maven repo (~/.m2/repository/com/tenantwall/agent-module/).

Step 4: Run your app with the agent
On Linux/macOS:

bash
java -javaagent:$HOME/.m2/repository/com/tenantwall/agent-module/1.0.0-SNAPSHOT/agent-module-1.0.0-SNAPSHOT.jar=tenantwall.properties \
     -jar your-app.jar
On Windows (Git Bash):

bash
WIN_HOME="$(cygpath -w "$HOME")"
AGENT_JAR="${WIN_HOME}\.m2\repository\com\tenantwall\agent-module\1.0.0-SNAPSHOT\agent-module-1.0.0-SNAPSHOT.jar"

java -javaagent:"${AGENT_JAR}=tenantwall.properties" \
     -jar your-app.jar
On Windows (PowerShell):

powershell
$AgentJar = "$env:USERPROFILE\.m2\repository\com\tenantwall\agent-module\1.0.0-SNAPSHOT\agent-module-1.0.0-SNAPSHOT.jar"
java -javaagent:"$AgentJar=tenantwall.properties" -jar your-app.jar
Step 5 (optional): Start the Python parser
The Python parser handles complex SQL (CTEs, window functions, recursive subqueries). If it's not running, TenantWall falls back to local JSqlParser.

bash
cd tenantwall/tenantwall-parser
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000
[!TIP]
Bind to 127.0.0.1 — never 0.0.0.0 in production. The parser sees raw SQL (table names, columns) and must not be exposed to the network.

That's it. Zero code. Zero annotations. Zero filters.
The Spring Boot Starter auto-registers TenantWallFilter at HIGHEST_PRECEDENCE. All you write is config.

Verifying It Works
1. Check the startup log
You should see these three lines in the console (in this order):


[AGENT] tenantwall Agent Loaded (v4 - Hibernate + Argument)
[AGENT] Interceptors installed (v4)
[AGENT] Auto-registering TenantWallFilter (strategy: header)
Line	Meaning
Line 1	-javaagent flag is correct, agent is loaded
Line 2	ByteBuddy advices installed on Hibernate/JDBC
Line 3	Spring Boot Starter auto-registered the filter
If line 3 is missing: the starter JAR isn't on the classpath. Check pom.xml.

If all lines are missing: -javaagent flag is wrong, or the JAR path is incorrect.

2. Test tenant isolation
bash
# Should return 200 (own tenant)
curl -H "X-Tenant-Id: TENANT_001" http://localhost:8080/api/products/1

# Should return 403 or 404 (cross-tenant blocked)
curl -H "X-Tenant-Id: TENANT_001" http://localhost:8080/api/products/40001

# Should return 200 (TENANT_002 sees its own data)
curl -H "X-Tenant-Id: TENANT_002" http://localhost:8080/api/products/40001
What you should see:

Request	Expected	If wrong
TENANT_001 → id=1	200	Agent not intercepting
TENANT_001 → id=40001	403/404	DATA LEAK — investigate
TENANT_002 → id=40001	200	Cross-tenant check broken
3. Check agent rewrite logs
bash
# In your app console, you should see:
🎯 [Hibernate] Modified: SELECT ... WHERE products.tenant_id = 'TENANT_001'
If you see this but the DB returns unfiltered data — the rewrite is happening on the wrong layer. See Engineering Journey.

Configuration Checklist
Copy-paste this into your tenantwall.properties and adapt:

properties
# ═══ Required ═══
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id

# ═══ Strongly recommended ═══
tenantwall.parser.url=http://127.0.0.1:8000
tenantwall.parser.dialect=mysql
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history

# ═══ Development vs Production ═══
# Development:
tenantwall.filter.onMissing=skip

# Production:
# tenantwall.filter.onMissing=fail
5 Tenant Strategies — Pick One
The starter supports 5 resolution strategies. Set tenantwall.tenant.strategy to exactly one:

Strategy	Best for	Config
header	APIs, microservices	tenantwall.tenant.headerName=X-Tenant-Id
fixed	Single-tenant tests	tenantwall.tenant.fixedValue=TENANT_001
session	Traditional web apps	tenantwall.tenant.sessionKey=TENANT_ID
cookie	Legacy apps, SPAs	tenantwall.tenant.cookieName=tenant_id
jwt	OAuth2/OIDC APIs	tenantwall.tenant.jwtClaim=tenant_id
[!WARNING]
Set exactly one. If you set multiple sub-config keys (e.g., both headerName and cookieName), TenantWall logs a warning. If you set an invalid strategy, the app fails at startup with a clear error — no silent misconfiguration.

Full strategy documentation: The Solution → Tenant Resolution Strategies.

Verify All 5 Strategies
The repo ships megaexample/test-strategies.sh — a 14-test suite covering all strategies + validation:

bash
cd megaexample
chmod +x test-strategies.sh
./test-strategies.sh
Expected output:


════ 1) strategy=header ════
▶ starting app...
✅ app started (pid=1234)
✅ header: own id=1 (200)
✅ header: cross id=40001 (403)
✅ header: TENANT_002 own id=40001 (200)

════ 2) strategy=fixed ════
✅ fixed: id=1 (forced TENANT_001) (200)
✅ fixed: id=40001 blocked (403)

════ 3) strategy=session ════
✅ session: own id=1 (200)
✅ session: cross id=40001 (403)

════ 4) strategy=cookie ════
✅ cookie: own id=1 (200)
✅ cookie: cross id=40001 (403)

════ 5) strategy=jwt ════
✅ jwt: TENANT_001 → id=1 (200)
✅ jwt: TENANT_001 → cross (403)
✅ jwt: TENANT_002 → id=40001 (200)

════ 6) validation: unknown strategy ════
✅ validation: bogus strategy rejected at startup

════ 7) conflict: extra strategy keys → warning ════
✅ conflict: warning printed for non-selected strategies

════════════════════════════════════════
PASS=14  FAIL=0
🎉 ALL TESTS PASSED
What each test verifies:

Test	Verifies
1-2	Header strategy: own access works, cross blocked both directions
3-4	Fixed strategy: forced tenant, cross blocked
5-6	Session strategy: cookie-based session, isolation
7-8	Cookie strategy: tenant_id cookie, isolation
9-11	JWT strategy: claim extraction, isolation
12	Config validation: bad strategy → startup fail
13	Conflict warning: multiple strategy keys → logged
14	HTTP codes: 200 for own, 403/404 for cross
Run the Full Load Test
For a complete performance + security validation:

bash
cd megaexample

# Sequential load test (5 strategies, 12 minutes)
chmod +x pods/test-strategies-load.sh
./pods/test-strategies-load.sh
What it does:

For each of the 5 strategies:

Starts the app with that strategy

Warms up for 20 seconds

Runs 90 seconds of load (20 VUs, mixed workload)

Measures: latency p50/p95/p99, cross-tenant blocks, leaks

Resets and moves to the next strategy

Expected summary:


strategy      reqs    rps  succ%   own   blk  leak     p50     p95     p99
-------------------------------------------------------------------------
header        3529   39.1  100.00  1594   528     0   96.6   288.6   313.6
fixed         ...    ...   100.00  ...    ...     0   ...     ...     ...
session       ...    ...   100.00  ...    ...     0   ...     ...     ...
cookie        ...    ...   100.00  ...    ...     0   ...     ...     ...
jwt           ...    ...   100.00  ...    ...     0   ...     ...     ...

✅ Zero isolation leaks across all 5 strategies
Success criteria:

succ% = 100.00 across all strategies

leak = 0 across all strategies

blk > 0 (cross-tenant blocks actually happened)

Full Examples
See the examples/ folder for complete working projects:

Example	Database	Tests	Path
MySQL	8.0	30/30 ✅	examples/mysql-spring-boot/
SQL Server	2022	29/29 ✅	examples/mssql-spring-boot/
Oracle	XE 21c	29/29 ✅	examples/oracle-spring-boot/
Plus the megaexample load test project:

megaexample/ — 1.75M rows, 20 tenants, 5 strategies, k6 load test

Running an Example (Full Workflow)
bash
# 1) Build the agent (one-time)
cd agent-module
mvn clean install -DskipTests

# 2) Build the starter (one-time)
cd ../tenantwall-spring-boot-starter
mvn clean install -DskipTests

# 3) Start the database
cd ../examples/mysql-spring-boot
docker compose up -d

# 4) Build the demo app
mvn clean package -DskipTests

# 5) Run with agent
WIN_HOME="$(cygpath -w "$HOME")"
AGENT_JAR="${WIN_HOME}\.m2\repository\com\tenantwall\agent-module\1.0.0-SNAPSHOT\agent-module-1.0.0-SNAPSHOT.jar"
java -javaagent:"${AGENT_JAR}=tenantwall.properties" -jar target/store-app.jar &

# 6) Wait for startup
sleep 15

# 7) Test isolation
bash test-mysql-integration.sh
Expected: PASS: 30, FAIL: 0, WARN: 0 🎉

Schema Requirements
Every table that holds tenant-specific data must have:

Requirement	Why
A tenant_id column	The column TenantWall filters on
An index on tenant_id	Performance — filtered queries use it
NOT NULL constraint	Data integrity — no orphan rows
Correct type	VARCHAR(50) recommended (matches tenant ID format)
Example (MySQL)
sql
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    INDEX idx_tenant (tenant_id)
);
Example (SQL Server)
sql
CREATE TABLE products (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(200) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    tenant_id NVARCHAR(50) NOT NULL,
    INDEX idx_tenant (tenant_id)
);
Example (Oracle)
sql
CREATE TABLE products (
    id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR2(200) NOT NULL,
    price NUMBER(12,2) NOT NULL,
    tenant_id VARCHAR2(50) NOT NULL
);
CREATE INDEX idx_tenant ON products(tenant_id);
Tables Without tenant_id
Some tables don't hold tenant-specific data — metadata, lookups, system tables. Add them to excludedTables:

properties
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history,countries,currencies,payment_methods,shipping_carriers,product_attributes,roles,permissions
Typical excluded tables:

Table	Why exclude
tenants	Contains tenant metadata, not tenant data
audit_log	Cross-tenant audit trail
flyway_schema_history	Migration tracking
countries, currencies	Global lookups
roles, permissions	Global RBAC
[!WARNING]
Don't over-exclude. Every table in excludedTables is not filtered — a leak risk if it actually holds tenant data. Review this list carefully.

Spring Boot Configuration
The agent config (tenantwall.properties) is separate from Spring config (application.yml). The agent reads tenantwall.properties at JVM startup, before Spring initializes.

application.yml (Spring / database)
yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/store_demo
    username: root
    password: root
  jpa:
    hibernate:
      ddl-auto: none
    open-in-view: false
tenantwall.properties (agent)
properties
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
Two separate files. This is intentional — the agent must be able to read its config even if Spring fails to start.

Common Pitfalls
#	Issue	Fix
1	NoClassDefFoundError: TenantContext	Missing -javaagent flag
2	Queries not filtered	Check tenantwall.properties loaded (see startup logs)
3	App fails with IllegalStateException	Invalid tenantwall.tenant.strategy — see error message
4	Cross-tenant 404 not working	Hibernate layer not intercepted — verify Hibernate 6.2+
5	Python parser 404	Trailing slash in tenantwall.parser.url (normalized in v4)
6	onMissing=fail throws in @PostConstruct	Set onMissing=skip for seed data
7	INSERT misses tenant_id	Table not auto-detected — add tenantwall.schema.<table>
8	Cannot open database (MSSQL)	Create the DB manually: CREATE DATABASE store_demo;
9	Oracle invalid username/password	Use APP_USER from docker-compose
10	Emoji garbled in Windows console	chcp 65001 before running
Detailed fixes for common errors
1. onMissing=fail breaks @PostConstruct
Symptom:


SecurityException: No tenant in context
    at com.tenantwall.agent...
Cause: @PostConstruct runs before any HTTP request — no tenant header available.

Fix 1 — Use skip during development:

properties
tenantwall.filter.onMissing=skip
Fix 2 — Set tenant manually in @PostConstruct:

java
@PostConstruct
public void init() {
    TenantContext.setTenant("SEED");
    try {
        // seed data
    } finally {
        TenantContext.clear();
    }
}
Fix 3 — Use Flyway for seed data (bypasses the agent via excludedTables).

2. INSERT misses tenant_id
Symptom:

sql
INSERT INTO products VALUES ('Laptop', 999);
-- Missing tenant_id → DB constraint error
Cause: INSERT INTO t VALUES (...) has no column list — TenantWall can't know where tenant_id goes.

Fix 1 — Use explicit column list (recommended):

sql
INSERT INTO products (name, price) VALUES (?, ?);
-- TenantWall injects: (name, price, tenant_id) VALUES (?, ?, 'TENANT_001')
Fix 2 — Use schema hint:

properties
tenantwall.schema.products=id,name,price,tenant_id
Now TenantWall knows tenant_id is the 4th column.

3. Cross-tenant GET returns 200 instead of 404
Symptom:

bash
curl -H "X-Tenant-Id: TENANT_001" http://localhost:8080/api/products/40001
# Expected: 403/404
# Got: 200 (DATA LEAK)
Diagnosis steps:

Check startup logs for [AGENT] Interceptors installed (v4)

Check for 🎯 [Hibernate] Modified: ... in query logs

Enable DB general log — verify WHERE tenant_id reaches the database

Check tenantwall.enabled=true

Check tenantwall.filter.column matches actual DB column

Most common cause: Hibernate < 6.2 — Layer 1 interceptor not active.

4. Python parser down, complex queries fail
Symptom:


[TenantWall] Python parser request failed: Connection refused
[TenantWall] Fallback to local parser
Fix 1 — Start the parser:

bash
cd tenantwall-parser
uvicorn app.main:app --host 127.0.0.1 --port 8000
Fix 2 — Monitor fallback counter:

bash
curl http://localhost:8080/api/admin/smart-stats
# {"fallbacks": N, ...}
If fallbacks grows, the parser is down. Local parser handles simple SQL but may fail on CTEs.

Configuration Best Practices
Development
properties
tenantwall.enabled=true
tenantwall.filter.onMissing=skip          # ← allow seed data
tenantwall.filter.column=tenant_id
tenantwall.tenant.strategy=header
tenantwall.parser.url=http://127.0.0.1:8000
Staging (pre-production)
properties
tenantwall.enabled=true
tenantwall.filter.onMissing=skip          # ← catch bugs before prod
tenantwall.filter.column=tenant_id
tenantwall.tenant.strategy=header
tenantwall.parser.url=http://parser.staging:8000
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history
Production
properties
tenantwall.enabled=true
tenantwall.filter.onMissing=fail          # ← fail-closed
tenantwall.filter.column=tenant_id
tenantwall.tenant.strategy=header
tenantwall.parser.url=http://parser.internal:8000
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history
tenantwall.insert.strategy=fail
Before going to production:

Verify all code paths set the tenant context

Run test-strategies.sh — 14/14 pass

Run load test — 0 leaks across 5 strategies

Check startup log for all 3 agent lines

Cross-tenant requests return 404 in staging

Next Steps
Once integrated:

#	Task	Command
1	Test isolation	curl -H "X-Tenant-Id: ..." /api/products
2	Verify logs	Look for 🎯 [Hibernate] Modified:
3	Run all strategies	./test-strategies.sh
4	Load test	./pods/test-strategies-load.sh
5	Enable onMissing=fail	Update tenantwall.properties
6	Monitor stats	curl /api/admin/smart-stats
Getting Help
Channel	Use for
Documentation	This docs/ folder
GitHub Issues	Bug reports, feature requests
GitHub Discussions	Questions, show-and-tell
Security disclosures	security@tenantwall.io (private)
Related Docs
The Solution → — 5 strategies explained in depth

Configuration → — all config keys

Security Model → — what's guaranteed

Test Results → — 102/102 tests passing

Roadmap → — what's next

FAQ → — common questions
---

**Next:** [Configuration →](./10-configuration.md)