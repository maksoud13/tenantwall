package com.megaexample.repo;

import com.megaexample.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    Page<Product> findByActive(Boolean active, Pageable pageable);

    /**
     * Complex CTE — forces Python parser
     */
    @Query(value = """
        WITH per_cat AS (
            SELECT category_id, COUNT(*) AS cnt,
                   SUM(base_price * 10) AS stock_value
            FROM products
            WHERE active = 1
            GROUP BY category_id
        )
        SELECT c.name, p.cnt, p.stock_value
        FROM categories c
        JOIN per_cat p ON p.category_id = c.id
        ORDER BY p.stock_value DESC
        LIMIT 20
        """, nativeQuery = true)
    List<Object[]> stockValueReport();

    /**
     * Window function — forces Python parser
     */
    @Query(value = """
        WITH ranked AS (
            SELECT id, category_id, base_price,
                   ROW_NUMBER() OVER (PARTITION BY category_id ORDER BY base_price DESC) AS rn
            FROM products
        )
        SELECT c.name, COUNT(*) AS top_count, AVG(r.base_price) AS avg_price
        FROM categories c
        JOIN ranked r ON r.category_id = c.id
        WHERE r.rn <= 10
        GROUP BY c.name
        ORDER BY top_count DESC
        """, nativeQuery = true)
    List<Object[]> topCategoriesReport();
}
