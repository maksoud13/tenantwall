package com.tenantwall.starter;

import com.tenantwall.agent.TenantContext;
import com.tenantwall.agent.TenantwallConfig;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Built-in tenant resolution filter.
 *
 * <p>Reads tenant ID from the configured source (header, fixed value, session, cookie)
 * and sets it in {@link TenantContext} for the current request thread.
 *
 * <p>Always clears the context in a {@code finally} block to prevent
 * thread-pool reuse from leaking tenant context between requests.
 *
 * <p><b>Zero user code required.</b> This filter is auto-registered by
 * {@link TenantWallAutoConfiguration} when the app starts.
 */
public class TenantWallFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) req;
        String tenant = resolveTenant(httpReq);

        try {
            if (tenant != null && !tenant.isBlank()) {
                TenantContext.setTenant(tenant);
            }
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();  // Critical: prevent context leak
        }
    }

    private String resolveTenant(HttpServletRequest req) {
        String strategy = TenantwallConfig.getTenantStrategy();

        return switch (strategy) {
            case "header"  -> req.getHeader(TenantwallConfig.getTenantHeaderName());
            case "fixed"   -> TenantwallConfig.getTenantFixedValue();
            case "session" -> resolveFromSession(req);
            case "cookie"  -> resolveFromCookie(req);
            default        -> null;
        };
    }

    private String resolveFromSession(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return null;
        Object value = session.getAttribute(TenantwallConfig.getTenantSessionKey());
        return value != null ? value.toString() : null;
    }

    private String resolveFromCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;

        String cookieName = TenantwallConfig.getTenantCookieName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
