package com.tenantwall.agent;

import net.bytebuddy.asm.Advice;

public class HibernateStatementAdvice {

    @Advice.OnMethodEnter
    public static void intercept(
            @Advice.Argument(value = 0, readOnly = false) String sql,
            @Advice.Origin("#t.#m") String origin) {

        if (sql == null || sql.isEmpty()) {
            return;
        }

        if (sql.contains("products") || sql.contains("categories")) {
            System.err.println("🔍 [HibernateAdvice] fired at " + origin);
        }

        String tenantId = TenantContext.getTenant();
        if (tenantId == null || tenantId.isEmpty()) {
            return;
        }

        String column = TenantwallConfig.getFilterColumn();
        String modified = SmartSqlModifier.modify(sql, column, tenantId);

        if (!modified.equals(sql)) {
            sql = modified; // ← reassignment directly propagates
            System.err.println("🎯 [Hibernate] Modified: " + sql);
        }
    }
}