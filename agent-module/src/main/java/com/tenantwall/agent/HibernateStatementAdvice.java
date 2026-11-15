package com.tenantwall.agent;

import net.bytebuddy.asm.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HibernateStatementAdvice {

    private static final Logger log = LoggerFactory.getLogger("TenantWall");

    @Advice.OnMethodEnter
    public static void intercept(
            @Advice.Argument(value = 0, readOnly = false) String sql,
            @Advice.Origin("#t.#m") String origin) {

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
            sql = modified; // ← reassignment directly propagates
            log.debug("{} Modified (via {})", LogTags.HIBERNATE, origin);
        }
    }
}