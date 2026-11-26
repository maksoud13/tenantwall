package com.megaexample.web;

import com.tenantwall.agent.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "tenant", TenantContext.getTenant() != null ? TenantContext.getTenant() : "none",
                "timestamp", System.currentTimeMillis()
        );
    }
}
