# Roadmap

---

## Shipped (v1.0)

The following are complete and verified:

- Java agent (ByteBuddy)
- Hibernate 6.x integration (`StatementPreparerImpl`)
- JDBC `Statement` interceptor (for `JdbcTemplate`)
- CTE / window function support (Python `sqlglot` sidecar)
- Fail-open fallback (local JSqlParser)
- Caffeine query cache (10K entries, 30 min TTL)
- Config via properties / env / system props / agent args
- Verified: MySQL 8.0, SQL Server 2022, Oracle XE 21c
- 88/88 integration tests passing

---

## In Progress (v1.1)

### Bind Variable Template Caching

Hibernate sends queries with `?` placeholders. Currently these are parsed on every distinct query. Template caching will recognize `SELECT * FROM products WHERE id = ?` as a template, caching the rewrite once per shape.

**Expected impact:** 30-50% reduction in parse calls for high-cardinality queries.

### MyBatis Interceptor Adapter

Currently, MyBatis users rely on the JDBC `Statement` interceptor (Layer 2). A dedicated MyBatis adapter will intercept at the `StatementHandler` level — same reliability as the Hibernate interceptor.

### Metrics Endpoint

Prometheus-format endpoint exposing:

- `tenantwall_queries_total`
- `tenantwall_cache_hits_total`
- `tenantwall_cache_misses_total`
- `tenantwall_python_calls_total`
- `tenantwall_fallbacks_total`
- `tenantwall_parse_duration_seconds` (histogram)

### Docker Image

A pre-built Docker image bundling:

- The agent JAR
- The Python parser
- Default config

For quick evaluation without a local build.

---

## Planned (v2.0)

### Framework Adapters

| Adapter | Status | Priority |
|---|---|---|
| jOOQ | Planned | High |
| QueryDSL | Planned | High |
| Spring Data R2DBC (reactive) | Planned | Medium |
| JPA Criteria API | Covered by Hibernate | — |

### Isolation Modes

- **Schema-per-tenant** — each tenant has its own schema (stronger isolation, higher complexity)
- **Database-per-tenant** — extreme isolation, for enterprise customers only

### SQL Complexity

- **Nested IN-clause tuple parsing** — `WHERE (tenant_id, region_id) IN (('1','US'), ('2','EU'))`
- **Oracle PL/SQL hint preservation** — `/*+ INDEX(...) */` hints must survive rewrite
- **SQL Server Always On AG testing** — read replicas + failover
- **Oracle RAC testing** — cluster-aware tenant scoping

### Deployment

- **Native GraalVM image** — build agent as native binary
- **WebAssembly variant** — for edge deployments
- **Kubernetes operator** — declarative `TenantWallConfig` CRD

---

## How to Contribute

We welcome contributions in these areas:

| Area | Difficulty | Notes |
|---|---|---|
| New database integration (Postgres, SQLite, Snowflake) | Easy | Copy `examples/mysql-spring-boot/` and adapt |
| Test suite for existing databases (Oracle 19c, SQL Server 2019) | Easy | Run existing `test-*.sh` against target DB |
| Documentation improvements | Easy | Fix typos, add examples |
| MyBatis adapter | Medium | Follow Hibernate interceptor pattern |
| jOOQ adapter | Medium | Different interception API |
| GraalVM native image | Advanced | Requires reflection config |
| Schema-per-tenant mode | Advanced | Changes isolation model |

### Contribution Process

1. **Fork** the repository
2. **Create a feature branch** — `git checkout -b feature/my-feature`
3. **Add tests** — follow the pattern in `examples/`
4. **Open a PR** with a clear description and test results

### What We Accept

- New database integrations with passing tests
- Performance improvements with benchmarks
- Bug fixes with regression tests
- Documentation clarifications

### What We Don't Accept

- Features that break the "zero code changes" promise
- Changes that require users to modify entities/annotations
- Dependencies on specific ORMs or databases
- Breaking changes in minor versions

---

## Roadmap Principles

The roadmap is guided by five principles:

### 1. Zero Code Changes

Never compromise this. If a feature requires users to annotate entities or modify repositories, it's not for TenantWall.

### 2. Universal Coverage

All JDBC queries — not just ORM. If a new feature only works for one framework, it's not universal enough.

### 3. Fail-Open by Default

Never break production. If a component fails, fall back to the next layer. The cache should always have a next layer.

### 4. Verified Claims Only

Test everything before shipping. If we can't test it, we don't claim it works. The Support Matrix must remain accurate.

### 5. Backward Compatible

No breaking changes in minor versions. Config keys, APIs, and behavior must remain stable across v1.x.

---

## Versioning

TenantWall follows **Semantic Versioning**:

- **Major (v2.0)** — breaking changes
- **Minor (v1.1)** — new features, backward compatible
- **Patch (v1.0.1)** — bug fixes only

Current version: **1.0.0**

---

## Long-Term Vision

We believe multi-tenant data isolation should be:

- **Infrastructure's job**, not the developer's
- **Universal**, not framework-specific
- **Proven**, not marketed

TenantWall is our contribution to that vision.

---

## Feature Requests

Have an idea? Open a GitHub issue with:

- **Use case** — what are you trying to do?
- **Current workaround** — how are you solving it today?
- **Proposed solution** — what would you like TenantWall to do?

We prioritize features by:
1. Impact on preventing data leaks
2. Universal applicability (not framework-specific)
3. Implementation complexity

---

**Next:** [FAQ →](./12-faq.md)