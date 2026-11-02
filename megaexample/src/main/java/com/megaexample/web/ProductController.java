package com.megaexample.web;

import com.megaexample.domain.Product;
import com.megaexample.repo.ProductRepository;
import com.tenantwall.agent.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Tag(name = "Products", description = "OLTP + OLAP load-test endpoints")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository products;

    public ProductController(ProductRepository products) {
        this.products = products;
    }

    /**
     * Point lookup.
     *
     * Response codes (TEST DIAGNOSTIC MODE):
     * - 200 OK            → product visible to this tenant
     * - 403 ISOLATED      → product exists but belongs to ANOTHER tenant (isolation WORKING)
     * - 404 NOT_FOUND     → product doesn't exist in any tenant
     *
     * ⚠️ In production, return 404 for BOTH 403 and 404 cases
     * (to avoid leaking that the resource exists elsewhere).
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> byId(@PathVariable Long id) {
        Optional<Product> visible = products.findById(id);

        if (visible.isPresent()) {
            return ResponseEntity.ok(visible.get());
        }

        // Not visible to current tenant. Was it filtered, or doesn't exist?
        if (existsAcrossTenants(id)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "CROSS_TENANT_ISOLATED",
                    "productId", id,
                    "isolation", "WORKING",
                    "note", "TEST MODE: in production use 404 for both cases"
            ));
        }

        return ResponseEntity.status(404).body(Map.of(
                "error", "NOT_FOUND",
                "productId", id
        ));
    }

    /**
     * Checks if a product exists in ANY tenant (bypassing the agent's filter).
     * Uses TenantContext.clear() + onMissing=skip to query without WHERE tenant_id.
     */
    private boolean existsAcrossTenants(Long id) {
        String saved = TenantContext.getTenant();
        TenantContext.clear();
        try {
            return products.existsById(id);
        } finally {
            if (saved != null && !saved.isEmpty()) {
                TenantContext.setTenant(saved);
            }
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Page<Product> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return products.findAll(PageRequest.of(page, size));
    }

    @GetMapping("/by-sku/{sku}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> bySku(@PathVariable String sku) {
        return products.findBySku(sku)
                .map(p -> ResponseEntity.ok((Object) p))
                .orElse(ResponseEntity.status(404).body(Map.of(
                        "error", "NOT_FOUND",
                        "sku", sku
                )));
    }

    @GetMapping("/active")
    @Transactional(readOnly = true)
    public Page<Product> active(
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return products.findByActive(active, PageRequest.of(page, size));
    }

    @GetMapping("/reports/stock-value")
    public List<Object[]> stockValue() {
        return products.stockValueReport();
    }

    @GetMapping("/reports/top-categories")
    public List<Object[]> topCategories() {
        return products.topCategoriesReport();
    }
}
