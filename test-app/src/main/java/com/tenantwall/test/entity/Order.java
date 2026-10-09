package com.tenantwall.test.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productName;
    private BigDecimal total;

    @Column(name = "order_status")
    private String status; // PENDING, PROCESSED, SHIPPED, CANCELLED

    @Column(name = "tenant_id")
    private String tenantId;

    // العلاقة مع العميل
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // Constructors, Getters, Setters...
    public Order() {
    }

    public Order(String productName, BigDecimal total, String status, String tenantId, Customer customer) {
        this.productName = productName;
        this.total = total;
        this.status = status;
        this.tenantId = tenantId;
        this.customer = customer;
    }

    public Order(String tenant, Long id2, double randomDouble) {
        this.productName = productName;
        this.total = total;
        this.status = status;
        this.tenantId = tenantId;
        this.customer = customer;
    }

    // ... (جميع الـ Getters و Setters)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }
}