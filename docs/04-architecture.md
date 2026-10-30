# Architecture

---

## Component Diagram

```
┌────────────────────────────────────────────────────────────┐
│                    TenantWall Agent JAR                    │
│                                                            │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────┐    │
│  │  ByteBuddy   │─▶│ SmartSql     │─▶│ PythonService  │    │
│  │  Advisor     │  │ Modifier     │  │ Client         │    │
│  └──────┬───────┘  └──────┬───────┘  └────────┬───────┘    │
│         │                 │                    │            │
│         │        ┌────────▼───────┐            │            │
│         │        │  QueryAnalyzer │            │            │
│         │        └────────┬───────┘            │            │
│         ▼                 ▼                    ▼            │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────┐    │
│  │ Statement    │  │  SqlModifier │  │ HTTP to Python │    │
│  │ Interceptor  │  │  (JSqlParser)│  │  sqlglot       │    │
│  └──────────────┘  └──────────────┘  └────────────────┘    │
└────────────────────────────────────────────────────────────┘
                          │
              ┌───────────▼───────────┐
              │   Caffeine Cache      │
              │  (10K entries, 30min) │
              └───────────────────────┘
```

---

## Query Lifecycle

```
1. Application calls repository.findAll()
2. Hibernate generates SQL: "SELECT * FROM products"
3. TenantWall StatementPreparerImpl.prepareQueryStatement() intercepts
4. QueryAnalyzer decides: simple → local JSqlParser
5. SQL rewriter: "SELECT * FROM products WHERE products.tenant_id = ?"
6. Cache stores (sql, column, value) → rewritten SQL
7. Modified SQL reaches MySQL
8. Result set returned to application
```

**Subsequent identical queries:** cache hit, no re-parsing.

---

## Module Structure

```
tenantwall/
├── agent-module/                    ← Java agent (the core)
│   ├── TenantWallAgent              ← premain / ByteBuddy installer
│   ├── TenantwallConfig             ← config loader (SPI-based)
│   ├── TenantContext                ← ThreadLocal tenant holder
│   ├── SmartSqlModifier             ← routing + caching layer
│   ├── QueryAnalyzer                ← "simple vs complex" classifier
│   ├── SqlModifier                  ← JSqlParser-based rewriter
│   ├── SelectHandler                ← SELECT AST manipulation
│   ├── InsertHandler                ← INSERT column injection
│   ├── UpdateHandler                ← UPDATE WHERE augmentation
│   ├── DeleteHandler                ← DELETE WHERE augmentation
│   ├── SqlModifierUtils             ← shared AST helpers
│   ├── TableSchemaRegistry          ← INSERT schema hints (SPI)
│   ├── PythonServiceClient          ← HTTP client to Python parser
│   ├── HibernateStatementAdvice     ← ByteBuddy @Advice for Hibernate
│   ├── SqlInterceptorAdvice         ← ByteBuddy @Advice for Statement
│   └── PrepareStatementAdvice       ← ByteBuddy @Advice for Connection
│
├── tenantwall-parser/               ← Python FastAPI + sqlglot sidecar
│   ├── app/main.py
│   └── app/modifier.py
│
├── examples/                        ← Integration examples
│   ├── mysql-spring-boot/
│   ├── mssql-spring-boot/
│   └── oracle-spring-boot/
│
└── docs/                            ← This documentation
```

---

## Class Responsibilities

