package com.storemysql.web;

import com.storemysql.domain.Category;
import com.storemysql.domain.Product;
import com.storemysql.domain.Supplier;
import com.storemysql.repo.CategoryRepository;
import com.storemysql.repo.ProductRepository;
import com.storemysql.repo.SupplierRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "Products")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final SupplierRepository suppliers;

    public ProductController(ProductRepository products,
            CategoryRepository categories,
            SupplierRepository suppliers) {
        this.products = products;
        this.categories = categories;
        this.suppliers = suppliers;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return products.findAll().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductResponse> get(@PathVariable Long id) {
        return products.findById(id).map(ProductResponse::from)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ProductResponse create(@RequestBody Map<String, Object> body) {
        Product p = new Product();
        p.setName((String) body.get("name"));
        p.setSku((String) body.get("sku"));
        p.setPrice(new BigDecimal(body.get("price").toString()));
        p.setStockQuantity((Integer) body.get("stockQuantity"));

        if (body.get("categoryId") != null) {
            p.setCategory(categories.findById(((Number) body.get("categoryId")).longValue()).orElse(null));
        }
        if (body.get("supplierId") != null) {
            p.setSupplier(suppliers.findById(((Number) body.get("supplierId")).longValue()).orElse(null));
        }
        return ProductResponse.from(products.save(p));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ProductResponse> update(@PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return products.findById(id).map(p -> {
            if (body.containsKey("name"))
                p.setName((String) body.get("name"));
            if (body.containsKey("price"))
                p.setPrice(new BigDecimal(body.get("price").toString()));
            if (body.containsKey("stockQuantity"))
                p.setStockQuantity((Integer) body.get("stockQuantity"));
            return ResponseEntity.ok(ProductResponse.from(products.save(p)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!products.existsById(id))
            return ResponseEntity.notFound().build();
        products.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reports/stock-value")
    public List<Object[]> stockValue() {
        return products.stockValueReport();
    }
}