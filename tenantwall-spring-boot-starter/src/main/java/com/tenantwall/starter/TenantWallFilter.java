package com.tenantwall.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Built-in tenant resolution filter.
 *
 * <p>
 * Reads tenant ID from the configured source (header, fixed value, session,
 * cookie, or JWT claim) and sets it in {@link TenantContext} for the current
 * request thread.
 *
 * <p>
 * Always clears the context in a {@code finally} block to prevent
 * thread-pool reuse from leaking tenant context between requests.
 *
 * <p>
 * <b>Zero user code required.</b> This filter is auto-registered by
 * {@link TenantWallAutoConfiguration} when the app starts.
 *
 * <p>
 * <b>JWT note:</b> For {@code strategy=jwt}, this filter <em>decodes</em> the
 * JWT payload but does <em>not</em> verify the signature. Signature
 * verification
 * is expected to happen upstream (API gateway, Spring Security, etc.).
 */
public class TenantWallFilter implements Filter {

    private static final ObjectMapper JSON = new ObjectMapper();

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
            TenantContext.clear(); // Critical: prevent context leak
        }
    }

    private String resolveTenant(HttpServletRequest req) {
        String strategy = TenantwallConfig.getTenantStrategy();

        return switch (strategy) {
            case "header" -> req.getHeader(TenantwallConfig.getTenantHeaderName());
            case "fixed" -> TenantwallConfig.getTenantFixedValue();
            case "session" -> resolveFromSession(req);
            case "cookie" -> resolveFromCookie(req);
            case "jwt" -> resolveFromJwt(req);
            default -> null;
        };
    }

    private String resolveFromSession(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null)
            return null;
        Object value = session.getAttribute(TenantwallConfig.getTenantSessionKey());
        return value != null ? value.toString() : null;
    }

    private String resolveFromCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null)
            return null;

        String cookieName = TenantwallConfig.getTenantCookieName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Extracts the tenant claim from a JWT in the configured header.
     * <b>Decode-only</b> — no signature verification. Trust must come from an
     * upstream gateway / Spring Security.
     */
    private String resolveFromJwt(HttpServletRequest req) {
        String headerName = TenantwallConfig.getTenantJwtHeader();
        String raw = req.getHeader(headerName);
        if (raw == null || raw.isBlank())
            return null;

        String prefix = TenantwallConfig.getTenantJwtPrefix();
        if (prefix != null && !prefix.isBlank() && raw.startsWith(prefix)) {
            raw = raw.substring(prefix.length()).trim();
        }

        String[] parts = raw.split("\\.");
        if (parts.length < 2)
            return null; // not a JWT

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = JSON.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            JsonNode claim = payload.get(TenantwallConfig.getTenantJwtClaim());
            return (claim == null || claim.isNull()) ? null : claim.asText();
        } catch (Exception e) {
            System.err.println("[TenantWall] JWT decode failed: " + e.getMessage());
            return null;
        }
    }
}