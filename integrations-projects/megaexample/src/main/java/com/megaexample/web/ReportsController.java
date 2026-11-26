package com.megaexample.web;

import com.megaexample.repo.ReportsRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "reports", description = "reports using complex and cte's")
@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final ReportsRepository reports;

    public ReportsController(ReportsRepository reports) {
        this.reports = reports;
    }

    @GetMapping("/category-revenue")
    public ResponseEntity<?> categoryRevenue() {
        List<?> rows = reports.categoryRevenue();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/customer-ltv")
    public ResponseEntity<?> customerLtv() {
        List<?> rows = reports.customerLtv();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/low-stock")
    public ResponseEntity<?> lowStock() {
        List<?> rows = reports.lowStock();
        return ResponseEntity.ok(rows);
    }
}