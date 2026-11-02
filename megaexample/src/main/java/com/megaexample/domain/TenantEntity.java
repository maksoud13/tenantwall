package com.megaexample.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tenantwall.agent.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

@MappedSuperclass
public abstract class TenantEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    @JsonIgnore
    private String tenantId;

    @PrePersist
    void ensureTenant() {
        if (tenantId == null || tenantId.isBlank()) {
            String t = TenantContext.getTenant();
            if (t != null && !t.isBlank()) {
                tenantId = t;
            }
            // Note: if TenantContext is null, we DON'T fail
            // (agent will inject tenant_id via SQL rewrite)
        }
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
