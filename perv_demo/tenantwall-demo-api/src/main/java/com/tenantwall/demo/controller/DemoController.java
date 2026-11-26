package com.tenantwall.demo.controller;

import com.tenantwall.demo.model.ModifyRequest;
import com.tenantwall.demo.model.ModifyResponse;
import com.tenantwall.demo.service.QueryAnalyzer;
import com.tenantwall.demo.service.SmartSqlModifier;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class DemoController {

    @GetMapping("/")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "ok");
        health.put("service", "TenantWall Demo API");
        health.put("version", "1.0.0");
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }

    @PostMapping("/api/modify")
    public ModifyResponse modify(@RequestBody ModifyRequest req) {
        long start = System.currentTimeMillis();

        try {
            if (req.sql == null || req.sql.trim().isEmpty()) {
                return ModifyResponse.fail("SQL query is empty");
            }
            if (req.value == null || req.value.trim().isEmpty()) {
                return ModifyResponse.fail("Filter value is empty");
            }

            boolean isComplex = QueryAnalyzer.isComplex(req.sql);
            String engine = isComplex ? "python" : "local";

            String result = SmartSqlModifier.modify(req.sql, req.column, req.value);
            long elapsed = System.currentTimeMillis() - start;

            ModifyResponse response = ModifyResponse.ok(result, engine, elapsed);
            System.out.println("[" + engine + "] " + elapsed + "ms: " + req.sql);
            return response;

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            return ModifyResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/api/stats")
    public Map<String, Object> stats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", SmartSqlModifier.getCacheSize());
        stats.put("pythonUrl", System.getenv("TENANTWALL_PARSER_URL"));
        return stats;
    }
}