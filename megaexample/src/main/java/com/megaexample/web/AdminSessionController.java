package com.megaexample.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Test-only helper for the session tenant strategy.
 * Allows curl to set the tenant into the HTTP session without a browser.
 *
 * <p>DO NOT ship this to production — it lets any caller pick a tenant.
 * Guard it with a profile if you keep it around.
 */
@RestController
@RequestMapping("/api/admin/session")
public class AdminSessionController {

    @PostMapping("/{tenant}")
    public Map<String, Object> setTenant(@PathVariable String tenant, HttpSession session) {
        session.setAttribute("TENANT_ID", tenant);
        return Map.of("ok", true, "tenant", tenant, "sessionId", session.getId());
    }

    @GetMapping
    public Map<String, Object> getTenant(HttpSession session) {
        Object v = session.getAttribute("TENANT_ID");
        return Map.of("tenant", v == null ? "null" : v.toString());
    }
}