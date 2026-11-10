# Executive Summary

> **TenantWall is a Java agent that enforces multi-tenant data isolation at the JDBC layer — with zero code changes to application logic.**

Every SQL statement (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) is intercepted before execution and rewritten to inject the current tenant's identifier as a `WHERE` predicate. The framework is **database-agnostic** and **ORM-agnostic**.

---

## Key Facts

| Metric | Value |
|---|---|
| **Verified databases** | MySQL 8.0, SQL Server 2022, Oracle XE 21c |
| **Test suite** | 88 integration tests + 14 strategy tests |
| **Pass rate** | 102/102 (100%) |
| **Code changes required** | **Zero** (Spring Boot Starter) |
| **Interception points** | Hibernate `StatementPreparerImpl` + JDBC `Statement` + `Connection.prepareStatement` |
| **Tenant resolution strategies** | 5: header, fixed, session, cookie, jwt |
| **Complex SQL support** | CTEs, window functions, recursive subqueries |
| **Dialects supported by parser** | 10 (mysql, tsql, oracle, postgres, sqlite, redshift, snowflake, bigquery, spark, databricks) |
| **Load test verified** | 3529 req, 39 rps, **0 cross-tenant leaks**, 100% success |

---

## What Makes It Different

| Approach | Code Changes | Coverage | Tenant Resolution |
|---|---|---|---|
| **TenantWall** | **Zero** | **All JDBC queries** | **5 strategies + auto-config** |
| Native RLS / VPD | Extensive | DB-specific | Session variable per DB |
| Hibernate `@TenantId` | Significant | ORM-tracked only | Single strategy |
| ShardingSphere | Moderate | Sharding rules only | N/A |

> [!IMPORTANT]
> **TenantWall is the only solution with universal coverage AND zero code changes AND auto-configured Spring Boot starter.**

---

## The Promise

**Before TenantWall — silent leak:**

```java
List<Order> orders = orderRepo.findByStatus("PENDING");
// SELECT * FROM orders WHERE status = 'PENDING'
// 💥 Returns orders from EVERY tenant
After TenantWall — safe by default:

java
List<Order> orders = orderRepo.findByStatus("PENDING");
// SELECT * FROM orders WHERE status = 'PENDING' AND tenant_id = 'ACME'
// ✅ Returns only ACME's orders
Your application code doesn't change. Not one line. Not one annotation. Just:

properties
tenantwall.enabled=true
tenantwall.tenant.strategy=header
tenantwall.tenant.headerName=X-Tenant-Id
The Spring Boot Starter auto-configures the filter. Done.

Zero-Code Integration (Phase 8)
Since v1.1, the Spring Boot Starter eliminates the need for a custom TenantFilter:

xml
<dependency>
    <groupId>com.tenantwall</groupId>
    <artifactId>tenantwall-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
What you get automatically:

TenantWallFilter registered at HIGHEST_PRECEDENCE

Tenant resolution from header / fixed / session / cookie / JWT

Config validation (fail-fast on bad strategy)

Conflict warning (if >1 strategy key set)

What you don't write: 15 lines of filter code + 1 @Component + ordering annotations.

Who Is This For?
B2B SaaS Founders
Shipping multi-tenant applications. Need data isolation yesterday. Can't afford a security incident.

Enterprise Architects
Migrating from single-tenant to multi-tenant. Audit trail required. Compliance teams asking questions.

Platform Teams
Running 10+ services on a shared database. SOC2, HIPAA, or GDPR compliance obligations.

Not For
Single-tenant apps — overkill for a single customer

Apps with no shared database — use DB-level RLS if you already have it

Greenfield projects that can design tenant_id from day 1 — still useful as defense-in-depth, but less urgent

The Bottom Line
Question	Answer
Does it work?	102/102 tests passing across MySQL, MSSQL, Oracle + 5 strategies
Is it invasive?	Zero code changes — one -javaagent flag + one property
Is it reliable?	Fail-open fallback, no single point of failure
Is it fast?	~0 μs on cache hit, ~0.8 ms on parse miss
Is it safe?	Fail-closed mode; 0 leaks in 3529-request load test
Is it configurable?	5 tenant strategies + per-DB dialect + per-table exclusion
What's Next (Roadmap)
✅ v1.0 — Core engine + 3 verified databases (shipped)

✅ v1.1 — Spring Boot Starter + 5 strategies + JWT (shipped)

🔄 v1.2 — Security hardening (JWT parser auth, bind localhost)

📦 v1.2 — Maven Central publication

🔵 v2.0 — PostgreSQL support, DataSource Catalog

⏸️ v3.0 — Backup/Restore (deferred until 50+ users)

**Next:** [The Problem →](./02-the-problem.md)