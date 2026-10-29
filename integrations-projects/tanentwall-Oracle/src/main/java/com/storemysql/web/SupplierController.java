package com.storeoracle.web;

import com.storeoracle.domain.Supplier;
import com.storeoracle.repo.SupplierRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Suppliers")
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierRepository suppliers;

    public SupplierController(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Supplier> list() {
        return suppliers.findAll(); // ← Hibernate findByAll → agent يفلتر تلقائي
    }
}