# The Solution: Zero-Code Interception

> **TenantWall rewrites SQL between Hibernate and JDBC. Your application code never sees the difference.**

---

## Before / After

**Application (UNCHANGED):**

```java
public List<Product> listProducts() {
    return productRepository.findAll();
}
Hibernate emits:

sql
SELECT p.id, p.name, p.price FROM products p;
TenantWall rewrites (before execution):

sql
SELECT p.id, p.name, p.price
FROM products p
WHERE p.tenant_id = 'SHOP_ALPHA';
The rewrite is automatic, universal, and invisible to the application.

Three-Layer Interception

┌─────────────────────────────────────────────────┐
│  Application (Spring Boot, Quarkus, plain Java) │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│  ORM Layer (Hibernate / JPA)                    │
│    ↳ StatementPreparerImpl.prepareStatement()   │ ← Layer 1
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│  JDBC Layer                                     │
│    ↳ java.sql.Statement.executeQuery()          │ ← Layer 2
│    ↳ java.sql.Connection.prepareStatement()     │ ← Layer 3
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│  MySQL / SQL Server / Oracle                    │
└─────────────────────────────────────────────────┘
Layer	Intercepts	Purpose
Layer 1	Hibernate prepareQueryStatement	Primary — captures Hibernate-generated SQL before HikariCP wraps it
Layer 2	JDBC Statement.executeQuery	Handles plain Statement from JdbcTemplate
Layer 3	JDBC Connection.prepareStatement	Fallback for direct prepareStatement calls
Tenant Resolution Strategies (v1.1)
TenantWall supports 5 strategies for resolving the current tenant. Pick exactly one via config:

properties
tenantwall.tenant.strategy=header   # or: fixed, session, cookie, jwt
Strategy	Reads tenant from	Best for
header	X-Tenant-Id HTTP header (name configurable)	API gateways, microservices
fixed	A single hard-coded value	Single-tenant deployment, tests
session	HttpSession attribute	Traditional web apps with login
cookie	Cookie value (tenant_id by default)	Legacy apps, SPA frontends
jwt	JWT claim in Authorization: Bearer ...	Modern APIs with auth servers
Strategy Config Reference
properties
# ═══ header (most common) ═══
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id

# ═══ fixed ═══
tenantwall.tenant.strategy=fixed
tenantwall.tenant.fixedValue=TENANT_001

# ═══ session ═══
tenantwall.tenant.strategy=session
tenantwall.tenant.sessionKey=TENANT_ID

# ═══ cookie ═══
tenantwall.tenant.strategy=cookie
tenantwall.tenant.cookieName=tenant_id

# ═══ jwt (decode-only — signature verify upstream) ═══
tenantwall.tenant.strategy=jwt
tenantwall.tenant.jwtClaim=tenant_id
tenantwall.tenant.jwtHeader=Authorization
tenantwall.tenant.jwtPrefix=Bearer 
How Each Strategy Resolves the Tenant
1. header — HTTP Header
java
// TenantWallFilter
String tenant = request.getHeader("X-Tenant-Id");
bash
curl -H "X-Tenant-Id: TENANT_001" http://app/api/products
Best for: Microservices, REST APIs, API gateways that inject tenant header.

⚠️ Security: Header is client-controlled. Always validate against your auth layer (JWT, session, etc.) before trusting it.

2. fixed — Hard-coded Value
java
// TenantWallFilter
String tenant = "TENANT_001";  // from config
properties
tenantwall.tenant.fixedValue=TENANT_001
Best for: Single-tenant deployments, integration tests, demos.

⚠️ Security: Every request is forced to the same tenant. Cross-tenant access is impossible — but so is multi-tenancy. Use only when there's exactly one tenant per JVM.

3. session — HttpSession Attribute
java
// TenantWallFilter
HttpSession session = request.getSession(false);
String tenant = (String) session.getAttribute("TENANT_ID");
java
// Your login endpoint
session.setAttribute("TENANT_ID", user.getTenantId());
Best for: Traditional web apps with form login and server-side sessions.

⚠️ Security: Session cookie is HTTPOnly by default (Spring). Set tenant on login, clear on logout.

4. cookie — Cookie Value
java
// TenantWallFilter
for (Cookie cookie : request.getCookies()) {
    if ("tenant_id".equals(cookie.getName())) {
        return cookie.getValue();
    }
}
bash
curl --cookie "tenant_id=TENANT_001" http://app/api/products
Best for: Legacy apps, SPA frontends that set tenant cookies after auth.

⚠️ Security: Cookies can be spoofed. Use HttpOnly; Secure; SameSite=Strict and validate server-side.

5. jwt — JWT Claim
java
// TenantWallFilter
String token = request.getHeader("Authorization").substring(7);  // "Bearer "
String[] parts = token.split("\\.");
String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
String tenant = new ObjectMapper().readTree(payloadJson).get("tenant_id").asText();
bash
# JWT payload: {"tenant_id":"TENANT_001","sub":"user123"}
curl -H "Authorization: Bearer eyJhbGciOi..." http://app/api/products
Best for: Modern APIs with JWT-based auth (OAuth2, OIDC, Spring Security).

[!WARNING]
The JWT payload is DECODED, not verified. TenantWall does not check the signature.

Signature verification = your responsibility (Spring Security, API gateway)

Tenant isolation = TenantWall's responsibility

This separation is intentional: TenantWall works with any auth system.

⚠️ Security: The tenant claim is treated as data, not code. SQL injection via JWT is impossible. But you must verify the JWT signature upstream before the request reaches TenantWall.

Validation & Safety
The Spring Boot Starter validates the strategy at startup — fail-fast, no silent misconfiguration.

Bad strategy → app fails to start
properties
tenantwall.tenant.strategy=bogus

[ERROR] Invalid tenantwall.tenant.strategy='bogus'.
        Must be one of: header, fixed, session, cookie, jwt
Missing sub-config → app fails to start
properties
tenantwall.tenant.strategy=fixed
# missing tenantwall.tenant.fixedValue

[ERROR] strategy=fixed requires tenantwall.tenant.fixedValue
Multiple strategies set → warning (not fatal)
properties
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
tenantwall.tenant.cookieName=tenant_id    # ← ignored

[WARN] strategy=header but tenantwall.tenant.cookieName is also set (ignored).
       Pick only one strategy.
Strategy Comparison Matrix
Feature	header	fixed	session	cookie	jwt
Client-controlled	✅	❌	⚠️	✅	✅
Requires login	❌	❌	✅	❌	⚠️
Multi-tenant	✅	❌	✅	✅	✅
Works for API	✅	✅	❌	⚠️	✅
Works for SPA	✅	⚠️	❌	✅	✅
Signature verified	❌	N/A	N/A	❌	⚠️ upstream
Setup complexity	🟢 Low	🟢 Low	🟡 Medium	🟢 Low	🟡 Medium
Legend: ✅ yes · ❌ no · ⚠️ depends

Which Strategy Should I Use?

Are you building a REST API?
├── Yes → Do you use JWT (OAuth2/OIDC)?
│         ├── Yes → jwt
│         └── No  → header
└── No (traditional web app)
          ├── Do you use server-side sessions?
          │   ├── Yes → session
          │   └── No  → cookie
          └── Single-tenant? → fixed
Default recommendation: header for APIs, session for web apps, jwt if you already have JWT infra.

Zero-Code Wiring (Spring Boot Starter)
Since v1.1, no filter code is required. The starter auto-registers TenantWallFilter:

xml
<dependency>
    <groupId>com.tenantwall</groupId>
    <artifactId>tenantwall-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
properties
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
That's it. No @Component, no Filter class, no @Order.

Startup log confirms:


[AGENT] Auto-registering TenantWallFilter (strategy: header)
Testing All 5 Strategies
The repo ships megaexample/test-strategies.sh — 14 tests, 100% pass:

bash
cd megaexample
./test-strategies.sh
Covers:

✅ header (own + cross + reverse)

✅ fixed (forced tenant + cross blocked)

✅ session (own + cross)

✅ cookie (own + cross)

✅ jwt (own + cross + reverse)

✅ validation (bogus strategy rejected)

✅ conflict warning (extra keys logged)

Hybrid AST Engine
Complex SQL requires more than regex. TenantWall routes queries to one of two parsers:

Parser	Language	Handles	Latency
JSqlParser 4.9	Java (local)	SELECT, INSERT, UPDATE, DELETE, basic JOIN, simple subqueries	< 1 ms
sqlglot 25+	Python (sidecar)	CTEs, window functions, recursive subqueries, 10 dialects	~5–15 ms
Routing Logic

Incoming SQL
    │
    ▼
┌──────────────────────────────────────┐
│ Contains: WITH, OVER (, PARTITION BY │
│           NVL, DECODE, CONNECT BY,   │
│           ROWNUM                     │
└──────────────┬───────────────────────┘
               │
      ┌────────┴────────┐
      │ Yes             │ No
      ▼                 ▼
  Python Parser     Java Parser
  (sqlglot)         (JSqlParser)
      │                 │
      ├─ Fail ──────────┤
      │                 │
      ▼                 ▼
  ┌──────────────────────┐
  │   Modified SQL       │
  └──────────────────────┘
[!TIP]
If Python is unreachable, the local parser runs as a fallback (fallbacks counter increments). No single point of failure.

Fail-Open Architecture

Request with tenant context?
   ├── YES → rewrite query → execute
   ├── NO, onMissing=fail → throw SecurityException (fail-closed)
   └── NO, onMissing=skip → execute unchanged, log warning
Production: onMissing=fail (fail-closed)

Development: onMissing=skip (fail-open)

What Gets Rewritten
SELECT
sql
-- Before
SELECT * FROM products WHERE price > 100;

-- After
SELECT * FROM products WHERE (price > 100) AND products.tenant_id = 'ACME';
INSERT
sql
-- Before
INSERT INTO products (name, price) VALUES ('Laptop', 999);

-- After
INSERT INTO products (name, price, tenant_id) VALUES ('Laptop', 999, 'ACME');
UPDATE
sql
-- Before
UPDATE products SET price = 888 WHERE id = 5;

-- After
UPDATE products SET price = 888 WHERE (id = 5) AND products.tenant_id = 'ACME';
DELETE
sql
-- Before
DELETE FROM products WHERE id = 5;

-- After
DELETE FROM products WHERE (id = 5) AND products.tenant_id = 'ACME';
CTE (Complex SQL → Python Parser)
sql
-- Before
WITH per_cat AS (
  SELECT category_id, COUNT(*) FROM products GROUP BY category_id
)
SELECT * FROM categories c JOIN per_cat p ON p.category_id = c.id;

-- After
WITH per_cat AS (
  SELECT category_id, COUNT(*) FROM products
  WHERE products.tenant_id = 'ACME'
  GROUP BY category_id
)
SELECT * FROM categories c JOIN per_cat p ON p.category_id = c.id
WHERE c.tenant_id = 'ACME';
What's NOT Rewritten
Some queries should never be filtered. TenantWall skips them:

Category	Example	Why
Excluded tables	tenants, flyway_schema_history	Metadata, not tenant data
Schema DDL	CREATE TABLE, ALTER TABLE	Migration scripts
System queries	SELECT 1, SHOW TABLES	Health checks, introspection
Explicit bypass	tenantwall.enabled=false	Kill switch
Excluded Tables Config
properties
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history,countries,currencies
These tables are queried unmodified — no WHERE tenant_id added.

The Key Insight
[!IMPORTANT]
The JDBC layer is the universal chokepoint. Every query — from ORM, from JdbcTemplate, from native SQL — passes through it. If you can intercept at the JDBC layer, you cover everything.

TenantWall doesn't require developers to remember anything. It doesn't require DBA intervention. It doesn't lock you into an ORM or a database.

It just works.

What You Write vs What TenantWall Does
Task	You	TenantWall
Add dependency	✅	—
Pick tenant strategy	✅ (1 property)	—
Set tenant on request	✅ (auto via filter)	✅ (starter)
Write filter class	❌	✅ (auto-config)
Inject WHERE tenant_id	❌	✅
Handle CTEs, window functions	❌	✅
Cache rewritten SQL	❌	✅
Fallback if parser down	❌	✅
Validate config	❌	✅
Cross-tenant 404	❌	✅
Total code you write: 0 lines (config only).

**Next:** [Architecture →](./04-architecture.md)