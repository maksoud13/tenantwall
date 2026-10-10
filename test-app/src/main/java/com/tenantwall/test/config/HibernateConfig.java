package com.tenantwall.test.config;

import com.tenantwall.agent.SmartSqlModifier;
import com.tenantwall.agent.TenantContext;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateConfig {

    @Bean
    public HibernatePropertiesCustomizer statementInspectorCustomizer() {
        return properties -> {
            properties.put(
                    "hibernate.session_factory.statement_inspector",
                    new TenantWallStatementInspector());
        };
    }

    /**
     * The actual inspector that modifies SQL before Hibernate prepares it.
     */
    public static class TenantWallStatementInspector implements StatementInspector {
        @Override
        public String inspect(String sql) {
            try {
                String tenantId = TenantContext.getTenant();
                if (tenantId == null || tenantId.isEmpty()) {
                    return sql;
                }

                String modified = SmartSqlModifier.modify(sql, "tenant_id", tenantId);

                if (!modified.equals(sql)) {
                    System.out.println("🎯 [HibernateInspector] Modified: " + modified);
                }

                return modified;

            } catch (Exception e) {
                System.err.println("⚠️ [HibernateInspector] Error: " + e.getMessage());
                return sql;
            }
        }
    }
}