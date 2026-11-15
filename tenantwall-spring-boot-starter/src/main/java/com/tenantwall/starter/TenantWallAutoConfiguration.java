package com.tenantwall.starter;

import com.tenantwall.agent.LogTags;
import com.tenantwall.agent.TenantwallConfig;
import jakarta.servlet.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger("TenantWall");

    @Bean
    @ConditionalOnMissingBean(name = "tenantWallFilter")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public Filter tenantWallFilter() {
        TenantwallConfig.validate(); // fail fast on bad config
        String strategy = TenantwallConfig.getTenantStrategy();
        log.info("{} Auto-registering TenantWallFilter (strategy: {})", LogTags.AGENT, strategy);
        return new TenantWallFilter();
    }
}