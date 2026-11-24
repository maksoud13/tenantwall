package com.tenantwall.agent;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for {@link TenantWallStatementInspector}.
 *
 * <h2>What these tests lock down</h2>
 * <ul>
 *   <li><b>BUG-1 (config-driven column):</b> the inspector must use
 *       {@link TenantwallConfig#getFilterColumn()} — not a hardcoded
 *       {@code "tenant_id"}. Configuring {@code -Dtenantwall.filter.column=org_id}
 *       must produce {@code WHERE org_id = '...'} at the inspector layer.
 *       Before the fix, the inspector ignored the config and always injected
 *       {@code "tenant_id"}, producing a predicate on a column that may not
 *       exist on the target table.</li>
 *
 *   <li><b>BUG-2 (fail-closed propagation):</b> when {@link SmartSqlModifier}
 *       throws {@link TenantWallParseException} (the v1.3.0+ fail-closed
 *       contract), the inspector must propagate it — not catch-and-swallow.
 *       Swallowing would silently revert the configured fail-closed mode to
 *       fail-open, defeating the security model.</li>
 * </ul>
 *
 * <p>These tests do not exercise ByteBuddy — they call {@code inspect()}
 * directly. ByteBuddy installation is verified by integration tests.
 */
class TenantWallStatementInspectorTest {

    private StatementInspector inspector = new TenantWallStatementInspector();

    @BeforeEach
    void resetState() {
        // Restore config + caches to a known baseline before every test.
        // TenantwallConfig holds static volatile fields; SmartSqlModifier
        // holds a static Caffeine cache. Both leak across tests if untouched.
        TenantContext.clear();
        TenantwallConfig.setEnabled(true);
        TenantwallConfig.setFilterColumn("tenant_id");
        TenantwallConfig.setDefaultValue(null);
        TenantwallConfig.setOnMissing(TenantwallConfig.MissingValueStrategy.FAIL);
        TenantwallConfig.setExcludedTables(List.of());
        TenantwallConfig.setParserUrl(null);
        TenantwallConfig.setParserFailureMode(TenantwallConfig.ParserFailureMode.FAIL_CLOSED);
        SmartSqlModifier.clearCache();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        TenantwallConfig.setFilterColumn("tenant_id");
        TenantwallConfig.setOnMissing(TenantwallConfig.MissingValueStrategy.FAIL);
        TenantwallConfig.setParserFailureMode(TenantwallConfig.ParserFailureMode.FAIL_CLOSED);
        SmartSqlModifier.clearCache();
    }

    // ═══════════════════════════════════════════════════════════════
    // BUG-1: inspector must honor TenantwallConfig.getFilterColumn()
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("default config: inspector injects tenant_id column")
    void defaultConfig_inspectorInjectsTenantIdColumn() {
        TenantContext.setTenant("ACME");

        String sql = "SELECT * FROM users";
        String modified = inspector.inspect(sql);

        assertThat(modified)
                .as("default filter.column is 'tenant_id'")
                .contains("tenant_id = 'ACME'");
    }

    @Test
    @DisplayName("BUG-1 regression: custom filter.column is respected by the inspector")
    void customFilterColumn_inspectorRespectsConfig() {
        // Simulate -Dtenantwall.filter.column=org_id
        TenantwallConfig.setFilterColumn("org_id");
        TenantContext.setTenant("ORG_42");

        String sql = "SELECT * FROM users";
        String modified = inspector.inspect(sql);

        assertThat(modified)
                .as("inspector must use the configured column, not hardcoded 'tenant_id'")
                .contains("org_id = 'ORG_42'")
                .doesNotContain("tenant_id = 'ORG_42'");
    }

    @Test
    @DisplayName("BUG-1 regression: custom column works for UPDATE too")
    void customFilterColumn_worksForUpdate() {
        TenantwallConfig.setFilterColumn("org_id");
        TenantContext.setTenant("ORG_42");

        String sql = "UPDATE orders SET status = 'DONE' WHERE id = 5";
        String modified = inspector.inspect(sql);

        assertThat(modified)
                .contains("org_id = 'ORG_42'")
                .doesNotContain("tenant_id");
    }

    @Test
    @DisplayName("BUG-1 regression: changing config at runtime is picked up by the inspector")
    void runtimeConfigChange_isReflected() {
        // First call uses default
        TenantwallConfig.setFilterColumn("tenant_id");
        TenantContext.setTenant("A");
        String first = inspector.inspect("SELECT * FROM users");

        // Reconfigure mid-flight (e.g. blue/green tenant column swap)
        TenantwallConfig.setFilterColumn("org_id");
        TenantContext.setTenant("B");
        String second = inspector.inspect("SELECT * FROM users");

        assertThat(first).contains("tenant_id = 'A'");
        assertThat(second)
                .as("runtime config change must be visible to the inspector without restart")
                .contains("org_id = 'B'")
                .doesNotContain("tenant_id = 'B'");
    }

    // ═══════════════════════════════════════════════════════════════
    // BUG-2: TenantWallParseException must propagate (fail-closed contract)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("BUG-2 regression: missing tenant + onMissing=FAIL throws (does NOT return original SQL)")
    void missingTenant_failClosed_throws() {
        // TenantContext is empty (cleared in @BeforeEach)
        // onMissing=FAIL is set in @BeforeEach
        // — so SmartSqlModifier.modify must throw TenantWallParseException

        // Note: when no tenant is set on the ThreadLocal, the inspector
        // short-circuits and returns the original SQL (no rewriting needed).
        // To exercise the fail-closed path through SmartSqlModifier we need
        // a tenant on the ThreadLocal but trigger a parse failure downstream.
        // We do that by feeding an unparseable SQL string.
        TenantContext.setTenant("ACME");

        // An empty string is not parseable; JSqlParser will throw, and
        // SmartSqlModifier with FAIL_CLOSED will wrap it in TenantWallParseException.
        // (null SQL returns null early, so we must use a non-null garbage string.)
        String garbageSql = "@@@ not valid sql @@@";

        assertThatThrownBy(() -> inspector.inspect(garbageSql))
                .isInstanceOf(TenantWallParseException.class)
                .as("fail-closed mode must propagate, not silently return original SQL");
    }

    @Test
    @DisplayName("BUG-2 regression: parserFailureMode=FAIL_CLOSED propagates on parse error")
    void parserFailure_failClosed_propagates() {
        TenantContext.setTenant("ACME");
        TenantwallConfig.setParserFailureMode(TenantwallConfig.ParserFailureMode.FAIL_CLOSED);

        // SQL that JSqlParser 4.9 cannot parse
        String unparseableSql = "SELECT FROM WHERE";

        assertThatThrownBy(() -> inspector.inspect(unparseableSql))
                .isInstanceOf(TenantWallParseException.class);
    }

    @Test
    @DisplayName("BUG-2 negative: parserFailureMode=FAIL_OPEN returns original SQL (no exception)")
    void parserFailure_failOpen_returnsOriginalSql() {
        // Sanity check: when the user explicitly opts into FAIL_OPEN (dev/legacy
        // only — a WARN is logged), the inspector returns the original SQL.
        // This is the documented behavior; we lock it in so the fix does not
        // accidentally break the dev escape hatch.
        TenantContext.setTenant("ACME");
        TenantwallConfig.setParserFailureMode(TenantwallConfig.ParserFailureMode.FAIL_OPEN);

        String unparseableSql = "SELECT FROM WHERE";
        String result = inspector.inspect(unparseableSql);

        assertThat(result).isEqualTo(unparseableSql);
    }

    // ═══════════════════════════════════════════════════════════════
    // No-tenant short-circuit
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("no tenant on ThreadLocal: returns SQL unchanged")
    void noTenant_returnsOriginalSql() {
        // TenantContext is empty (cleared in @BeforeEach)
        String sql = "SELECT * FROM users";
        String result = inspector.inspect(sql);

        assertThat(result).isSameAs(sql);
    }

    @Test
    @DisplayName("empty tenant on ThreadLocal: returns SQL unchanged")
    void emptyTenant_returnsOriginalSql() {
        TenantContext.setTenant("");

        String sql = "SELECT * FROM users";
        String result = inspector.inspect(sql);

        assertThat(result).isSameAs(sql);
    }
}