| Class | Role |
|---|---|
| `TenantWallAgent` | Entry point — installs ByteBuddy advisors at JVM startup |
| `TenantwallConfig` | Loads config from 6 sources (agent args, sys props, env, classpath, etc.) |
| `TenantContext` | ThreadLocal holder for the current tenant ID |
| `SmartSqlModifier` | Routes queries (simple/complex), manages cache, tracks stats |
| `QueryAnalyzer` | Pattern-matches SQL to decide local vs Python |
| `SqlModifier` | Main entry — dispatches to handlers based on statement type |
| `SelectHandler` | Manipulates `PlainSelect`, `SetOperationList`, CTEs |
| `InsertHandler` | Injects `tenant_id` column into INSERT statements |
| `UpdateHandler` | Augments `WHERE` clause on UPDATE |
| `DeleteHandler` | Augments `WHERE` clause on DELETE |
| `PythonServiceClient` | HTTP client for the Python parser |
| `HibernateStatementAdvice` | ByteBuddy `@Advice` for `StatementPreparerImpl` |

---

## Data Flow — Detailed

### Layer 1: Hibernate Interception

```
Hibernate Session
    │
    ▼
StatementPreparerImpl.prepareQueryStatement(sql)
    │
    ├──→ [ByteBuddy Advice fires]
    │      │
    │      ├── Get tenant from TenantContext (ThreadLocal)
    │      │
    │      ├── SmartSqlModifier.modify(sql, column, tenantId)
    │      │      │
    │      │      ├── Check cache
    │      │      │
    │      │      ├── QueryAnalyzer.isComplex(sql)?
    │      │      │      │
    │      │      │      ├── Simple → JSqlParser
    │      │      │      └── Complex → Python service
    │      │      │
    │      │      └── Return modified SQL
    │      │
    │      └── SQL argument replaced (@Advice.Argument readOnly=false)
    │
    ▼
HikariCP (receives modified SQL)
    │
    ▼
JDBC Driver → Database
```

### Layer 2: Statement Interception

```
JdbcTemplate.query(sql)
    │
    ▼
Statement.executeQuery(sql)
    │
    ├──→ [ByteBuddy Advice fires]
    │      │
    │      ├── Same tenant logic as Layer 1
    │      │
    │      └── SQL argument replaced
    │
    ▼
JDBC Driver → Database
```

---

## Caching Strategy

| Property | Value | Rationale |
|---|---|---|
| **Library** | Caffeine | Fast, in-process, no external dependency |
| **Max size** | 10,000 entries | ~10K unique queries is generous for most SaaS apps |
| **TTL** | 30 minutes | After 30 min, re-parse to catch schema changes |
| **Key** | `{sql}\|{column}\|{value}` | Composite key prevents cross-tenant cache pollution |
| **Invalidation** | On config change | `setFilterColumn()`, `setParserUrl()` clear cache |

### Why Cache?

Without cache:
- Each query = parse cost (~0.8ms local, ~12ms Python)
- 10K QPS = 8-120 seconds of parsing per second (**impossible**)

With cache:
- First query = parse cost
- Subsequent same query = **~0 μs**
- Real-world: 90%+ hit rate after warmup

---

## Failure Modes

| Failure | Detection | Behavior |
|---|---|---|
| Python parser down | HTTP timeout | Fallback to local JSqlParser |
| Local parser error | Exception | Return original SQL (fail-open) |
| No tenant in context | `TenantContext.getTenant() == null` | Depends on `onMissing` |
| Cache corrupt | Not possible (Caffeine) | N/A |
| ByteBuddy install fails | `premain` throws | **JVM fails to start** (fail-fast) |

> [!IMPORTANT]
> **ByteBuddy failures are loud.** If the agent can't install, the JVM won't start. This is intentional — better to fail at startup than to silently leak data.

---

## Thread Safety

| Component | Concurrency Model |
|---|---|
| `TenantContext` | `ThreadLocal<String>` — one tenant per thread |
| `SmartSqlModifier` | Stateless + thread-safe cache (Caffeine) |
| `TenantwallConfig` | `volatile` fields + `CopyOnWriteArraySet` for excluded tables |
| `QueryAnalyzer` | Stateless (compiled patterns) |
| `SqlModifier` | Stateless |

**No locks, no contention, no deadlocks.**

---

**Next:** [Support Matrix →](./05-support-matrix.md)