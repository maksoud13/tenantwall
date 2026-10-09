package com.tenantwall.agent;

import net.bytebuddy.asm.Advice;

public class PrepareStatementAdvice {

    @Advice.OnMethodEnter
    public static void intercept(@Advice.AllArguments Object[] args,
            @Advice.Local("alreadyProcessed") boolean alreadyProcessed) {

        if (alreadyProcessed) {
            return;
        } else {

            if (args == null || args.length == 0 || !(args[0] instanceof String)) {
                return;
            }

            String sql = (String) args[0];
            String tenantId = TenantContext.getTenant();

            if (tenantId == null) {
                tenantId = "TENANT_A"; // مؤقت
            }

            String modifiedSql = SqlModifier.addTenantId(sql, tenantId);
            if (!modifiedSql.equals(sql)) {
                args[0] = modifiedSql;
                System.out.println("✅ [PrepStmt] Modified SQL (PreparedStatement): " + modifiedSql);
            }
            alreadyProcessed = true;
        }
    }
}