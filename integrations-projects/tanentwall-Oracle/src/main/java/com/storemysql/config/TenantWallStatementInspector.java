package com.storeoracle.config;

import com.tenantwall.agent.SmartSqlModifier;
import com.tenantwall.agent.TenantContext;
import com.tenantwall.agent.TenantwallConfig;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate-level SQL rewriter.
 * Fires BEFORE Hibernate builds the PreparedStatement,
 * so the modified SQL is used directly.
 */
public class TenantWallStatementInspector implements StatementInspector {

    @Override
    public String inspect(String sql) {
        try {
            String tenantId = TenantContext.getTenant();
            if (tenantId == null || tenantId.isEmpty()) {
                return sql;
            }

            String column = TenantwallConfig.getFilterColumn();
            String modified = SmartSqlModifier.modify(sql, column, tenantId);

            if (!modified.equals(sql)) {
                System.err.println("🎯 [Inspector] Modified: " + modified);
            }
            return modified;

        } catch (Exception e) {
            System.err.println("⚠️ [Inspector] Error: " + e.getMessage());
            return sql;  // fail-safe
        }
    }
}