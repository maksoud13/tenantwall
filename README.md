# 🛡️ TenantWall

> **Zero-code multi-tenant data isolation for any JDBC database.**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./docs/13-license.md)
[![Java](https://img.shields.io/badge/Java-11%2B-orange)](https://openjdk.org/)
[![Tests](https://img.shields.io/badge/tests-88%2F88-brightgreen)](./docs/07-test-results.md)
[![Databases](https://img.shields.io/badge/databases-3-blue)](./docs/05-support-matrix.md)

TenantWall is a Java agent that intercepts every SQL query at the JDBC layer and injects tenant filters — with **zero application code changes**. It works with any framework that uses JDBC (Spring Boot, Quarkus, plain Java) and any database.

```diff
- SELECT * FROM products;                              -- ❌ leaks all tenants
+ SELECT * FROM products WHERE tenant_id = 'ACME';    -- ✅ tenant-isolated
```

---

## Why TenantWall?

| Approach | Code Changes | Coverage | DB Lock-in |
|---|---|---|---|
| **TenantWall** | **Zero** | **All JDBC queries** | **None** |
| Native RLS / VPD | Extensive | DB-specific | Full |
| ShardingSphere | Moderate | Sharding rules only | Moderate |
| Hibernate MultiTenancy | Significant | ORM-tracked only | Hibernate |

---

## Quick Start (5 minutes)

```bash
# 1) Build the agent
cd agent-module
mvn clean install -DskipTests

# 2) Start a database (any of the examples)
cd examples/mysql-spring-boot
docker compose up -d

# 3) Run the app with the agent
java -javaagent:tenantwall-agent.jar=tenantwall.properties \
     -jar target/store-app.jar

# 4) Test tenant isolation
curl -H "X-Tenant-Id: SHOP_ALPHA" http://localhost:8080/api/products
curl -H "X-Tenant-Id: SHOP_BETA"  http://localhost:8080/api/products
```

---

## Verified Databases

| Database | Version | Tests | Result |
|---|---|---|---|
| MySQL | 8.0 | 30 | ✅ 30/30 |
| SQL Server | 2022 | 29 | ✅ 29/29 |
| Oracle | XE 21c | 29 | ✅ 29/29 |
| **Total** | | **88** | **✅ 88/88** |

See [full support matrix](./docs/05-support-matrix.md).

---

## Documentation

| Section | Description |
|---|---|
| [Executive Summary](./docs/01-executive-summary.md) | What TenantWall is and why it matters |
| [The Problem](./docs/02-the-problem.md) | Silent leaks in multi-tenant SaaS |
| [The Solution](./docs/03-the-solution.md) | How zero-code interception works |
| [Architecture](./docs/04-architecture.md) | Component diagram and query lifecycle |
| [Support Matrix](./docs/05-support-matrix.md) | Verified, supported, and planned databases |
| [Security Model](./docs/06-security-model.md) | What's guaranteed and what's out of scope |
| [Test Results](./docs/07-test-results.md) | 88/88 tests across 3 databases |
| [Engineering Journey](./docs/08-engineering-journey.md) | What didn't work and why |
| [Getting Started](./docs/09-getting-started.md) | 5-minute integration guide |
| [Configuration](./docs/10-configuration.md) | All config keys and sources |
| [Roadmap](./docs/11-roadmap.md) | Shipped, in progress, planned |
| [FAQ](./docs/12-faq.md) | Common questions |
| [License](./docs/13-license.md) | MIT |

---

## Project Structure

```
tenantwall/
├── agent-module/             ← Java agent (the core)
├── tenantwall-parser/        ← Python FastAPI + sqlglot sidecar
├── examples/
│   ├── mysql-spring-boot/
│   ├── mssql-spring-boot/
│   └── oracle-spring-boot/
└── docs/                     ← This documentation
```

---

## License

MIT — see [docs/13-license.md](./docs/13-license.md).