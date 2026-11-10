# FAQ

> **Common questions about TenantWall — organized by topic.**

---

## Table of Contents

1. [General](#general)
2. [Tenant Strategies](#tenant-strategies)
3. [Architecture & Behavior](#architecture--behavior)
4. [Configuration](#configuration)
5. [Security](#security)
6. [Performance](#performance)
7. [Troubleshooting](#troubleshooting)
8. [Migration & Adoption](#migration--adoption)
9. [Comparison](#comparison)
10. [Roadmap & Future](#roadmap--future)

---

## General

### Is it production-ready?

**Yes, with documented caveats.**

| Aspect | Status |
|--------|--------|
| Integration tests | ✅ 102/102 passing (88 integration + 14 strategy) |
| Cross-tenant isolation | ✅ 0 leaks in 3,529-request load test |
| Fail-closed mode | ✅ Available (`onMissing=fail`) |
| Tenant strategies | ✅ 5 verified (header, fixed, session, cookie, jwt) |
| 3 verified databases | ✅ MySQL 8.0, SQL Server 2022, Oracle XE 21c |
| Maven Central | ⚠️ **Not yet published** — on roadmap (Tier 1) |
| Battle-tested at scale | ⚠️ No public case study yet |
| SLF4J logging | ⚠️ Uses `System.err` — on roadmap (Tier 1) |

**Recommendation:**
- **Today:** Safe for **internal tools and staging**. Integrate from source.
- **After Tier 1 (weeks 1-2):** Safe for **production** — Maven Central published, SLF4J, JWT parser auth.
- **After Month 2:** Battle-tested with PostgreSQL + real users.

---

### What's the performance overhead?

From benchmarks on the megaexample load test (3,529 requests, 20 VUs, 90 seconds):

| Scenario | Overhead | Notes |
|---|---|---|
| **Cache hit** (repeated SQL) | **~0 μs** | Most production queries after warmup |
| **Cache miss, simple query** (local JSqlParser) | ~0.8 ms | SELECT/INSERT/UPDATE/DELETE |
| **Cache miss, complex query** (Python sidecar) | ~12 ms | CTEs, window functions — network RTT dominates |
| **Fail-open fallback** (Python down) | ~1.5 ms | Local parser handles simple SQL |

**Agent impact on JVM:**
- CPU usage: **4.3%** (from Prometheus)
- GC overhead: **0.043%** (negligible)
- Memory: ~640MB / 4GB heap (16% used)

**The cache is the key** — after warmup, most queries pay **zero** parsing cost. The bottleneck in the load test was **database queries** (indexes, COUNT(*), CTE scans), not the agent.

---

### Does it work with Hibernate 5.x?

**Not verified.** The `StatementPreparerImpl` API differs between Hibernate 5 and 6.

| Hibernate version | Status |
|-------------------|--------|
| 6.2+ | ✅ Verified |
| 6.0 – 6.1 | ⚠️ Not tested |
| 5.x | ❌ Not supported |

Community contribution welcome — [open an issue](https://github.com/maksoud13/tenantwall/issues) if you want to test Hibernate 5.x.

**Workaround for Hibernate 5.x:** You can use Layer 2 (`Statement` interceptor) for `JdbcTemplate` queries, but Hibernate queries won't be intercepted at the primary layer.

---

### Can I use it without Spring Boot?

**Yes.** TenantWall integrates at the JDBC layer, not Spring.

**You need:**
1. Hibernate 6.2+ (or use plain `Statement`/`PreparedStatement` — Layer 2/3 interceptor)
2. A `ThreadLocal` holding the current tenant (implement `TenantContext` equivalent)
3. The agent JAR on the `-javaagent` flag

**What you lose without Spring Boot Starter:**
- Auto-registered `TenantWallFilter` (you write ~15 lines)
- Config validation at startup
- Strategy conflict warnings

**What you keep:**
- All SQL rewriting
- Parser routing
- Cache
- All config keys

**Example (plain Java):**

```java
public class MyTenantFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        String tenant = ((HttpServletRequest) req).getHeader("X-Tenant-Id");
        try {
            TenantContext.setTenant(tenant);
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }
}
Does it work with connection pooling?
Yes. The rewrite happens before the connection is obtained from the pool.

Pool	Status	Notes
HikariCP	✅ Verified	Used in all integration tests
Tomcat JDBC	✅ Should work	Same JDBC spec
C3P0	⚠️ Not tested	Likely works
DBCP2	⚠️ Not tested	Likely works
Why pool doesn't matter: TenantWall intercepts at StatementPreparerImpl.prepareQueryStatement (Hibernate SPI) — before HikariCP wraps the connection. The rewrite is already done when the SQL reaches the pool.

Is there a Docker image?
Not yet. On the roadmap (Tier 1 — Week 2).

Planned image:

Agent JAR

Python parser

Default config

docker-compose.yml for MySQL + sidecar

For now, build from source (see Getting Started).

Tenant Strategies
Which strategy should I use?
If your app...	Use	Config
Is an API / microservice with tenant header	header	tenantwall.tenant.strategy=header
Uses Spring Security + JWT	jwt	tenantwall.tenant.strategy=jwt
Has traditional login + HttpSession	session	tenantwall.tenant.strategy=session
Has a legacy SPA setting a cookie	cookie	tenantwall.tenant.strategy=cookie
Is a single-tenant test/dev env	fixed	tenantwall.tenant.strategy=fixed
Default recommendation:

REST API → header (or jwt if you have auth infra)

Traditional web app → session

Single-tenant → fixed

Can I use multiple strategies at once?
No. Pick exactly one strategy per JVM.

Why? Because a request can only resolve to one tenant. If you need multi-strategy testing, use separate app instances:


┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  Pod: header│  │  Pod: jwt   │  │  Pod: cookie│
│  :8081      │  │  :8082      │  │  :8083      │
└─────────────┘  └─────────────┘  └─────────────┘
       │                │                │
       └────────────────┴────────────────┘
                        │
                  ┌─────▼─────┐
                  │  MySQL    │
                  └───────────┘
This is exactly what megaexample/pods/ does — 5 pods, 5 strategies, one database. See the sequential load test.

What if I need to switch strategies?
Change one property, restart the JVM:

properties
# Was:
# tenantwall.tenant.strategy=header
# tenantwall.tenant.headerName=X-Tenant-Id

# Now:
tenantwall.tenant.strategy=session
tenantwall.tenant.sessionKey=TENANT_ID
No code changes. Config validation ensures the new strategy is valid at startup.

[!NOTE]
Strategies cannot be changed at runtime. TenantwallConfig validates at initialize() — a restart is required.

What happens if JWT signature is invalid?
TenantWall does not verify JWT signatures. It decodes the payload only.

Why? Because TenantWall is auth-agnostic. Signature verification is your responsibility:

API Gateway (Kong, Apigee, AWS API Gateway) → verifies signature

Spring Security → verifies signature before filter

Custom auth filter → verifies signature

What TenantWall DOES guarantee:

The tenant claim is treated as data, not code

SQL injection via JWT is impossible

Cross-tenant SQL isolation is enforced

What you MUST do:

Verify the JWT signature upstream (gateway/Spring Security)

Reject expired tokens

Reject tokens with invalid signatures

Example with Spring Security:

java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
Spring Security verifies the JWT signature before TenantWall's filter runs.

Can the header strategy be spoofed?
Yes — the X-Tenant-Id header is client-controlled. You must validate it against your auth system.

❌ WRONG — trusting the header blindly:

java
// TenantWall does this (isolated tenant):
String tenant = request.getHeader("X-Tenant-Id");
TenantContext.setTenant(tenant);

// But the client can send ANY tenant ID:
//   curl -H "X-Tenant-Id: OTHER_TENANT" http://app/api/products
// → User authenticated as TENANT_001 sees TENANT_OTHER's data
✅ CORRECT — validate against authenticated user:

java
// In your auth layer (NOT in TenantWall):
@Configuration
public class TenantValidationFilter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        User user = securityContext.getAuthenticatedUser();
        String headerTenant = ((HttpServletRequest) req).getHeader("X-Tenant-Id");

        if (headerTenant != null && !user.getTenants().contains(headerTenant)) {
            throw new AccessDeniedException("Tenant not allowed: " + headerTenant);
        }

        TenantContext.setTenant(headerTenant != null ? headerTenant : user.getTenantId());
        chain.doFilter(req, res);
    }
}
Rule: TenantWall isolates SQL. Auth is still your job.

How does jwt strategy handle nested claims?
Dot notation works for nested claims:

properties
# JWT payload: {"user": {"org": {"id": "TENANT_001"}}}
tenantwall.tenant.jwtClaim=user.org.id
Supported claim types:

Strings: {"tenant_id": "TENANT_001"} → tenant_id

Numbers: {"tenant_id": 123} → tenant_id (converted to string)

Nested: {"user": {"tenant_id": "T"}} → user.tenant_id

Not supported (yet):

Arrays: {"tenants": ["A", "B"]} → not resolvable (single tenant only)

Boolean claims: {"active": true} → not a valid tenant

If your JWT uses a non-string claim, extract it in a custom filter and use TenantContext.setTenant(...) directly.

Can I set the tenant manually (bypassing strategies)?
Yes. If you need custom logic (e.g., from a request-scoped context, database lookup), use TenantContext directly:

java
try {
    TenantContext.setTenant("CUSTOM_TENANT");
    // All queries in this scope are filtered to CUSTOM_TENANT
} finally {
    TenantContext.clear();
}
⚠️ Important: Always clear in finally to prevent leaks.

Use cases:

Multi-tenant admin dashboards (switch tenant per request)

Scheduled jobs that iterate over all tenants

Migration scripts

Custom auth (e.g., read tenant from API key)

Example — iterate over all tenants:

java
@Scheduled(cron = "0 0 2 * * *")  // 2 AM daily
public void nightlyReports() {
    for (String tenant : tenantRepo.findAllIds()) {
        TenantContext.setTenant(tenant);
        try {
            reportService.generateForCurrentTenant();
        } finally {
            TenantContext.clear();
        }
    }
}
Does the strategy affect performance?
No measurable difference. All 5 strategies resolve the tenant before SQL execution.

From the sequential load test (3,529 requests each):

Strategy	Requests	RPS	Success	p50 lookup	Leaks
header	3,529	39.1	100%	96.6 ms	0
fixed	~3,500	~39	100%	~95 ms	0
session	~3,500	~39	100%	~97 ms	0
cookie	~3,500	~39	100%	~96 ms	0
jwt	~3,500	~39	100%	~97 ms	0
Difference: < 2% across all strategies — within noise.

Why? The tenant resolution is O(1) for all strategies (header lookup, cookie read, session get, JWT decode). The SQL rewriting cost dominates, and it's identical across strategies.

JWT is slightly slower because of the Base64 decode + JSON parse (~5-10 µs per request). Negligible at scale.

Architecture & Behavior
What happens if the Python parser is down?
Fail-open fallback to local JSqlParser:

Query type	Behavior
Simple (SELECT, INSERT, UPDATE, DELETE)	✅ Rewritten by local parser
Complex (CTE, window, recursive)	⚠️ Attempted by JSqlParser; if it fails, original SQL runs unmodified
In production:

Monitor the fallbacks counter (see /api/admin/smart-stats)

A steady increase means Python is down

Complex queries may leak without filtering — set up alerting

Mitigation:

Run Python parser as a sidecar with health checks

Use docker-compose to auto-restart

Kubernetes: readiness probe on parser

Is there a per-tenant schema option?
Not yet. Current version supports row-level isolation (shared schema, tenant_id column).

Isolation model	Status
Row-level (shared schema)	✅ Current
Schema-per-tenant	🔮 Planned (v2.0)
Database-per-tenant	❌ Not planned
Why row-level first? It's the most common SaaS pattern. It scales to thousands of tenants. Schema-per-tenant is for enterprise customers only (5-50 tenants).

How do I handle the tenants table itself?
Exclude it in config:

properties
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history
Why? The tenants table typically stores tenant metadata (name, plan, created_at) — not per-tenant data. Filtering it would break tenant discovery.

What about a "tenant_users" join table? That DOES have tenant_id — don't exclude it.

What about multi-tenant queries intentionally spanning tenants?
E.g., a billing job that sums across all tenants:

java
List<Object[]> allRevenue = repo.getTotalRevenueAcrossAllTenants();
Options:

#	Approach	When to use
1	Exclude the table	Single query, dedicated view
2	System tenant (TenantContext.setTenant("SYSTEM"))	Job runner with onMissing=skip
3	Bypass agent for admin service	Separate JVM without -javaagent
4	Direct JDBC with TenantContext.clear() before query	Last resort — audit this
Recommended for scheduled jobs:

java
@Scheduled(cron = "0 0 2 * * *")
public void aggregateRevenue() {
    TenantContext.setTenant("SYSTEM");  // not a real tenant — filter sees literal "SYSTEM"
    try {
        // Cross-tenant query — but tenant_id = 'SYSTEM' won't match anything
        // Use excluded view instead: billing_summary
    } finally {
        TenantContext.clear();
    }
}
Better: Create a dedicated view that aggregates across tenants, and exclude it:

sql
CREATE VIEW billing_summary AS
SELECT tenant_id, SUM(amount) FROM invoices GROUP BY tenant_id;
properties
tenantwall.filter.excludedTables=billing_summary
Now the job queries billing_summary (not filtered) and gets aggregate data.

How do I debug why a query isn't filtered?
5-step diagnostic:

Step	Check
1	Startup logs — look for [AGENT] Interceptors installed (v4)
2	Query logs — look for 🎯 [Hibernate] Modified: ...
3	Tenant context — TenantContext.getTenant() should not be null
4	Config — tenantwall.enabled=true, filter.column=tenant_id
5	DB general log — see what reaches the database
If the log shows Modified SQL but DB gets original:

You're on Layer 3 (Javassist proxy) — Hibernate < 6.2 or non-Hibernate ORM. See Engineering Journey.

If the log shows nothing:

Filter not intercepting — check -javaagent flag and tenantwall.enabled=true.

If context is null:

For header strategy: header not sent

For fixed strategy: fixedValue not set

For @PostConstruct: runs before HTTP request — use skip for seed data

Configuration
What's the difference between onMissing=skip and onMissing=fail?
Mode	Behavior	Use case
skip	Executes original SQL, logs warning	Development, staging
fail	Throws SecurityException	Production
[!IMPORTANT]
In production, always use onMissing=fail. If a query fires without tenant context, that's a bug — better to fail than leak.

Migration path:


Development  → skip
Staging      → skip (catch bugs early)
Production   → fail (after verifying all paths)
Why does @PostConstruct seed data fail with onMissing=fail?
@PostConstruct runs before any HTTP request, so TenantContext is empty. The agent refuses to filter — which is correct behavior.

Options:

1. Use onMissing=skip during development (simplest):

properties
tenantwall.filter.onMissing=skip
2. Manually set the tenant in @PostConstruct:

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
3. Use Flyway for seed data (bypasses the agent via excludedTables).

4. Use defaultValue config:

properties
tenantwall.filter.defaultValue=SYSTEM
tenantwall.filter.onMissing=skip
Now SYSTEM is used as fallback — but this only works with skip.

Can I change the filter column name?
Yes. Set:

properties
tenantwall.filter.column=org_id
Common alternatives:

Column	Use case
tenant_id (default)	Generic SaaS
org_id	B2B with organizations
account_id	B2B with accounts
customer_id	B2C with customer entities
workspace_id	Tools like Slack/Notion
client_code	Legacy systems
The agent will inject WHERE org_id = '...' instead.

How do I disable TenantWall temporarily?
Option 1 — Config:

properties
tenantwall.enabled=false
Option 2 — System property:

bash
-Dtenantwall.enabled=false
Option 3 — Remove the -javaagent flag entirely.

[!CAUTION]
Never disable in production. Disabling removes all tenant filters — silent data leaks will happen.

What is insert.strategy and why does it matter?
INSERT INTO t VALUES (...) (without explicit column list) is ambiguous — TenantWall can't know where tenant_id goes.

Value	Behavior
skip	Default. Skip injection, log error
append	Append tenant_id as last column (⚠️ only correct if tenant_id is last)
fail	Throw exception
Best practice: Use skip (default) + explicit column lists:

sql
INSERT INTO products (name, price) VALUES (?, ?);
-- TenantWall injects: (name, price, tenant_id) VALUES (?, ?, 'TENANT_001')
If you can't change the code, use schema hints:

properties
tenantwall.schema.products=id,name,price,tenant_id
Now TenantWall knows tenant_id is the 4th column and injects correctly.

Why should I use tenantwall.schema.<table>?
For INSERT INTO t VALUES (...) statements where the column order is not explicit in the SQL.

Example:

sql
-- App sends:
INSERT INTO orders VALUES (?, ?, ?, ?)

-- TenantWall can't know which position is tenant_id
-- Use schema hint:
properties
tenantwall.schema.orders=id,customer_id,product_name,total,tenant_id
Now TenantWall knows tenant_id is column 5 and rewrites:

sql
INSERT INTO orders VALUES (?, ?, ?, ?, 'TENANT_001')
When to use:

Legacy code you can't modify

Stored procedures with INSERT INTO t VALUES (...)

Batch inserts from JdbcTemplate.batchUpdate()

When NOT to use:

Explicit column lists already present (INSERT INTO t (a, b) VALUES (?, ?))

Modern ORMs (they generate explicit column lists)

What is excludedTables and when should I use it?
Tables without a tenant_id column must be excluded — otherwise queries fail with "column not found."

properties
tenantwall.filter.excludedTables=tenants,audit_log,flyway_schema_history,countries,currencies
Typical excluded tables:

Table	Why exclude
tenants	Tenant metadata (not per-tenant data)
audit_log	Cross-tenant audit trail
flyway_schema_history	Migration tracking
countries, currencies	Global lookups
roles, permissions	Global RBAC
payment_methods	Shared config
[!WARNING]
Don't over-exclude. Every table in excludedTables is not filtered — a leak risk if it actually holds tenant data.

Audit checklist:

sql
-- Every non-excluded table MUST have a tenant_id column
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'your_db'
  AND COLUMN_NAME = 'tenant_id';
Compare against your exclusion list.

Security
Can an attacker exploit the X-Tenant-Id header?
No for SQL injection. Yes for IDOR (Insecure Direct Object Reference) — if you don't validate the header.

SQL injection — IMPOSSIBLE:

java
new StringValue(value)   // JSqlParser — creates a string literal
Renders as:

sql
WHERE tenant_id = 'attacker-controlled-value'
The tenant value is data, not code. An attacker can:

Set their own tenant (if they have credentials — that's auth)

Cannot inject SQL fragments

Cannot break out of the string literal

IDOR — POSSIBLE if you don't validate:

bash
# User authenticated as TENANT_001
curl -H "X-Tenant-Id: TENANT_OTHER" http://app/api/products
# → Without validation, sees TENANT_OTHER's data
✅ CORRECT — validate against auth:

java
if (!user.getTenants().contains(headerTenant)) {
    throw new AccessDeniedException("Tenant not allowed");
}
Rule: TenantWall prevents SQL injection. Auth is still your job.

Does TenantWall protect against insider threats?
Partially.

Threat	Protected?
Developer forgets WHERE tenant_id	✅ Yes (agent injects)
Native SQL bypass	✅ Yes (JDBC interceptor)
ORM bypass	✅ Yes (Hibernate interceptor)
DBA with direct DB access	❌ Out of scope
Application-level auth bugs	❌ Out of scope
Malicious authenticated user	❌ Out of scope (that's auth)
Defense in depth is required — TenantWall is one layer of your security strategy:


Layer 1: Authentication (who is this user?)
Layer 2: Authorization (what can they access?)
Layer 3: 🛡️ TenantWall (cross-tenant isolation at SQL layer)
Layer 4: Database constraints (NOT NULL, FK, CHECK)
Layer 5: Audit logging (who did what when?)
How do I audit which queries ran for which tenant?
3 audit layers:

1. Agent logs (per-query):


🎯 [Hibernate] Modified: SELECT * FROM products WHERE tenant_id = 'ACME'
Redirect to file:

bash
java -javaagent:agent.jar=config.properties -jar app.jar 2> agent.log
2. Database general log (per-query):

MySQL:

sql
SET GLOBAL general_log = 'ON';
SET GLOBAL general_log_file = '/var/log/mysql/general.log';
SQL Server:

sql
-- Extended Events → sql_statement_starting
Oracle:

sql
AUDIT SELECT TABLE;
3. Application audit (per-request):

java
@Component
public class TenantAuditFilter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        String tenant = TenantContext.getTenant();
        log.info("tenant={} path={} user={}", tenant, path, user);
        chain.doFilter(req, res);
    }
}
Ship all 3 to your log aggregator (Splunk, Datadog, ELK) for compliance.

Is JWT signature verification done by TenantWall?
No. TenantWall only decodes the JWT payload — it does not verify the signature.

Your responsibility:

Layer	Responsibility
API Gateway (Kong, Apigee)	Verify JWT signature
Spring Security (@EnableWebSecurity)	Verify JWT signature
Custom auth filter	Verify JWT signature
TenantWall	Extract tenant_id claim, inject SQL filter
Why this separation? Because TenantWall is auth-agnostic. It works with any auth system (JWT, OAuth2, session, API keys, mTLS). If it tried to verify signatures, it would need to know your signing algorithm, keys, issuer, audience — defeating the purpose of a universal tool.

Best practice:

java
// Spring Security verifies JWT signature BEFORE TenantWall filter runs
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.decoder(jwtDecoder()))
        );
        return http.build();
    }
}
Spring Security runs first (verifies signature). Then TenantWall's filter runs (extracts tenant claim). By the time TenantWall sees the request, the JWT is guaranteed valid.

Performance
How do I measure the agent's overhead?
3 approaches:

1. Reproducible benchmark (roadmap Tier 2):

bash
cd megaexample
./test-load.sh             # baseline + intercepted
./test-load.sh --skip-baseline  # intercepted only
Compares:

Baseline (no agent)

Intercepted (with agent)

Outputs p50, p95, p99 latency differences.

2. Prometheus metrics:

bash
curl http://localhost:8080/actuator/prometheus | grep -E "process_cpu|jvm_gc"
Look for:

process_cpu_usage < 10% (healthy)

jvm_gc_overhead < 1% (healthy)

3. Agent smart-stats:

bash
curl http://localhost:8080/api/admin/smart-stats
Returns:

json
{
  "pythonCalls": 10,
  "localCalls": 124,
  "fallbacks": 0,
  "cache.hits": 77,
  "cache.misses": 520
}
Why is my cache hit rate low?
In tests, expected. In production, should be >80%.

Test environments:

reset-stats clears cache

Warmup + test = new SQL shapes

Test hit rate: 10-20% (normal)

Production:

Stable SQL shapes → 80-95% hit rate after warmup

Cache TTL: 30 min

If hit rate is low in production:

Cause	Fix
Many unique queries (dynamic SQL)	Increase --max-size (roadmap v1.2)
TTL too short	Increase TTL (roadmap v1.2)
Cache invalidation on setFilterColumn()	Avoid runtime column changes
Multi-tenant with per-tenant SQL	Expected — different SQL shapes
Does the agent slow down startup?
Minimal impact. ~1-2 seconds additional startup.

Breakdown:

Agent JAR load: ~200ms

ByteBuddy advice install: ~500ms

Config load: ~50ms

Python parser health check: ~10ms (if configured)

For a typical Spring Boot app (10s startup): +5% startup time.

Why was findAll slow in the load test (522ms)?
Not the agent's fault — it's Hibernate's COUNT(*) behavior.

What happens:

java
Page<Product> products = repo.findAll(PageRequest.of(0, 20));
Hibernate generates:

SELECT COUNT(*) FROM products WHERE tenant_id = 'X' (522ms — scans 40K rows)

SELECT * FROM products WHERE tenant_id = 'X' LIMIT 20 (fast)

The COUNT(*) is the slow part.

Fix:

java
// Use Slice instead of Page — removes COUNT(*)
Slice<Product> products = repo.findAll(PageRequest.of(0, 20));
Result: 522ms → ~50ms (10x faster).

On the roadmap (Phase 9): Page → Slice in megaexample.

Troubleshooting
Error: NoClassDefFoundError: TenantContext
Cause: Missing -javaagent flag — the agent classes aren't on the classpath.

Fix:

bash
java -javaagent:tenantwall-agent.jar=tenantwall.properties -jar app.jar
Verify:

Agent JAR exists at the path

Path uses correct separator (: Linux/Mac, \ Windows)

Error: Cannot open database (SQL Server)
Cause: Database doesn't exist. Unlike MySQL, SQL Server doesn't auto-create databases from JDBC URL.

Fix:

bash
docker exec -i store-mssql /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "TenantWall123!" -C \
  -Q "CREATE DATABASE store_demo;"
Error: Oracle invalid username/password
Cause: Wrong credentials or the user wasn't created.

Fix: Check the docker-compose.yml:

yaml
environment:
  ORACLE_PASSWORD: "TenantWall123!"
  APP_USER: store_demo
  APP_USER_PASSWORD: "TenantWall123!"
The APP_USER is created automatically on first startup. If it's not:

bash
docker compose down -v  # ⚠️ deletes data
docker compose up -d
Error: IllegalStateException: Invalid tenantwall.tenant.strategy
Cause: Bad strategy value in config.

Fix: Use one of:

properties
tenantwall.tenant.strategy=header
# or: fixed, session, cookie, jwt
Check the startup log for the exact error message:


[ERROR] Invalid tenantwall.tenant.strategy='bogus'.
Must be one of: header, fixed, session, cookie, jwt
Query has WHERE tenant_id but DB got original
Cause: You're on Layer 3 (Javassist proxy) — @Advice.AllArguments(readOnly=false) doesn't propagate across proxy boundaries.

Fix: Use Hibernate 6.2+ so Layer 1 (Hibernate SPI) is used instead. See Engineering Journey.

Diagnosis:

Startup logs show [AGENT] Interceptors installed (v4) ✅

Query logs show 🎯 [Hibernate] Modified: ... ✅

DB general log shows no WHERE tenant_id ❌

→ This is the Layer 3 problem.

Emoji garbled in Windows console
Cause: Console code page doesn't support UTF-8.

Fix:

powershell
chcp 65001
Or configure Windows Terminal to use UTF-8 by default:


Settings → Defaults → Additional settings → "Set UTF-8 as default encoding"
App fails to start after adding the starter
Common causes:

Cause	Fix
Spring Boot 2.x	Upgrade to Spring Boot 3.x
Missing spring-boot-autoconfigure	Add to pom.xml
Missing jakarta.servlet-api	Add to pom.xml
tenantwall.properties not found	Check agent arg path
Invalid strategy	See error message
Diagnostic: Check the full stack trace. The error message usually tells you exactly what's wrong.

@PostConstruct seed data crashes with onMissing=fail
See: Why does @PostConstruct seed data fail with onMissing=fail?

Quick fix: Use onMissing=skip for development, or set tenant manually:

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
Migration & Adoption
How do I migrate from single-tenant to multi-tenant?
5-phase migration:

Phase 1: Schema migration

sql
-- Add tenant_id to all data tables
ALTER TABLE products ADD COLUMN tenant_id VARCHAR(50) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE orders ADD COLUMN tenant_id VARCHAR(50) NOT NULL DEFAULT 'LEGACY';

-- Add indexes
CREATE INDEX idx_products_tenant ON products(tenant_id);
CREATE INDEX idx_orders_tenant ON orders(tenant_id);
Phase 2: Deploy TenantWall (read-only)

properties
tenantwall.enabled=true
tenantwall.filter.onMissing=skip
tenantwall.tenant.strategy=fixed
tenantwall.tenant.fixedValue=LEGACY
All queries are filtered to LEGACY — app works identically.

Phase 3: Add auth + tenant resolution

properties
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
Add filter to read tenant from auth:

java
if (!user.getTenants().contains(headerTenant)) {
    throw new AccessDeniedException();
}
Phase 4: Test with real tenants

Create 2 test tenants

Run cross-tenant tests → expect 404

Run full integration suite

Phase 5: Enable onMissing=fail

properties
tenantwall.filter.onMissing=fail
Now any query without tenant context fails — catches bugs early.

How do I add TenantWall to an existing app?
Minimal changes:

pom.xml:

xml
<dependency>
    <groupId>io.github.tenantwall</groupId>
    <artifactId>tenantwall-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
tenantwall.properties:

properties
tenantwall.enabled=true
tenantwall.filter.column=tenant_id
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
Deployment:

bash
java -javaagent:agent.jar=tenantwall.properties -jar app.jar
No changes to:

Controllers

Repositories

Entities (unless you want @TenantId for extra safety)

SQL queries

Can I test TenantWall without deploying?
Yes. Each example is a self-contained test environment:

bash
cd examples/mysql-spring-boot
docker compose up -d
mvn clean package -DskipTests
java -javaagent:tenantwall-agent.jar=tenantwall.properties -jar target/store-app.jar &
sleep 15
bash test-mysql-integration.sh
Expected: PASS: 30/30 in under 5 seconds after startup.

Comparison
TenantWall vs Hibernate @TenantId
Feature	TenantWall	Hibernate @TenantId
Code changes	Zero	Annotate every entity
Native SQL	✅ Covered	❌ Not covered
Complex queries	✅ CTEs, window functions	⚠️ ORM-only
Framework lock-in	None	Hibernate
DB support	Any JDBC	Any (but ORM-bound)
Setup time	2 minutes	30+ minutes
Multi-strategy	✅ 5 strategies	Single strategy
Use TenantWall if: You want zero code changes and full coverage.

Use Hibernate @TenantId if: You're already 100% ORM and want the safest possible path.

Use both if: You want maximum safety (defense in depth).

TenantWall vs Row-Level Security (RLS)
Feature	TenantWall	RLS
DBA involvement	None	Required
Code changes	Zero	Some (session context)
Cross-DB support	✅ Single config	❌ Per-DB syntax
Performance	Cache + parse	Native
Debugging	Agent logs	DB logs
Works across databases	✅ Yes	❌ No
Multi-strategy	✅ 5 strategies	DB-specific
Use TenantWall if: You want DB-agnostic, zero-code isolation.

Use RLS if: You're on a single DB, want max performance, and have DBA support.

Use both if: You want defense in depth (RLS at DB + TenantWall at JDBC).

TenantWall vs ShardingSphere
Feature	TenantWall	ShardingSphere
Purpose	Tenant isolation	Sharding / data distribution
Code changes	Zero	Moderate (rules config)
Complexity	Low	High (proxy layer)
Row-level filtering	✅ Primary feature	⚠️ Possible but awkward
Works with ORMs	✅ All	⚠️ Driver-level
Scale	Single DB	Multi-DB (sharding)
Use TenantWall if: You need tenant isolation, not sharding.

Use ShardingSphere if: You need horizontal scaling across multiple DBs.

Different tools for different problems — they can coexist.

TenantWall vs Spring Security multi-tenancy
Feature	TenantWall	Spring Security MT
Layer	JDBC	HTTP (auth)
Enforces SQL filter	✅ Yes	❌ No
Prevents SQL leaks	✅ Yes	❌ No
Auth + JWT verify	⚠️ Decode only	✅ Full
Zero SQL code changes	✅ Yes	N/A
Different concerns:

Spring Security → Authentication (who is this user?)

TenantWall → SQL isolation (can this user see other tenants' data?)

Use both — they're complementary.

Roadmap & Future
Is Maven Central publication planned?
Yes — it's Tier 1, Week 2 of the roadmap.

Why not yet?

Need SLF4J migration first (Tier 1)

Need JWT parser auth first (Tier 1)

Need concurrency tests first (Tier 1)

Why it's critical: 90% of developers won't git clone a library. Without Maven Central, adoption is blocked.

Expected timeline:

Week 1: Security hardening

Week 2: Maven Central publish

End of Week 2: mvn dependency works

Track progress: Roadmap →

Is PostgreSQL support planned?
Yes — it's Tier 4, Month 2 of the roadmap.

Why Month 2 and not Week 1? Because PostgreSQL support requires:

Testcontainers integration test

Documentation page

Example project

Docker image with postgres:16

That's ~2 weeks of work. It's after Maven Central because getting the tool adopted (any DB) is higher priority than supporting the 4th DB.

Current status: Parser supports postgres dialect. Full integration test pending.

Workaround: Use tenantwall.parser.dialect=postgres with the Python parser. The agent will work, but not officially "verified."

Is a Backup/Restore feature planned?
Not in the near term. It's deferred until 50+ users request it.

Why?

Backup/Restore scope	Reality
Features	20 sub-features (export, restore, FK sort, encryption, etc.)
Time estimate	2 months solo dev
Market	Different product category (data management, not security)
Priority	Core isolation must be perfect first
What might change this: If 50+ users explicitly request it, we'll revisit.

Alternative today:

bash
mysqldump -u root -p store_demo products \
  --where="tenant_id='TENANT_001'" > tenant_001_products.sql
For per-tenant backup today, use native DB tools with --where.

Is a DataSource Catalog planned?
Phase 1 only — Tier 5, Month 3+.

What's planned:

DataSourceCatalog class

Auto-discover from Spring (@Primary default enabled)

Per-DS config (tenantwall.ds.<name>.column, etc.)

/api/admin/catalog endpoint

What's deferred:

Per-Factory TenantContext (Map<DS, Tenant>)

Connection tagging wrapper

Schema discovery

Catalog export/import

Why defer Phase 2+? Because per-factory context requires 10+ multi-DS users to design correctly. It's a problem looking for a use case.

When it will ship: When 10+ users have 2+ DataSources and need isolation between them.

Is a UI planned?
No — out of scope, permanently.

Why?

TenantWall is a library, not a management tool

Adding a UI would 10x the maintenance burden

Better to integrate with existing tools (Prometheus, Grafana, Datadog)

What you get instead:

Agent logs (JSON-parseable)

/api/admin/smart-stats (REST endpoint)

Prometheus metrics (roadmap v1.2)

Standard System.err output (until SLF4J integration)

If you want a UI: Build it on top of the REST endpoints and logs. TenantWall stays out of the way.

What about async/reactive support?
Async — deferred. Reactive — not planned for v1.x.

Runtime	Status
Spring MVC (blocking)	✅ Verified
Spring WebFlux (reactive)	❌ Not supported
CompletableFuture	⚠️ Needs TransmittableThreadLocal
Kotlin coroutines	⚠️ Same issue
The problem: ThreadLocal doesn't propagate across threads. In async code:

java
CompletableFuture.supplyAsync(() -> {
    // TenantContext is EMPTY here — different thread
    return repo.findAll();
});
Workaround: Use TransmittableThreadLocal from Alibaba:

xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>transmittable-thread-local</artifactId>
    <version>2.14.3</version>
</dependency>
This is deferred (5+ async users needed).

Will TenantWall always be free?
Yes. MIT license, permanent.

Why? Because open-source tools grow faster, attract more contributors, and become standards. The alternative (closed source) would limit adoption.

What might change:

Managed SaaS (hosted config) — planned Year 2

Enterprise support (SLA, custom features) — planned Year 3

Always: Core library stays MIT

How do I contribute?
See: Roadmap → How to Contribute.

Good first issues:

Add PostgreSQL example (examples/postgres-spring-boot/)

Write SQLite test suite

Improve documentation

Add Prometheus metrics exporter

Contribution process:

Fork the repo

git checkout -b feature/my-feature

Add tests (follow examples/ pattern)

Open a PR with test results

Related Docs
The Solution → — 5 strategies explained

Getting Started → — 2-minute quickstart

Configuration → — all config keys

Security Model → — what's guaranteed

Test Results → — 102/102 tests passing

---

**Next:** [License →](./13-license.md)