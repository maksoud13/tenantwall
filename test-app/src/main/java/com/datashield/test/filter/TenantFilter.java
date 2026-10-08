package com.datashield.test.filter;

import com.datashield.agent.TenantContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TenantFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            String tenantId = ((HttpServletRequest) request).getHeader("X-Tenant-Id");
            if (tenantId == null || tenantId.isEmpty()) {
                tenantId = "TENANT_A";
            }
            TenantContext.setTenant(tenantId);
            System.out.println("🔑 TenantFilter: Tenant set to " + tenantId);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            System.out.println("🧹 TenantFilter: Tenant cleared");
        }
    }
}