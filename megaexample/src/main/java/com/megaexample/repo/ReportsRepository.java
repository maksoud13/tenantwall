package com.megaexample.repo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class ReportsRepository {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> categoryRevenue() {
        String sql = """
            WITH cat_revenue AS (
                SELECT p.category_id,
                       SUM(oi.line_total) AS revenue,
                       SUM(oi.quantity)   AS units
                FROM order_items oi
                JOIN products p ON p.id = oi.variant_id
                GROUP BY p.category_id
            )
            SELECT c.name AS category,
                   cr.revenue AS revenue,
                   cr.units   AS units,
                   cur.symbol AS symbol
            FROM cat_revenue cr
            JOIN categories c ON c.id = cr.category_id
            CROSS JOIN currencies cur
            WHERE cur.iso_code = 'USD'
            ORDER BY cr.revenue DESC
            LIMIT 20
            """;
        Query q = em.createNativeQuery(sql);
        return q.getResultList();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> customerLtv() {
        String sql = """
            WITH cust_stats AS (
                SELECT o.customer_id,
                       COUNT(*)     AS order_count,
                       SUM(o.total) AS total_spent
                FROM orders o
                WHERE o.status IN ('DELIVERED', 'SHIPPED')
                GROUP BY o.customer_id
            ),
            cust_paid AS (
                SELECT p.order_id, SUM(pt.amount) AS paid
                FROM payments p
                JOIN payment_transactions pt ON pt.payment_id = p.id
                WHERE pt.status = 'CAPTURED'
                GROUP BY p.order_id
            )
            SELECT c.full_name,
                   c.email,
                   cn.name AS country,
                   cs.order_count,
                   cs.total_spent,
                   COALESCE(SUM(cp.paid), 0) AS total_paid
            FROM customers c
            JOIN countries cn ON cn.id = c.country_id
            JOIN cust_stats cs ON cs.customer_id = c.id
            LEFT JOIN cust_paid cp ON cp.order_id IN (
                SELECT id FROM orders WHERE customer_id = c.id
            )
            GROUP BY c.id, c.full_name, c.email, cn.name, cs.order_count, cs.total_spent
            ORDER BY cs.total_spent DESC
            LIMIT 10
            """;
        Query q = em.createNativeQuery(sql);
        return q.getResultList();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> lowStock() {
        String sql = """
            WITH var_stock AS (
                SELECT i.variant_id,
                       SUM(i.quantity_on_hand)  AS on_hand,
                       SUM(i.quantity_reserved) AS reserved
                FROM inventory i
                GROUP BY i.variant_id
            )
            SELECT p.name,
                   pv.variant_name,
                   pv.sku,
                   c.name AS category,
                   s.name AS supplier,
                   cn.name AS country,
                   vs.on_hand,
                   vs.reserved,
                   (vs.on_hand - vs.reserved) AS available
            FROM var_stock vs
            JOIN product_variants pv ON pv.id = vs.variant_id
            JOIN products p         ON p.id  = pv.product_id
            JOIN categories c       ON c.id  = p.category_id
            JOIN suppliers s        ON s.id  = p.supplier_id
            JOIN inventory inv      ON inv.variant_id = pv.id
            JOIN warehouses w       ON w.id = inv.warehouse_id
            JOIN countries cn       ON cn.id = w.country_id
            WHERE (vs.on_hand - vs.reserved) < 50
            ORDER BY available ASC
            LIMIT 20
            """;
        Query q = em.createNativeQuery(sql);
        return q.getResultList();
    }
}