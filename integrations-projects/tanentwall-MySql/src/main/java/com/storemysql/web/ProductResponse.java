package com.storemysql.web;

import com.storemysql.domain.Product;

public record ProductResponse(
        Long id, String name, String sku,
        java.math.BigDecimal price, Integer stockQuantity,
        Long categoryId, String categoryName,
        Long supplierId, String supplierName
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(), p.getName(), p.getSku(), p.getPrice(), p.getStockQuantity(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getSupplier() != null ? p.getSupplier().getId() : null,
                p.getSupplier() != null ? p.getSupplier().getName() : null
        );
    }
}