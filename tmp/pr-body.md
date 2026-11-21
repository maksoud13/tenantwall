## Summary

Fixes 6 cross-tenant bypass paths triggered when a table is listed in
`tenantwall.filter.excludedTables` and the query contains a CTE or subquery
touching a non-excluded table.

| # | Handler | Bypass path | Impact |
|---|---|---|---|
| 1 | `SelectHandler` | CTE alias matches an excluded table name → whole query skipped | Cross-tenant READ |
| 2 | `SelectHandler` | `FROM (subquery)` never recursed → subquery body unfiltered | Cross-tenant READ |
| 3 | `UpdateHandler` | WHERE subquery not processed when target excluded | Cross-tenant WRITE |
| 4 | `UpdateHandler` | SET expression subquery never processed | Cross-tenant WRITE |
| 5 | `DeleteHandler` | WHERE subquery not processed when target excluded | Cross-tenant DELETE |
| 6 | `InsertHandler` | `INSERT … SELECT` body not processed when target excluded | Cross-tenant INSERT |

## Changes

- **`TableExtractor`** — distinguishes CTE aliases from physical tables
- **`SelectHandler`** — per-FROM-item exclusion; recurses into `FROM (subquery)`; threads CTE context
- **`UpdateHandler`** — always processes WHERE subqueries; processes SET expression subqueries via `ExpressionList.getExpressions()`
- **`DeleteHandler`** — always processes WHERE subqueries
- **`InsertHandler`** — `INSERT … SELECT` body always sent through `SelectHandler`
- **Docs** — `06-security-model.md` gains § Table Exclusion Semantics; README gains multi-statement diff examples
- **Version** — bumps to 1.2.1

## Tests

15 new regression tests across 4 test classes. All **247** tests pass:
- Surefire: 247 / 0 failures / 0 errors
- SpotBugs: 0 bugs
- Checkstyle: 0 violations
- JaCoCo: thresholds met

## Verification

```bash
mvn clean verify -Pquality