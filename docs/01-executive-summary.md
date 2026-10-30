# Executive Summary

> **TenantWall is a Java agent that enforces multi-tenant data isolation at the JDBC layer — with zero code changes to application logic.**

Every SQL statement (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) is intercepted before execution and rewritten to inject the current tenant's identifier as a `WHERE` predicate. The framework is **database-agnostic** and **ORM-agnostic**.

---

## Key Facts

| Metric | Value |
|---|---|
| **Verified databases** | MySQL 8.0, SQL Server 2022, Oracle XE 21c |
| **Test suite** | 88 tests across 3 databases |
| **Pass rate** | 88/88 (100%) |
| **Code changes required** | Zero (business logic) |
| **Interception points** | Hibernate `StatementPreparerImpl` + JDBC `Statement` |
| **Complex SQL support** | CTEs, window functions, recursive subqueries |
| **Dialects supported by parser** | 10 (mysql, tsql, oracle, postgres, sqlite, redshift, snowflake, bigquery, spark, databricks) |

---

## What Makes It Different

| Approach | Code Changes | Coverage | DB Lock-in |
|---|---|---|---|
| **TenantWall** | **Zero** | **All JDBC queries** | **None** |
| Native RLS / VPD | Extensive | DB-specific | Full |
| ShardingSphere | Moderate | Sharding rules only | Moderate |
| Hibernate MultiTenancy | Significant | ORM-tracked only | Hibernate |

> [!IMPORTANT]
> **TenantWall is the only solution that provides universal coverage with zero application code changes.**

---

## The Promise

**Before TenantWall — silent leak:**

```java
List<Order> orders = orderRepo.findByStatus("PENDING");
// SELECT * FROM orders WHERE status = 'PENDING'
// 💥 Returns orders from EVERY tenant
```

**After TenantWall — safe by default:**

```java
List<Order> orders = orderRepo.findByStatus("PENDING");
// SELECT * FROM orders WHERE status = 'PENDING' AND tenant_id = 'ACME'
// ✅ Returns only ACME's orders
```

**Your application code doesn't change.** The agent intercepts, rewrites, and returns control.

---

## Who Is This For?

### B2B SaaS Founders

Shipping multi-tenant applications. Need data isolation yesterday. Can't afford a security incident.

### Enterprise Architects

Migrating from single-tenant to multi-tenant. Audit trail required. Compliance teams asking questions.

### Platform Teams

Running 10+ services on a shared database. SOC2, HIPAA, or GDPR compliance obligations.

---

## Not For

- **Single-tenant apps** — overkill for a single customer
- **Apps with no shared database** — use DB-level RLS if you already have it
- **Greenfield projects that can design `tenant_id` from day 1** — still useful as defense-in-depth, but less urgent

---

## The Bottom Line

| Question | Answer |
|---|---|
| **Does it work?** | 88/88 tests passing across MySQL, MSSQL, Oracle |
| **Is it invasive?** | Zero code changes — one `-javaagent` flag |
| **Is it reliable?** | Fail-open fallback, no single point of failure |
| **Is it fast?** | ~0 μs on cache hit, ~0.8 ms on parse miss |
| **Is it safe?** | Fail-closed mode available for production |

---

**Next:** [The Problem →](./02-the-problem.md)