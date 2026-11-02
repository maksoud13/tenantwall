package com.megaexample.web;

import com.tenantwall.agent.SmartSqlModifier;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "Admin", description = "TenantWall agent stats")
@RestController
@RequestMapping("/api/admin")
public class StatsController {

    @GetMapping("/stats")
    public Map<String, Object> allStats() {
        var cacheStats = SmartSqlModifier.getCacheStats();

        Map<String, Object> result = new HashMap<>();

        // Cache
        result.put("cache.hits", cacheStats.hitCount());
        result.put("cache.misses", cacheStats.missCount());
        result.put("cache.hitRate", cacheStats.hitRate());
        result.put("cache.size", SmartSqlModifier.getCacheSize());
        result.put("cache.evictions", cacheStats.evictionCount());
        result.put("cache.loadTimeMicros", cacheStats.totalLoadTime());

        // Routing
        result.put("routing.pythonCalls", SmartSqlModifier.getPythonCallCount());
        result.put("routing.localCalls", SmartSqlModifier.getLocalCallCount());
        result.put("routing.fallbacks", SmartSqlModifier.getFallbackCount());

        return result;
    }

     @PostMapping("/reset-counters")
    public Map<String, String> resetCounters() {
        SmartSqlModifier.resetCounters();
        return Map.of("status", "ok", "message", "Counters reset (cache preserved)");
    }

    @PostMapping("/reset-stats")
    public Map<String, String> reset() {
        SmartSqlModifier.resetStats();
        return Map.of("status", "ok", "message", "Full reset (cache cleared)");
    }
}
