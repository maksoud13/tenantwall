package com.tenantwall.agent;

import com.tenantwall.agent.TenantwallConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TenantwallConfig}.
 * Focus: validation logic, defaults, and setter behavior.
 */
class TenantwallConfigTest {

    @BeforeEach
    void setUp() {
        // Reset to known defaults
        TenantwallConfig.resetTenantResolution();
        TenantwallConfig.setEnabled(true);
        TenantwallConfig.setFilterColumn("tenant_id");
        TenantwallConfig.setParserUrl(null);
        TenantwallConfig.setDialect("mysql");
        TenantwallConfig.setOnMissing(TenantwallConfig.MissingValueStrategy.SKIP);
        TenantwallConfig.setInsertStrategy(TenantwallConfig.InsertStrategy.SKIP);
    }

    // ═══════════════════════════════════════════════════════════════
    // Defaults
    // ═══════════════════════════════════════════════════════════════

    @Test
    void defaultsAreSane() {
        TenantwallConfig.resetTenantResolution();

        assertThat(TenantwallConfig.getTenantStrategy()).isEqualTo("header");
        assertThat(TenantwallConfig.getTenantHeaderName()).isEqualTo("X-Tenant-Id");
        assertThat(TenantwallConfig.getTenantSessionKey()).isEqualTo("TENANT_ID");
        assertThat(TenantwallConfig.getTenantCookieName()).isEqualTo("tenant_id");
        assertThat(TenantwallConfig.getTenantJwtClaim()).isEqualTo("tenant_id");
        assertThat(TenantwallConfig.getTenantJwtHeader()).isEqualTo("Authorization");
        assertThat(TenantwallConfig.getTenantJwtPrefix()).isEqualTo("Bearer ");
    }

    // ═══════════════════════════════════════════════════════════════
    // Strategy setters
    // ═══════════════════════════════════════════════════════════════

    @Test
    void setTenantStrategy_normalizesToLowercase() {
        TenantwallConfig.setTenantStrategy("HEADER");
        assertThat(TenantwallConfig.getTenantStrategy()).isEqualTo("header");

        TenantwallConfig.setTenantStrategy("  JWT  ");
        assertThat(TenantwallConfig.getTenantStrategy()).isEqualTo("jwt");
    }

    @Test
    void setTenantStrategy_nullFallsBackToHeader() {
        TenantwallConfig.setTenantStrategy(null);
        assertThat(TenantwallConfig.getTenantStrategy()).isEqualTo("header");
    }

    // ═══════════════════════════════════════════════════════════════
    // Validation — all 5 strategies succeed when properly configured
    // ═══════════════════════════════════════════════════════════════

    @Test
    void validate_headerSucceeds() {
        TenantwallConfig.setTenantStrategy("header");
        TenantwallConfig.setTenantHeaderName("X-Tenant-Id");

        assertThatNoException().isThrownBy(TenantwallConfig::validate);
    }

    @Test
    void validate_fixedSucceeds() {
        TenantwallConfig.setTenantStrategy("fixed");
        TenantwallConfig.setTenantFixedValue("TENANT_001");

        assertThatNoException().isThrownBy(TenantwallConfig::validate);
    }

    @Test
    void validate_sessionSucceeds() {
        TenantwallConfig.setTenantStrategy("session");
        TenantwallConfig.setTenantSessionKey("TENANT_ID");

        assertThatNoException().isThrownBy(TenantwallConfig::validate);
    }

    @Test
    void validate_cookieSucceeds() {
        TenantwallConfig.setTenantStrategy("cookie");
        TenantwallConfig.setTenantCookieName("tenant_id");

        assertThatNoException().isThrownBy(TenantwallConfig::validate);
    }

    @Test
    void validate_jwtSucceeds() {
        TenantwallConfig.setTenantStrategy("jwt");
        TenantwallConfig.setTenantJwtClaim("tenant_id");
        TenantwallConfig.setTenantJwtHeader("Authorization");

        assertThatNoException().isThrownBy(TenantwallConfig::validate);
    }

    // ═══════════════════════════════════════════════════════════════
    // Validation — failures
    // ═══════════════════════════════════════════════════════════════

    @Test
    void validate_badStrategy_throws() {
        TenantwallConfig.setTenantStrategy("bogus");

        assertThatThrownBy(TenantwallConfig::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid tenantwall.tenant.strategy");
    }

    @Test
    void validate_fixedWithoutValue_throws() {
        TenantwallConfig.setTenantStrategy("fixed");
        TenantwallConfig.setTenantFixedValue(null);

        assertThatThrownBy(TenantwallConfig::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strategy=fixed requires tenantwall.tenant.fixedValue");
    }

    @Test
    void validate_jwtWithoutHeader_throws() {
        TenantwallConfig.setTenantStrategy("jwt");
        TenantwallConfig.setTenantJwtClaim("tenant_id");
        TenantwallConfig.setTenantJwtHeader(null);

        assertThatThrownBy(TenantwallConfig::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strategy=jwt requires tenantwall.tenant.jwtHeader");
    }

    @Test
    void validate_headerWithNullName_throws() {
        TenantwallConfig.setTenantStrategy("header");
        TenantwallConfig.setTenantHeaderName(null);

        assertThatThrownBy(TenantwallConfig::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strategy=header requires tenantwall.tenant.headerName");
    }

    // ═══════════════════════════════════════════════════════════════
    // Enum parsing
    // ═══════════════════════════════════════════════════════════════

    @Test
    void onMissing_acceptsCaseInsensitive() {
        // Parsing logic used by initialize() when reading config properties
        String[] variants = { "skip", "SKIP", "Skip", "  skip  " };

        for (String value : variants) {
            TenantwallConfig.setOnMissing(
                    TenantwallConfig.MissingValueStrategy.valueOf(value.trim().toUpperCase()));
            assertThat(TenantwallConfig.getOnMissing())
                    .as("value=%s", value)
                    .isEqualTo(TenantwallConfig.MissingValueStrategy.SKIP);
        }
    }
}