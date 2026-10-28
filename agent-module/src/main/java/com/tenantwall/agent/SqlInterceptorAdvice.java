package com.tenantwall.agent;

import net.bytebuddy.asm.Advice;

public class SqlInterceptorAdvice {

    @Advice.OnMethodEnter
    public static void intercept(
            @Advice.Argument(value = 0, readOnly = false) String sql) {

        if (sql == null || sql.isEmpty()) {
            return;
        }

        String tenantId = TenantContext.getTenant();
        if (tenantId == null || tenantId.isEmpty()) {
            return;
        }

        String column = TenantwallConfig.getFilterColumn();
        String modified = SmartSqlModifier.modify(sql, column, tenantId);

        if (!modified.equals(sql)) {
            sql = modified;
            System.err.println("✅ [Statement] Modified: " + sql);
        }
    }
}