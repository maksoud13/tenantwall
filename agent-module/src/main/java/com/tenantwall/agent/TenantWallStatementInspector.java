package com.tenantwall.agent;

import org.hibernate.resource.jdbc.spi.StatementInspector;

public class TenantWallStatementInspector implements StatementInspector {

    @Override
    public String inspect(String sql) {
        try {
            String tenantId = TenantContext.getTenant();
            if (tenantId == null || tenantId.isEmpty()) {
                return sql;
            }

            // 🎯 استخدم SmartSqlModifier (نفس اللي الـ Agent بيستخدمه)
            String modified = SmartSqlModifier.modify(sql, "tenant_id", tenantId);
            
            if (!modified.equals(sql)) {
                System.out.println("🎯 [HibernateInspector] Modified: " + modified);
            }
            
            return modified;

        } catch (Exception e) {
            System.err.println("⚠️ [HibernateInspector] Error: " + e.getMessage());
            return sql; // fallback: original SQL
        }
    }
}