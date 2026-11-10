package com.tenantwall.starter;

import com.tenantwall.agent.TenantContext;
import com.tenantwall.agent.TenantwallConfig;
import com.tenantwall.starter.TenantWallFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TenantWallFilter}.
 * Verifies all 5 tenant resolution strategies + context lifecycle.
 */
class TenantWallFilterTest {

    private TenantWallFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        filter = new TenantWallFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);

        TenantwallConfig.resetTenantResolution();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TenantwallConfig.resetTenantResolution();
    }

    // ─── Helper: capture tenant during filter chain ───
    private String captureTenantDuringChain() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        FilterChain chain = (req, res) -> captured.set(TenantContext.getTenant());
        filter.doFilter(request, response, chain);
        return captured.get();
    }

    // ═══════════════════════════════════════════════════════════════
    // Strategy: header
    // ═══════════════════════════════════════════════════════════════

    @Test
    void header_resolvesTenant() throws Exception {
        TenantwallConfig.setTenantStrategy("header");
        TenantwallConfig.setTenantHeaderName("X-Tenant-Id");
        when(request.getHeader("X-Tenant-Id")).thenReturn("TENANT_001");

        assertThat(captureTenantDuringChain()).isEqualTo("TENANT_001");
    }

    @Test
    void header_missingHeader_noTenant() throws Exception {
        TenantwallConfig.setTenantStrategy("header");
        TenantwallConfig.setTenantHeaderName("X-Tenant-Id");
        when(request.getHeader("X-Tenant-Id")).thenReturn(null);

        assertThat(captureTenantDuringChain()).isNull();
    }

    @Test
    void header_blankHeader_noTenant() throws Exception {
        TenantwallConfig.setTenantStrategy("header");
        TenantwallConfig.setTenantHeaderName("X-Tenant-Id");
        when(request.getHeader("X-Tenant-Id")).thenReturn("   ");

        assertThat(captureTenantDuringChain()).isNull();
    }

    // ═══════════════════════════════════════════════════════════════
    // Strategy: fixed
    // ═══════════════════════════════════════════════════════════════

    @Test
    void fixed_resolvesConfiguredValue() throws Exception {
        TenantwallConfig.setTenantStrategy("fixed");
        TenantwallConfig.setTenantFixedValue("TENANT_FIXED");

        assertThat(captureTenantDuringChain()).isEqualTo("TENANT_FIXED");
    }

    // ═══════════════════════════════════════════════════════════════
    // Strategy: session
    // ═══════════════════════════════════════════════════════════════

    @Test
    void session_resolvesFromAttribute() throws Exception {
        TenantwallConfig.setTenantStrategy("session");
        TenantwallConfig.setTenantSessionKey("TENANT_ID");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("TENANT_ID")).thenReturn("TENANT_SESSION");

        assertThat(captureTenantDuringChain()).isEqualTo("TENANT_SESSION");
    }

    @Test
    void session_noSession_noTenant() throws Exception {
        TenantwallConfig.setTenantStrategy("session");
        when(request.getSession(false)).thenReturn(null);

        assertThat(captureTenantDuringChain()).isNull();
    }

    @Test
    void session_missingAttribute_noTenant() throws Exception {
        TenantwallConfig.setTenantStrategy("session");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("TENANT_ID")).thenReturn(null);

        assertThat(captureTenantDuringChain()).isNull();
    }

    // ═══════════════════════════════════════════════════════════════
    // Strategy: cookie
    // ═══════════════════════════════════════════════════════════════

    @Test
    void cookie_resolvesFromCookie() throws Exception {
        TenantwallConfig.setTenantStrategy("cookie");
        TenantwallConfig.setTenantCookieName("tenant_id");
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie("other", "x"),
                new Cookie("tenant_id", "TENANT_COOKIE")
        });

        assertThat(captureTenantDuringChain()).isEqualTo("TENANT_COOKIE");
    }

    @Test
    void cookie_missingCookie_noTenant() throws Exception {
        TenantwallConfig.setTenantStrategy("cookie");
        when(request.getCookies()).thenReturn(null);

        assertThat(captureTenantDuringChain()).isNull();
    }

    @Test
    void cookie_noMatchingName_noTenant() throws Exception {
        TenantwallConfig.setTenantStrategy("cookie");
        TenantwallConfig.setTenantCookieName("tenant_id");
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie("session", "x")
        });

        assertThat(captureTenantDuringChain()).isNull();
    }

    // ═══════════════════════════════════════════════════════════════
    // Strategy: jwt
    // ═══════════════════════════════════════════════════════════════

    @Test
    void jwt_resolvesFromClaim() throws Exception {
        TenantwallConfig.setTenantStrategy("jwt");
        TenantwallConfig.setTenantJwtClaim("tenant_id");
        TenantwallConfig.setTenantJwtHeader("Authorization");
        TenantwallConfig.setTenantJwtPrefix("Bearer ");

        String jwt = makeJwt("{\"tenant_id\":\"TENANT_JWT\",\"sub\":\"user1\"}");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + jwt);

        assertThat(captureTenantDuringChain()).isEqualTo("TENANT_JWT");
    }

    @Test
    void jwt_missingHeader_noTenant() throws Exception {
        TenantwallConfig.setTenantStrategy("jwt");
        when(request.getHeader("Authorization")).thenReturn(null);

        assertThat(captureTenantDuringChain()).isNull();
    }

    @Test
    void jwt_malformedToken_noTenant() throws Exception {
        TenantwallConfig.setTenantStrategy("jwt");
        TenantwallConfig.setTenantJwtPrefix("Bearer ");
        when(request.getHeader("Authorization")).thenReturn("Bearer not-a-jwt");

        assertThat(captureTenantDuringChain()).isNull();
    }

    @Test
    void jwt_missingClaim_noTenant() throws Exception {
        TenantwallConfig.setTenantStrategy("jwt");
        TenantwallConfig.setTenantJwtClaim("tenant_id");
        TenantwallConfig.setTenantJwtPrefix("Bearer ");

        String jwt = makeJwt("{\"sub\":\"user1\"}"); // no tenant_id claim
        when(request.getHeader("Authorization")).thenReturn("Bearer " + jwt);

        assertThat(captureTenantDuringChain()).isNull();
    }

    // ═══════════════════════════════════════════════════════════════
    // Lifecycle — ThreadLocal always cleared
    // ═══════════════════════════════════════════════════════════════

    @Test
    void contextAlwaysCleared_afterSuccess() throws Exception {
        TenantwallConfig.setTenantStrategy("header");
        when(request.getHeader("X-Tenant-Id")).thenReturn("TENANT_001");

        captureTenantDuringChain();

        assertThat(TenantContext.getTenant()).isNull();
    }

    @Test
    void contextAlwaysCleared_afterException() throws Exception {
        TenantwallConfig.setTenantStrategy("header");
        when(request.getHeader("X-Tenant-Id")).thenReturn("TENANT_001");

        FilterChain throwingChain = (req, res) -> {
            throw new RuntimeException("boom");
        };

        try {
            filter.doFilter(request, response, throwingChain);
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(TenantContext.getTenant()).isNull();
    }

    // ─── Helper: build an unsigned JWT ───
    private static String makeJwt(String payloadJson) {
        String header = b64("{\"alg\":\"none\"}");
        String payload = b64(payloadJson);
        return header + "." + payload + ".sig";
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes());
    }
}