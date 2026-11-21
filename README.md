<div align="center">
  <img src="./assets/tenantwalllogo.svg" alt="TenantWall - Integration Agent" width="100%" />
</div>

<br />

> **Zero-code multi-tenant data isolation for any JDBC database.**

[![Maven Central](https://img.shields.io/maven-central/v/io.github.maksoud13/tenantwall-spring-boot-starter.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.maksoud13/tenantwall-spring-boot-starter)
[![Docker Pulls](https://img.shields.io/docker/pulls/maksoud13/tenantwall-parser.svg?label=Docker%20Hub)](https://hub.docker.com/r/maksoud13/tenantwall-parser)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./docs/13-license.md)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://openjdk.org/)
[![Tests](https://img.shields.io/badge/tests-390%2B-brightgreen)](./docs/07-test-results.md)
[![Databases](https://img.shields.io/badge/databases-3-blue)](./docs/05-support-matrix.md)
[![Strategies](https://img.shields.io/badge/strategies-5-purple)](./docs/03-the-solution.md#tenant-resolution-strategies-v11)

TenantWall is a Java agent that intercepts every SQL query at the JDBC layer and injects tenant filters — with **zero application code changes**. It works with any framework that uses JDBC (Spring Boot, Quarkus, plain Java) and any database.

**SELECT** — row-level read isolation:
```diff
- SELECT * FROM products;
+ SELECT * FROM products WHERE tenant_id = 'ACME';
```

**INSERT** — `tenant_id` injected at the exact schema position:
```diff
- INSERT INTO products (name, price) VALUES ('Widget', 99);
+ INSERT INTO products (name, price, tenant_id) VALUES ('Widget', 99, 'ACME');
```

**UPDATE** — WHERE clause hardened so no cross-tenant row can be modified:
```diff
- UPDATE products SET price = 100 WHERE id = 42;
+ UPDATE products SET price = 100 WHERE id = 42 AND products.tenant_id = 'ACME';
```

**DELETE** — same protection as UPDATE:
```diff
- DELETE FROM products WHERE id = 42;
+ DELETE FROM products WHERE id = 42 AND products.tenant_id = 'ACME';
```

**CTE with aggregation** — CTE bodies are filtered before aggregation runs:
```diff
- WITH totals AS (SELECT customer_id, SUM(amount) total FROM orders GROUP BY customer_id)
- SELECT * FROM totals;
+ WITH totals AS (SELECT customer_id, SUM(amount) total FROM orders WHERE orders.tenant_id = 'ACME' GROUP BY customer_id)
+ SELECT * FROM totals;
```

**Chained CTEs with JOIN** — every physical table in every CTE body is scoped:
```diff
- WITH active AS (SELECT * FROM products WHERE active = true),
-      items  AS (SELECT * FROM order_items)
- SELECT * FROM active JOIN items ON active.id = items.product_id;
+ WITH active AS (SELECT * FROM products WHERE active = true AND products.tenant_id = 'ACME'),
+      items  AS (SELECT * FROM order_items WHERE order_items.tenant_id = 'ACME')
+ SELECT * FROM active JOIN items ON active.id = items.product_id;
```


Your application code doesn't change. Not one line. Not one annotation.

---

## Why TenantWall?

| Approach | Code Changes | Coverage | Tenant Strategies | DB Lock-in |
|:---|:---|:---|:---|:---|
| **TenantWall** | **Zero** | All JDBC queries | 5 strategies | None |
| Native RLS / VPD | Extensive | DB-specific | Session variable | Full |
| ShardingSphere | Moderate | Sharding rules only | N/A | Moderate |
| Hibernate MultiTenancy | Significant | ORM-tracked only | Single | Hibernate |

---

## Quick Start (2 minutes)

### 1. Add the dependency (Maven Central)

```xml
<dependency>
    <groupId>io.github.maksoud13</groupId>
    <artifactId>tenantwall-spring-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

### 2. Run the Python Parser (Docker Hub)

```bash
docker run -d --name tenantwall-parser \
  -p 127.0.0.1:8000:8000 \
  -e TENANTWALL_PARSER_SECRET="your-32-char-secret-here" \
  maksoud13/tenantwall-parser:latest
```

> Use the same secret in your `tenantwall.properties`.

### 3. Run your app with the agent

```bash
java -javaagent:agent-module-1.2.0.jar=tenantwall.properties -jar app.jar
```

---

## The 5 Tenant Strategies

Pick exactly one strategy per JVM — config validation fails fast on bad values.

| Strategy | Reads tenant from | Best for |
|:---|:---|:---|
| `header` | `X-Tenant-Id` HTTP header | APIs, microservices |
| `fixed` | Hard-coded config value | Single-tenant, tests |
| `session` | `HttpSession` attribute | Traditional web apps |
| `cookie` | HTTP cookie (`tenant_id`) | Legacy apps, SPAs |
| `jwt` | JWT claim (`tenant_id`) | OAuth2/OIDC APIs |

Full details: [The Solution → Tenant Resolution Strategies](./docs/03-the-solution.md#tenant-resolution-strategies-v11).

---

## Test Results

> Full details: [docs/07-test-results.md](./docs/07-test-results.md)

### Summary

| Category | Tests | Status |
|:---|:---:|:---|
| Unit — Java | 232 | ✅ 232 / 232 |
| Unit — Python | 48 | ✅ 48 / 48 |
| Integration — 3 DBs | 88 | ✅ 88 / 88 |
| Strategy tests | 14 | ✅ 14 / 14 |
| Concurrency (1,000 parallel) | 2 | ✅ 2 / 2, 0 leaks |
| JWT E2E | 6 | ✅ 6 / 6 |
| Load test (8,439 requests) | — | ✅ 98.11% success, 0 leaks |

**Total: 390 automated tests** across Java (232) + Python (48) + Integration (88) + Strategy (14) + Concurrency (2) + JWT E2E (6).

### Performance (with agent)

| Endpoint | p50 | p95 | p99 |
|:---|:---:|:---:|:---:|
| lookup | 96 ms | 201 ms | 283 ms |
| SKU | 98 ms | 205 ms | 288 ms |
| cross-tenant (blocked) | 121 ms | 317 ms | 403 ms |
| paginated list | 984 ms | 2.2 s | 2.88 s |

Agent overhead is approximately **0 ms**. All latency comes from DB queries.

---

## Verified Databases

| Database | Version | Driver | Tests |
|:---|:---|:---|:---|
| MySQL | 8.0 | mysql-connector-j 8.3.0 | ✅ 30 / 30 |
| SQL Server | 2022 | mssql-jdbc 12.6.1 | ✅ 29 / 29 |
| Oracle | XE 21c | ojdbc11 23.4.0 | ✅ 29 / 29 |

See full [support matrix](./docs/05-support-matrix.md).

---

## Documentation

| Section | Description |
|:---|:---|
| [Executive Summary](./docs/01-executive-summary.md) | What TenantWall is and why it matters |
| [The Problem](./docs/02-the-problem.md) | Silent leaks in multi-tenant SaaS |
| [The Solution](./docs/03-the-solution.md) | Zero-code interception + 5 strategies |
| [Getting Started](./docs/09-getting-started.md) | 2-minute integration guide |
| [Configuration](./docs/10-configuration.md) | All config keys, sources, and strategies |
| [Security Model](./docs/06-security-model.md) | What's guaranteed and what's out of scope |
| [Roadmap](./docs/11-roadmap.md) | Shipped, in progress, deferred |

---

## License

MIT — see [docs/13-license.md](./docs/13-license.md).