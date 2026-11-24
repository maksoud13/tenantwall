## [1.2.1] - 2026-11-25

### 🔒 Security Fixes

TenantWall v1.2.1 fixes **6 tenant-isolation bypass vulnerabilities** discovered
during a security audit. All 6 are fixed on `security/subquery-exclusion-bypass`
and have 15 regression tests. No breaking changes.

| # | Vulnerability | Fix |
|---|---|---|
| 1 | CTE alias matching excluded table name bypassed filter | `TableExtractor` now distinguishes CTE aliases from physical tables |
| 2 | `FROM (subquery)` never recursed → body unfiltered | `collectPhysical` + `addPhysicalFromItem` now recurse into `ParenthesedSelect` |
| 3 | `UPDATE` WHERE subquery not processed when target excluded | `UpdateHandler` processes WHERE subquery |
| 4 | `UPDATE` SET expression subquery never processed | `UpdateHandler` processes SET expressions |
| 5 | `DELETE` WHERE subquery not processed when target excluded | `DeleteHandler` processes WHERE subquery |
| 6 | `INSERT ... SELECT` body not processed when target excluded | `InsertHandler` processes SELECT body |

### 🧪 Tests

- +15 regression tests for subquery exclusion bypasses
- `TableExtractor` line coverage: 0% → **100%**
- `TenantWallStatementInspector` tests added

### 📊 Coverage

- Agent module: **86.8%** line coverage (JaCoCo excludes for ByteBuddy-only classes)
- Excluded from coverage: `tenantwallAgent`, `SqlInterceptorAdvice`, `PrepareStatementAdvice`, `HibernateStatementAdvice`, `SqlModifierDebugger`

### 📦 Dependencies

- No dependency changes.

### ⚠️ Upgrade

```xml
<dependency>
    <groupId>io.github.maksoud13</groupId>
    <artifactId>tenantwall-spring-boot-starter</artifactId>
    <version>1.2.1</version>
</dependency>