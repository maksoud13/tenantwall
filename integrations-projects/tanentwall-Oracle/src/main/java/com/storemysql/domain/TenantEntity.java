package com.storeoracle.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tenantwall.agent.TenantContext;
import jakarta.persistence.*;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class TenantEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    @JsonIgnore
    private String tenantId;

    @PrePersist
    void ensureTenant() {
        if (tenantId == null || tenantId.isBlank()) {
            String t = TenantContext.getTenant();
            if (t == null || t.isBlank()) {
                throw new IllegalStateException(
                        "TenantContext is empty — set X-Tenant-Id header");
            }
            tenantId = t;
        }
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}