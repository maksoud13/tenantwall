# Support Matrix

---

## Verified (tested by maintainers)

| Database | Version | Driver | Tests | Result |
|---|---|---|---|---|
| **MySQL** | 8.0 | `mysql-connector-j` 8.3.0 | 30 | ✅ **30/30** |
| **SQL Server** | 2022 Developer | `mssql-jdbc` 12.6.1 | 29 | ✅ **29/29** |
| **Oracle** | XE 21c | `ojdbc11` 23.4.0 | 29 | ✅ **29/29** |

> [!IMPORTANT]
> **Total: 88/88 tests passing (100%)** — every test runs against a Dockerized database with seeded data for two tenants.

---

## Supported (should work; not yet tested)

| Database | Version | Notes |
|---|---|---|
| MySQL | 5.7 | Driver compatible; test suite not run |
| SQL Server | 2019, 2022 Enterprise | Same T-SQL parser; AG not tested |
| Oracle | 12c, 19c | Same SQL dialect; RAC not tested |
| PostgreSQL | 14+ | Parser supports `postgres`; integration not built |
| SQLite | 3.x | Parser supports `sqlite` dialect |
| Snowflake | Current | Parser supports `snowflake` |
| BigQuery | Current | Parser supports `bigquery` |
| Redshift | Current | Parser supports `redshift` |
| Spark SQL | 3.x | Parser supports `spark` |
| Databricks | Current | Parser supports `databricks` |

> **Community testing welcome** — [file an issue](https://github.com/maksoud13/tenantwall/issues) with results.

---

## Roadmap (planned, not implemented)

- jTDS driver support (legacy SQL Server)
- PL/SQL hint preservation (Oracle)
- DBLink-aware tenant scoping (Oracle)
- Always On Availability Group testing (SQL Server)
- Oracle RAC cluster testing
- MyBatis interceptor adapter
- jOOQ and QueryDSL adapters
- Schema-per-tenant mode (vs row-level)
- Bind variable template caching

---

## Database Dialects

The Python parser (`sqlglot`) supports these dialects:

| Dialect | Value | Database |
|---|---|---|
| MySQL | `mysql` | MySQL, MariaDB |
| T-SQL | `tsql` | SQL Server, Azure SQL |
| Oracle | `oracle` | Oracle |
| PostgreSQL | `postgres` | PostgreSQL, Redshift |
| SQLite | `sqlite` | SQLite |
| Redshift | `redshift` | Amazon Redshift |
| Snowflake | `snowflake` | Snowflake |
| BigQuery | `bigquery` | Google BigQuery |
| Spark | `spark` | Apache Spark SQL |
| Databricks | `databricks` | Databricks |

Set via `tenantwall.parser.dialect=<value>` in config.

---

## What "Verified" Means

A database is **verified** when:

1. A complete example exists in `examples/<db>-spring-boot/`
2. The example has a `docker-compose.yml` with the DB
3. `schema.sql` + `data.sql` are provided and tested
4. `test-<db>-integration.sh` has 29+ tests
5. All tests pass in CI on every commit
6. The maintainers have run it on their machines

**If a database is not in the verified list, we don't claim it works.** We'd rather under-promise and over-deliver.

---

## What "Supported" Means

A database is **supported** when:

1. The SQL dialect is recognized by the parser (`sqlglot` supports it)
2. The JDBC driver is compatible with JDBC 4.2+
3. The query rewriting logic should work based on SQL syntax similarity
4. We haven't personally tested it end-to-end

**Supported ≠ broken.** It means "expected to work, but no proof."

---

## What "Planned" Means

A **planned** feature is:

1. Documented in the roadmap
2. Not yet implemented
3. No timeline commitment
4. Community contributions welcome

---

## Adding a New Database

To verify a new database, follow the pattern in `examples/mysql-spring-boot/`:

```
examples/<db>-spring-boot/
├── pom.xml                          # Add DB driver
├── docker-compose.yml               # DB container
├── tenantwall.properties            # Set dialect
├── src/main/resources/
│   ├── application.yml              # DB connection
│   ├── schema.sql                   # Table definitions
│   └── data.sql                     # Seed data (2 tenants)
└── test-<db>-integration.sh         # 29+ tests
```

**Steps:**

1. Copy `examples/mysql-spring-boot/`
2. Replace MySQL driver with the target DB driver
3. Adapt `schema.sql` to the target DB syntax
4. Adapt `data.sql` (identity inserts, commit statements)
5. Update `docker-compose.yml` with the new image
6. Update `tenantwall.properties` (`dialect=<target>`)
7. Run `test-<db>-integration.sh` — aim for 29+ passing tests
8. Open a PR with the test results

---

## Version Compatibility Matrix

| TenantWall | Hibernate | Java | Spring Boot |
|---|---|---|---|
| v1.0 | 6.2+ | 11+ | 3.x |
| v1.0 | 6.5 | 21 | 3.3.5 |

**Other combinations:** untested, but likely compatible. File an issue if you test them.

---

**Next:** [Security Model →](./06-security-model.md)