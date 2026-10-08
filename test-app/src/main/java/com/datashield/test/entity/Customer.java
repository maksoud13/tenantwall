package com.datashield.test.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String level; // GOLD, SILVER, BRONZE

    @Column(name = "tenant_id")
    private String tenantId;

    // علاقة: العميل عنده مجموعة طلبات (Orders)
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Order> orders = new ArrayList<>();

    // Constructors, Getters, Setters...
    public Customer() {
    }

    public Customer(String name, String email, String level, String tenantId) {
        this.name = name;
        this.email = email;
        this.level = level;
        this.tenantId = tenantId;
    }

    public Customer(String fullName, String tenant, String cellPhone) {
        this.name = name;
        this.email = email;
        this.level = level;
    }

    // ... (جميع الـ Getters و Setters)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public void setOrders(List<Order> orders) {
        this.orders = orders;
    }
}