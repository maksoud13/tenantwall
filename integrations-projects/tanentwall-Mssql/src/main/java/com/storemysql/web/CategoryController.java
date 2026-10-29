package com.storeMSsql.web;

import com.storeMSsql.domain.Category;
import com.storeMSsql.domain.Product;
import com.storeMSsql.domain.Supplier;
import com.storeMSsql.repo.CategoryRepository;
import com.storeMSsql.repo.ProductRepository;
import com.storeMSsql.repo.SupplierRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@Tag(name = "Categories")
@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryRepository repo;
    public CategoryController(CategoryRepository repo) { this.repo = repo; }

    @GetMapping
    public List<Category> list() { return repo.findAll(); }

    @PostMapping
    public Category create(@RequestBody Category c) { return repo.save(c); }
}