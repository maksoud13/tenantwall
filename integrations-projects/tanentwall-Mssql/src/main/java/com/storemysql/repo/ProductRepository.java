// ProductRepository.java
package com.storeMSsql.repo;

import com.storeMSsql.domain.Product;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Override
    @EntityGraph(attributePaths = { "category", "supplier" })
    @NonNull
    List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = { "category", "supplier" })
    @NonNull
    Optional<Product> findById(@NonNull Long id);

    // CTE — will go through Python parser
    @Query(value = """
            WITH per_cat AS (
                SELECT category_id, COUNT(*) AS cnt, SUM(price * stock_quantity) AS stock_value
                FROM products
                GROUP BY category_id
            )
            SELECT c.name, p.cnt, p.stock_value
            FROM categories c
            JOIN per_cat p ON p.category_id = c.id
            ORDER BY p.stock_value DESC
            """, nativeQuery = true)
    List<Object[]> stockValueReport();
}