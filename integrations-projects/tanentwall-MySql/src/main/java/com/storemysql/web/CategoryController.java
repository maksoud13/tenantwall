package com.storemysql.web;

import com.storemysql.domain.Category;
import com.storemysql.domain.Product;
import com.storemysql.domain.Supplier;
import com.storemysql.repo.CategoryRepository;
import com.storemysql.repo.ProductRepository;
import com.storemysql.repo.SupplierRepository;
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