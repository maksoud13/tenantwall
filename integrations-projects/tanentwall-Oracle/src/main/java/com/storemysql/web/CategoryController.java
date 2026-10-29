package com.storeoracle.web;

import com.storeoracle.domain.Category;
import com.storeoracle.domain.Product;
import com.storeoracle.domain.Supplier;
import com.storeoracle.repo.CategoryRepository;
import com.storeoracle.repo.ProductRepository;
import com.storeoracle.repo.SupplierRepository;
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