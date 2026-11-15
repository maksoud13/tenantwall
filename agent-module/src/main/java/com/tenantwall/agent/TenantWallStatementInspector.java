package com.tenantwall.agent;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenantWallStatementInspector implements StatementInspector {

    private static final Logger log = LoggerFactory.getLogger("TenantWall");

    @Override
    public String inspect(String sql) {
        try {
            String tenantId = TenantContext.getTenant();
            if (tenantId == null || tenantId.isEmpty()) {
                return sql;
            }

            String modified = SmartSqlModifier.modify(sql, "tenant_id", tenantId);

            if (!modified.equals(sql)) {
                log.debug("{} Modified (Inspector layer)", LogTags.HIBERNATE);
            }

            return modified;

        } catch (Exception e) {
            // Fail-open: return original SQL on unexpected error.
            // This is a security-relevant fallback — visible warning + stack trace.
            log.warn("{} Unexpected error — returning original SQL", LogTags.HIBERNATE, e);
            return sql;
        }
    }
}