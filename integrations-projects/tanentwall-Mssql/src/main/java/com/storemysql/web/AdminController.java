package com.storeMSsql.web;

import com.tenantwall.agent.SmartSqlModifier;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/cache-stats")
    public Map<String, Object> cacheStats() {
        var s = SmartSqlModifier.getCacheStats();
        Map<String, Object> r = new HashMap<>();
        r.put("hits", s.hitCount());
        r.put("misses", s.missCount());
        r.put("hitRate", String.format("%.2f%%", s.hitRate() * 100));
        r.put("size", SmartSqlModifier.getCacheSize());
        return r;
    }

    @GetMapping("/smart-stats")
    public Map<String, Long> smartStats() {
        Map<String, Long> r = new HashMap<>();
        r.put("pythonCalls", SmartSqlModifier.getPythonCallCount());
        r.put("localCalls", SmartSqlModifier.getLocalCallCount());
        r.put("fallbacks", SmartSqlModifier.getFallbackCount());
        return r;
    }

    @PostMapping("/cache-reset")
    public String reset() {
        SmartSqlModifier.resetStats();
        return "ok";
    }
}