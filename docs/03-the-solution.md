# The Solution: Zero-Code Interception

> **TenantWall rewrites SQL between Hibernate and JDBC. Your application code never sees the difference.**

---

## Before / After

**Application (UNCHANGED):**

```java
public List<Product> listProducts() {
    return productRepository.findAll();
}
```

**Hibernate emits:**

```sql
SELECT p.id, p.name, p.price FROM products p;
```

**TenantWall rewrites (before execution):**

```sql
SELECT p.id, p.name, p.price
FROM products p
WHERE p.tenant_id = 'SHOP_ALPHA';
```

The rewrite is **automatic, universal, and invisible** to the application.

---

## Three-Layer Interception

```
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
```

| Layer | Intercepts | Purpose |
|---|---|---|
| **Layer 1** | Hibernate `prepareQueryStatement` | **Primary** — captures Hibernate-generated SQL before HikariCP wraps it |
| **Layer 2** | JDBC `Statement.executeQuery` | Handles plain `Statement` from `JdbcTemplate` |
| **Layer 3** | JDBC `Connection.prepareStatement` | Fallback for direct `prepareStatement` calls |

---

## Hybrid AST Engine

Complex SQL requires more than regex. TenantWall routes queries to one of two parsers:

| Parser | Language | Handles | Latency |
|---|---|---|---|
| **JSqlParser 4.9** | Java (local) | SELECT, INSERT, UPDATE, DELETE, basic JOIN, simple subqueries | < 1 ms |
| **sqlglot 25+** | Python (sidecar) | CTEs, window functions, recursive subqueries, 10 dialects | ~5–15 ms |

### Routing Logic

```
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
```

> [!TIP]
> **If Python is unreachable**, the local parser runs as a fallback (`fallbacks` counter increments). **No single point of failure.**

---

## Fail-Open Architecture

```
Request with tenant context?
   ├── YES → rewrite query → execute
   ├── NO, onMissing=fail → throw SecurityException (fail-closed)
   └── NO, onMissing=skip → execute unchanged, log warning
```

- **Production:** `onMissing=fail` (fail-closed)
- **Development:** `onMissing=skip` (fail-open)

---

## What Gets Rewritten

### SELECT

```sql
-- Before
SELECT * FROM products WHERE price > 100;

-- After
SELECT * FROM products WHERE (price > 100) AND products.tenant_id = 'ACME';
```

### INSERT

```sql
-- Before
INSERT INTO products (name, price) VALUES ('Laptop', 999);

-- After
INSERT INTO products (name, price, tenant_id) VALUES ('Laptop', 999, 'ACME');
```

### UPDATE

```sql
-- Before
UPDATE products SET price = 888 WHERE id = 5;

-- After
UPDATE products SET price = 888 WHERE (id = 5) AND products.tenant_id = 'ACME';
```

### DELETE

```sql
-- Before
DELETE FROM products WHERE id = 5;

-- After
DELETE FROM products WHERE (id = 5) AND products.tenant_id = 'ACME';
```

### CTE (Complex SQL → Python Parser)

```sql
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
```

---

## The Key Insight

> [!IMPORTANT]
> **The JDBC layer is the universal chokepoint.** Every query — from ORM, from `JdbcTemplate`, from native SQL — passes through it. If you can intercept at the JDBC layer, you cover everything.

TenantWall doesn't require developers to remember anything. It doesn't require DBA intervention. It doesn't lock you into an ORM or a database.

**It just works.**

---

**Next:** [Architecture →](./04-architecture.md)