package com.storeMSsql.config;

import com.tenantwall.agent.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        String tenant = ((HttpServletRequest) req).getHeader("X-Tenant-Id");
        try {
            if (tenant != null && !tenant.isBlank()) {
                TenantContext.setTenant(tenant);
            }
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }
}