package com.tenantwall.starter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TenantWallAutoConfiguration}.
 *
 * <p>Uses {@link WebApplicationContextRunner} (SERVLET type) — required because
 * {@link TenantWallAutoConfiguration} is guarded by
 * {@code @ConditionalOnWebApplication(type = SERVLET)}.
 *
 * <p>The default {@link org.springframework.boot.test.context.runner.ApplicationContextRunner}
 * creates a {@code WebApplicationType.NONE} context, where the condition would
 * fail and the bean would never be registered.
 */
class TenantWallAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantWallAutoConfiguration.class));

    @Test
    void filterBeanRegistered_whenEnabledByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TenantWallFilter.class);
            assertThat(ctx.getBean("tenantWallFilter")).isInstanceOf(TenantWallFilter.class);
        });
    }

    @Test
    void filterBeanNotRegistered_whenDisabled() {
        runner.withPropertyValues("tenantwall.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(TenantWallFilter.class));
    }
}