# Configuration Reference

> **Complete reference for every TenantWall configuration key, source, and strategy.**

---

## Quick Reference

TenantWall config lives in **`tenantwall.properties`** (separate from Spring's `application.yml`). The agent reads it at **JVM startup** — before Spring initializes.

**Minimal working config:**

```properties
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
Production config:

properties
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.filter.onMissing=fail
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
tenantwall.parser.url=http://127.0.0.1:8000
tenantwall.parser.dialect=mysql
tenantwall.insert.strategy=fail
All Configuration Keys
Core
Key	Type	Default	Description
tenantwall.enabled	boolean	true	Master switch
tenantwall.filter.column	string	tenant_id	Column name to filter on
tenantwall.filter.defaultValue	string	null	Fallback if no tenant in context (single-tenant mode)
tenantwall.filter.onMissing	enum	skip	skip (dev) or fail (prod)
tenantwall.filter.excludedTables	CSV	(empty)	Tables to never filter (e.g., tenants,audit_log)
Tenant Resolution (v1.1)
Key	Type	Default	Description
tenantwall.tenant.strategy	enum	header	header, fixed, session, cookie, or jwt
tenantwall.tenant.headerName	string	X-Tenant-Id	HTTP header name (header strategy)
tenantwall.tenant.fixedValue	string	null	Hard-coded tenant (fixed strategy)
tenantwall.tenant.sessionKey	string	TENANT_ID	Session attribute key (session strategy)
tenantwall.tenant.cookieName	string	tenant_id	Cookie name (cookie strategy)
tenantwall.tenant.jwtClaim	string	tenant_id	JWT claim path (jwt strategy)
tenantwall.tenant.jwtHeader	string	Authorization	JWT header name (jwt strategy)
tenantwall.tenant.jwtPrefix	string	Bearer	JWT header prefix (jwt strategy)
Parser
Key	Type	Default	Description
tenantwall.parser.url	string	null	Python parser URL (null = disabled, local-only)
tenantwall.parser.dialect	string	mysql	DB dialect (mysql, tsql, oracle, postgres, ...)
INSERT Handling
Key	Type	Default	Description
tenantwall.insert.strategy	enum	skip	skip, append, or fail for INSERT without column list
tenantwall.schema.<table>	CSV	(empty)	Explicit column list for INSERT hints
Configuration Sources (Priority Order)
The agent merges config from 6 sources. Later sources override earlier ones:

Priority	Source	Example
1 (lowest)	Classpath	src/main/resources/tenantwall.properties
2	Environment variables	TENANTWALL_FILTER_COLUMN=org_id
3	System properties	-Dtenantwall.filter.column=org_id
4	Config file (from -Dtenantwall.config)	-Dtenantwall.config=/etc/tw.properties
5	Agent args	-javaagent:agent.jar=/etc/tw.properties
6 (highest)	Runtime overrides	TenantwallConfig.setFilterColumn("org_id")
Practical guidance:

Production: use agent args (-javaagent:agent.jar=/etc/tenantwall.properties) for explicit control

Docker/K8s: use environment variables (portable across images)

Local dev: use classpath tenantwall.properties (auto-loaded)

Tenant Resolution Strategies (v1.1)
This is the most important configuration section. Pick exactly one strategy.

The tenantwall.tenant.strategy Key
Value	Reads tenant from	Best for
header	HTTP header	APIs, microservices
fixed	Hard-coded config value	Single-tenant, tests
session	HttpSession attribute	Traditional web apps
cookie	HTTP cookie	Legacy apps, SPAs
jwt	JWT claim	OAuth2/OIDC APIs
Default: header (if not set)

Strategy 1: header
Reads the tenant from an HTTP header.

properties
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
Key	Default	Description
tenantwall.tenant.headerName	X-Tenant-Id	The header to read
Example request:

bash
curl -H "X-Tenant-Id: TENANT_001" http://app/api/products
What happens:

Filter reads X-Tenant-Id: TENANT_001

Sets TenantContext.setTenant("TENANT_001")

All SQL queries get WHERE tenant_id = 'TENANT_001'

Context cleared in finally block

⚠️ Security: The header is client-controlled. Always validate the tenant against your auth layer (JWT, session) before trusting it.

Common header names:

Use case	Header name
Generic	X-Tenant-Id
Organization	X-Org-Id
Workspace	X-Workspace-Id
Account	X-Account-Id
Standard (RFC 6648)	Tenant-Id (no X- prefix)
Strategy 2: fixed
Forces every request to a single hard-coded tenant. No header, no cookie, no session.

properties
tenantwall.tenant.strategy=fixed
tenantwall.tenant.fixedValue=TENANT_001
Key	Default	Description
tenantwall.tenant.fixedValue	null	Required — the tenant value
When to use:

Single-tenant deployment

Integration tests (no need to send headers)

Demos

Legacy app being gradually migrated

⚠️ Security: All requests are forced to one tenant. Multi-tenancy is disabled. Use only when there's exactly one tenant per JVM.

Strategy 3: session
Reads the tenant from HttpSession.

properties
tenantwall.tenant.strategy=session
tenantwall.tenant.sessionKey=TENANT_ID
Key	Default	Description
tenantwall.tenant.sessionKey	TENANT_ID	The session attribute name
How to set the session (your login code):

java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpSession session) {
    User user = authService.authenticate(req);
    session.setAttribute("TENANT_ID", user.getTenantId());
    return ResponseEntity.ok(user);
}
How TenantWall reads it:

java
// Inside TenantWallFilter
HttpSession session = request.getSession(false);
String tenant = (session != null) ? (String) session.getAttribute("TENANT_ID") : null;
When to use:

Traditional Spring MVC apps with server-side sessions

Login-based authentication

No JWT infra

⚠️ Security: Session cookies must be HttpOnly, Secure, SameSite=Strict (Spring defaults are correct).

Strategy 4: cookie
Reads the tenant from an HTTP cookie.

properties
tenantwall.tenant.strategy=cookie
tenantwall.tenant.cookieName=tenant_id
Key	Default	Description
tenantwall.tenant.cookieName	tenant_id	The cookie to read
Example request:

bash
curl --cookie "tenant_id=TENANT_001" http://app/api/products
When to use:

Legacy apps that already set tenant cookies

SPA frontends that manage tenant state client-side

Stateless setups where session isn't desired

⚠️ Security: Cookies are client-controlled. Set HttpOnly; Secure; SameSite=Strict and validate server-side. Prefer jwt if you have auth infra.

Strategy 5: jwt
Reads the tenant from a JWT claim in the Authorization header.

properties
tenantwall.tenant.strategy=jwt
tenantwall.tenant.jwtClaim=tenant_id
tenantwall.tenant.jwtHeader=Authorization
tenantwall.tenant.jwtPrefix=Bearer 
Key	Default	Description
tenantwall.tenant.jwtClaim	tenant_id	The claim path inside the JWT payload
tenantwall.tenant.jwtHeader	Authorization	The HTTP header carrying the token
tenantwall.tenant.jwtPrefix	Bearer	The prefix to strip before decoding
Example JWT payload:

json
{
  "sub": "user-123",
  "tenant_id": "TENANT_001",
  "roles": ["USER"],
  "exp": 1731100060
}
Example request:

bash
curl -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." http://app/api/products
[!WARNING]
Decode-only — no signature verification.

TenantWall decodes the JWT payload but does not verify the signature.

Signature verification = your responsibility (Spring Security, API gateway)

Tenant extraction = TenantWall's responsibility

This separation is intentional: TenantWall works with any auth system. Always verify JWTs upstream before they reach your application.

When to use:

Modern APIs with OAuth2 / OIDC

Spring Security already configured

API gateway performs signature verification

Nested claims: If the tenant claim is nested, use dot notation:

properties
# JWT: {"user": {"org": {"id": "TENANT_001"}}}
tenantwall.tenant.jwtClaim=user.org.id
Strategy Validation & Safety
TenantWall validates the strategy at startup — fail-fast, no silent misconfiguration.

Bad strategy → app fails to start
properties
tenantwall.tenant.strategy=bogus
Error at startup:


java.lang.IllegalStateException: [TenantWall] Invalid tenantwall.tenant.strategy='bogus'.
Must be one of: header, fixed, session, cookie, jwt
Missing sub-config → app fails to start
properties
tenantwall.tenant.strategy=fixed
# ⚠️ missing tenantwall.tenant.fixedValue
Error at startup:


java.lang.IllegalStateException: [TenantWall] strategy=fixed requires tenantwall.tenant.fixedValue
Required sub-config per strategy:

Strategy	Required key
header	tenantwall.tenant.headerName (default X-Tenant-Id is valid)
fixed	tenantwall.tenant.fixedValue
session	tenantwall.tenant.sessionKey (default TENANT_ID is valid)
cookie	tenantwall.tenant.cookieName (default tenant_id is valid)
jwt	tenantwall.tenant.jwtClaim (default tenant_id is valid)
Multiple strategies set → warning (not fatal)
properties
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
tenantwall.tenant.cookieName=tenant_id    # ← ignored
tenantwall.tenant.sessionKey=TENANT_ID    # ← ignored
Warning at startup:


[TenantWall] WARNING: strategy=header but tenantwall.tenant.cookieName is also set (ignored). Pick only one strategy.
[TenantWall] WARNING: strategy=header but tenantwall.tenant.sessionKey is also set (ignored). Pick only one strategy.
Behavior: The selected strategy (header) is used. Other keys are ignored — but the warning tells you to clean up config.

Strategy Selection Guide

Are you building a REST API?
│
├── YES → Do you use JWT (OAuth2 / OIDC / Keycloak)?
│         │
│         ├── YES → strategy=jwt
│         │         tenantwall.tenant.jwtClaim=tenant_id
│         │
│         └── NO  → strategy=header
│                   tenantwall.tenant.headerName=X-Tenant-Id
│
└── NO (traditional web app)
          │
          ├── Do you use server-side sessions?
          │   │
          │   ├── YES → strategy=session
          │   │         tenantwall.tenant.sessionKey=TENANT_ID
          │   │
          │   └── NO  → strategy=cookie
          │             tenantwall.tenant.cookieName=tenant_id
          │
          └── Single-tenant test/dev → strategy=fixed
                                       tenantwall.tenant.fixedValue=TENANT_001
Default recommendation: header for APIs, session for web apps, jwt if you already have JWT infra.

Example Configuration Files
Minimal (development)
properties
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.filter.onMissing=skip
tenantwall.tenant.strategy=header
Production (header strategy)
properties
# ═══ Core ═══
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.filter.onMissing=fail
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history

# ═══ Tenant Resolution ═══
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id

# ═══ Parser ═══
tenantwall.parser.url=http://127.0.0.1:8000
tenantwall.parser.dialect=mysql

# ═══ INSERT ═══
tenantwall.insert.strategy=fail
Production (JWT strategy)
properties
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.filter.onMissing=fail
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history

tenantwall.tenant.strategy=jwt
tenantwall.tenant.jwtClaim=tenant_id
tenantwall.tenant.jwtHeader=Authorization
tenantwall.tenant.jwtPrefix=Bearer 

tenantwall.parser.url=http://127.0.0.1:8000
tenantwall.parser.dialect=postgres
Production (multi-tenant with schema hints)
properties
tenantwall.enabled=true
tenantwall.filter.column=org_id
tenantwall.filter.onMissing=fail
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history,countries,currencies

tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Org-Id

tenantwall.parser.url=http://127.0.0.1:8000
tenantwall.parser.dialect=tsql

tenantwall.insert.strategy=skip

# Schema hints for INSERT without column list
tenantwall.schema.orders=id,customer_id,product_name,total,org_id
tenantwall.schema.customers=id,name,org_id
Full (all options)
properties
# ═══ Core ═══
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.filter.defaultValue=
tenantwall.filter.onMissing=skip
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history,countries,currencies,payment_methods,roles,permissions

# ═══ Tenant Resolution ═══
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
# tenantwall.tenant.fixedValue=TENANT_001
# tenantwall.tenant.sessionKey=TENANT_ID
# tenantwall.tenant.cookieName=tenant_id
# tenantwall.tenant.jwtClaim=tenant_id
# tenantwall.tenant.jwtHeader=Authorization
# tenantwall.tenant.jwtPrefix=Bearer 

# ═══ Parser ═══
tenantwall.parser.url=http://127.0.0.1:8000
tenantwall.parser.dialect=mysql

# ═══ INSERT ═══
tenantwall.insert.strategy=skip

# ═══ Schema hints ═══
tenantwall.schema.orders=id,customer_id,product_name,total,tenant_id
tenantwall.schema.customers=id,name,tenant_id
The onMissing Decision
This is the single most important production config choice.

skip (development / staging)
properties
tenantwall.filter.onMissing=skip
Behavior:

No tenant in context → execute original SQL unchanged

Logs a warning

Application continues normally

Use when:

@PostConstruct seed data runs before any HTTP request

Scheduled jobs that span all tenants

Migration scripts

Development environment

Risk: ⚠️ Silent data leak if a request has no tenant context.

fail (production)
properties
tenantwall.filter.onMissing=fail
Behavior:

No tenant in context → throw SecurityException

Request fails with HTTP 500

Query never executes

Use when:

Every query must have tenant context

You want to catch bugs before they leak data

Compliance requires fail-closed behavior

Production

Trade-off: Startup/seed data will crash unless you set the tenant manually:

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
Migration path

Development → skip
     ↓
Staging → skip (catch bugs)
     ↓
Production → fail (after verifying all paths)
[!IMPORTANT]
Never go to production with skip. If a code path forgets to set the tenant, it will silently leak data. fail makes it loud.

The insert.strategy Decision
Controls INSERT INTO t VALUES (...) (without column list).

Value	Behavior
skip	Default. Skip injection, log error
append	Append tenant_id as last column (⚠️ only correct if tenant_id is last)
fail	Throw exception
Recommendation: Use skip (default) + explicit column lists in your code. If you can't change the code, use tenantwall.schema.<table> instead.

Schema hint example
properties
tenantwall.schema.orders=id,customer_id,product_name,total,tenant_id
Now:

sql
INSERT INTO orders VALUES (?, ?, ?, ?)
Becomes:

sql
INSERT INTO orders VALUES (?, ?, ?, ?, 'SHOP_ALPHA')
(The position of tenant_id is determined by the schema hint.)

The excludedTables Decision
Tables without tenant_id must be excluded — otherwise queries fail with "column not found."

properties
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history,countries,currencies
Typical excluded tables:

Table	Why
tenants	Metadata (not per-tenant data)
audit_log	Cross-tenant audit trail
flyway_schema_history	Migration tracking
countries, currencies	Global lookups
roles, permissions	Global RBAC
payment_methods, shipping_carriers	Shared config
[!WARNING]
Don't over-exclude. Every table in excludedTables is not filtered — a leak risk if it actually holds tenant data. Review this list carefully.

Verification:

sql
-- Every non-excluded table MUST have a tenant_id column
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'your_db'
  AND COLUMN_NAME = 'tenant_id';
Compare against your exclusion list.

Per-Database Configuration
MySQL
properties
tenantwall.parser.dialect=mysql
yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/store_demo?useSSL=false&serverTimezone=UTC
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
SQL Server
properties
tenantwall.parser.dialect=tsql
yaml
spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=store_demo;encrypt=false;trustServerCertificate=true
    username: sa
    password: "TenantWall123!"
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
[!IMPORTANT]
encrypt=false;trustServerCertificate=true is required for SQL Server 2022 (TLS is enabled by default).

Oracle
properties
tenantwall.parser.dialect=oracle
yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@//localhost:1521/XEPDB1
    username: store_demo
    password: "TenantWall123!"
    driver-class-name: oracle.jdbc.OracleDriver
[!NOTE]
XEPDB1 is the default PDB name in Oracle XE. store_demo is the user and schema name.

PostgreSQL (planned — v2.0)
properties
tenantwall.parser.dialect=postgres
yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/store_demo
    username: postgres
    password: postgres
The parser supports postgres dialect. Full integration tests are on the roadmap for v2.0.

Environment Variable Mapping
Environment variables map to config keys by:

Lowercase

Replace _ with .

Prefix with tenantwall.

Environment Variable	Config Key
TENANTWALL_ENABLED	tenantwall.enabled
TENANTWALL_FILTER_COLUMN	tenantwall.filter.column
TENANTWALL_FILTER_ONMISSING	tenantwall.filter.onMissing
TENANTWALL_TENANT_STRATEGY	tenantwall.tenant.strategy
TENANTWALL_TENANT_HEADERNAME	tenantwall.tenant.headerName
TENANTWALL_TENANT_JWTCLAIM	tenantwall.tenant.jwtClaim
TENANTWALL_PARSER_URL	tenantwall.parser.url
TENANTWALL_PARSER_DIALECT	tenantwall.parser.dialect
Docker Compose Example
yaml
services:
  app:
    image: my-app:latest
    environment:
      TENANTWALL_FILTER_COLUMN: "org_id"
      TENANTWALL_TENANT_STRATEGY: "jwt"
      TENANTWALL_TENANT_JWTCLAIM: "org_id"
      TENANTWALL_PARSER_URL: "http://parser:8000"
      TENANTWALL_PARSER_DIALECT: "tsql"
      TENANTWALL_FILTER_ONMISSING: "fail"
Kubernetes ConfigMap
yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: tenantwall-config
data:
  TENANTWALL_FILTER_COLUMN: "org_id"
  TENANTWALL_TENANT_STRATEGY: "header"
  TENANTWALL_TENANT_HEADERNAME: "X-Org-Id"
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
Runtime Overrides
Config can be changed programmatically (useful for tests, blue/green deploys):

java
// Before Spring starts
TenantwallConfig.setFilterColumn("org_id");
TenantwallConfig.setParserUrl("http://parser.internal:8000");
TenantwallConfig.setOnMissing(TenantwallConfig.MissingValueStrategy.FAIL);
TenantwallConfig.setExcludedTables(List.of("tenants", "audit_log"));
[!NOTE]
setFilterColumn() and setExcludedTables() clear the query cache automatically — safe to call at runtime.

setTenantStrategy() and related keys are validated on initialize() only. If you need to change strategy at runtime, restart the JVM.

Cache Configuration
The Caffeine cache is not user-configurable in v1.1. Fixed values:

Property	Value
Max size	10,000 entries
TTL	30 minutes
Eviction	Window-TinyLFU
Key	{sql}|{column}|{value}
Invalidation triggers:

setFilterColumn() — column changed

setParserUrl() — Python parser URL changed

resetStats() — manual reset

TTL expiry (30 min)

Configurable cache tuning is on the roadmap for v1.2.

Logging Configuration
Agent logs currently use System.err (v1.1). SLF4J integration is on the roadmap for v1.2.

Redirect to a file
bash
java -javaagent:agent.jar=config.properties \
     -jar app.jar \
     2> agent.log
Filter by level
bash
java -javaagent:agent.jar=config.properties \
     -jar app.jar \
     2>&1 | grep -E "TenantWall|Hibernate.*Modified"
Send to stdout (mixing with Spring logs)
bash
java -javaagent:agent.jar=config.properties \
     -jar app.jar \
     2>&1 | tee app.log
[!NOTE]
SLF4J integration is in Tier 1 of the Roadmap. After that, agent logs will respect your logback.xml / log4j2.xml config.

Validating Configuration
Check what the agent loaded (startup log)
Look for these lines:


[TenantWall] Loaded config file: tenantwall.properties
[TenantWall] Config: enabled=true, column=tenant_id, parser=http://127.0.0.1:8000, excluded=[tenants, audit_log]
[AGENT] Auto-registering TenantWallFilter (strategy: header)
If you see strategy: header in the third line, the strategy was validated successfully.

If you see an IllegalStateException, the config is invalid — read the error message.

Live stats endpoint
bash
curl http://localhost:8080/api/admin/smart-stats
Response:

json
{
  "pythonCalls": 10,
  "localCalls": 124,
  "fallbacks": 0,
  "cache.hits": 77,
  "cache.misses": 520,
  "cache.hitRate": "12.90%"
}
If fallbacks > 0 and growing, the Python parser is down — check tenantwall.parser.url.

Common Configuration Mistakes
#	Mistake	Symptom	Fix
1	tenantwall.enabled=false	No filters applied	Set to true
2	Wrong filter.column	SQL errors (column not found)	Use the actual DB column name
3	Missing parser.dialect	Parser errors on complex SQL	Set to DB dialect (mysql, tsql, oracle, postgres)
4	parser.url trailing slash	404 from Python	Normalized in v4 (no action needed)
5	onMissing=fail in dev	Startup fails on seed data	Use skip for dev
6	excludedTables missing flyway_schema_history	Migration queries fail	Add to excluded list
7	Wrong strategy value	IllegalStateException at startup	Use header, fixed, session, cookie, or jwt
8	Multiple strategy keys set	Warning logs, unexpected behavior	Pick one strategy, remove others
9	strategy=fixed without fixedValue	IllegalStateException at startup	Add tenantwall.tenant.fixedValue
10	strategy=jwt but no auth layer verifying signatures	Security vulnerability	Verify JWT upstream (gateway/Spring Security)
11	Header strategy without server-side validation	IDOR vulnerability	Validate X-Tenant-Id against authenticated user
12	excludedTables too aggressive	Tables not filtered (data leak)	Review exclusion list carefully
13	insert.strategy=append with non-last tenant_id	Wrong column injected	Use skip + explicit column lists
14	Running agent JAR without -javaagent flag	NoClassDefFoundError	Add -javaagent:agent.jar=config.properties
Configuration Anti-Patterns
❌ Anti-pattern 1: Trusting the header without auth
java
// ❌ WRONG — any client can spoof the tenant
String tenant = request.getHeader("X-Tenant-Id");
TenantContext.setTenant(tenant);
java
// ✅ CORRECT — validate against authenticated user
User user = securityContext.getAuthenticatedUser();
String headerTenant = request.getHeader("X-Tenant-Id");
if (!user.getTenants().contains(headerTenant)) {
    throw new AccessDeniedException("Tenant not allowed");
}
TenantContext.setTenant(headerTenant);
❌ Anti-pattern 2: onMissing=skip in production
properties
# ❌ WRONG — silent data leak risk
tenantwall.filter.onMissing=skip
properties
# ✅ CORRECT — fail loud
tenantwall.filter.onMissing=fail
❌ Anti-pattern 3: Excluding too many tables
properties
# ❌ WRONG — orders/products have tenant_id but aren't filtered
tenantwall.filter.excludedTables=orders,products,tenants,audit_log
properties
# ✅ CORRECT — only exclude metadata/lookup tables
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history,countries,currencies
❌ Anti-pattern 4: Multiple strategies in config
properties
# ❌ WRONG — conflict warning, only one is used
tenantwall.tenant.strategy=header
tenantwall.tenant.cookieName=tenant_id
tenantwall.tenant.sessionKey=TENANT_ID
properties
# ✅ CORRECT — pick one
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
Config Reference Summary
Category	Key prefix	Count
Core filter	tenantwall.filter.*	5
Tenant resolution	tenantwall.tenant.*	8
Parser	tenantwall.parser.*	2
INSERT	tenantwall.insert.*	1
Schema hints	tenantwall.schema.*	variable
Master	tenantwall.enabled	1
Total		17+
---

**Next:** [Roadmap →](./11-roadmap.md)