package com.tenantwall.starter;

import com.tenantwall.agent.TenantwallConfig;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Spring Boot auto-configuration for TenantWall.
 *
 * <p>
 * Registers a {@link TenantWallFilter} bean at highest precedence,
 * so tenant context is available before any other filter or controller runs.
 *
 * <p>
 * Activated automatically when the app starts. User does not need to
 * declare any beans or filters in their code.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "tenantwall.enabled", matchIfMissing = true)
public class TenantWallAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "tenantWallFilter")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public Filter tenantWallFilter() {
        TenantwallConfig.validate(); // fail fast on bad config
        String strategy = TenantwallConfig.getTenantStrategy();
        System.out.println("[AGENT] Auto-registering TenantWallFilter (strategy: " + strategy + ")");
        return new TenantWallFilter();
    }
}
