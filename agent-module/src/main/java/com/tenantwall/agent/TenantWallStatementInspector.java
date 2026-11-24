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
            String column = TenantwallConfig.getFilterColumn();
            String modified = SmartSqlModifier.modify(sql, column, tenantId);

            if (!modified.equals(sql)) {
                log.debug("{} Modified (Inspector layer)", LogTags.HIBERNATE);
            }

            return modified;

        } catch (TenantWallParseException e) {
            // v1.3.0 fail-closed contract: a query that cannot be safely
            // rewritten MUST NOT run unfiltered. SmartSqlModifier already
            // throws when onMissing=FAIL or parserFailureMode=FAIL_CLOSED
            // (the defaults). Swallowing that exception here would have
            // silently reverted to fail-open, defeating the entire security
            // model. Propagate as-is.
            throw e;
        } catch (Exception e) {
            // Truly unexpected error (NPE, ClassNotFound, ...). Returning the
            // original SQL is a security-relevant fallback — log loudly so it
            // is visible in monitoring, not silently swallowed.
            log.warn("{} Unexpected error — returning original SQL", LogTags.HIBERNATE, e);
            return sql;
        }
    }
}