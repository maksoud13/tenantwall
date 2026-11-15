# TenantWall Parser

**Python FastAPI sidecar for [TenantWall](https://github.com/maksoud13/tenantwall) — Zero-code multi-tenant data isolation for any JDBC database.**

TenantWall is a Java agent that injects tenant filters into SQL queries at the JDBC layer. This image is the Python sidecar responsible for parsing and rewriting complex SQL (CTEs, window functions, recursive subqueries) using [`sqlglot`](https://github.com/tobymao/sqlglot).

It supports **10 SQL dialects** (MySQL, PostgreSQL, Oracle, SQL Server, and more), runs completely locally, and uses **HMAC-SHA256 JWT authentication** between the Java agent and the parser.

---

## 🚀 Quick Start

```bash
docker run -d --name tenantwall-parser \
  -p 127.0.0.1:8000:8000 \
  -e TENANTWALL_PARSER_SECRET="your-32-char-secret-here" \
  -e TENANTWALL_PARSER_ENFORCE_AUTH=1 \
  maksoud13/tenantwall-parser:latest
```

---

## ⚙️ Environment Variables

| Variable | Default | Description |
|:---|:---:|:---|
| `TENANTWALL_PARSER_SECRET` | — | **(Required if auth is on)** HMAC secret for JWT. Must match the Java agent. |
| `TENANTWALL_PARSER_ENFORCE_AUTH` | `1` | Enforce JWT on incoming requests (`1` = yes, `0` = no). |
| `TENANTWALL_PARSER_TOKEN_TTL_SECONDS` | `60` | Allowed age of JWT tokens in seconds. |
| `TENANTWALL_PARSER_PORT` | `8000` | Internal port the parser listens on. |

---

## 🔒 Security

- **Bind locally** — The image is designed to run on `127.0.0.1` only. Never expose it directly to the internet.
- **Auth** — The parser requires a valid JWT signed by the Java agent when `ENFORCE_AUTH=1`.
- **Isolation** — The parser does not connect to a database; it only rewrites SQL text.

---

## 📚 Documentation & Links

- **Main documentation:** [github.com/maksoud13/tenantwall](https://github.com/maksoud13/tenantwall)
- **Java agent (Maven Central):** `io.github.maksoud13:agent-module`
- **Spring Boot Starter:** `io.github.maksoud13:tenantwall-spring-boot-starter`

---

Built with [FastAPI](https://fastapi.tiangolo.com) and [sqlglot](https://github.com/tobymao/sqlglot).