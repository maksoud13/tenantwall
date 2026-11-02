-- ═══════════════════════════════════════════════════════════════
--  TenantWall — megaexample Schema (30 tables)
--  Target: MySQL 8.0
-- ═══════════════════════════════════════════════════════════════

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ═══════════════════════════════════════════════════════════════
--  GROUP 1: LOOKUP TABLES (NO tenant_id — EXCLUDED from agent)
-- ═══════════════════════════════════════════════════════════════

DROP TABLE IF EXISTS countries;
CREATE TABLE countries (
    id       INT PRIMARY KEY,
    iso_code CHAR(2) NOT NULL UNIQUE,
    name     VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

DROP TABLE IF EXISTS currencies;
CREATE TABLE currencies (
    id       INT PRIMARY KEY,
    iso_code CHAR(3) NOT NULL UNIQUE,
    name     VARCHAR(50) NOT NULL,
    symbol   VARCHAR(10)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS payment_methods;
CREATE TABLE payment_methods (
    id   INT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL
) ENGINE=InnoDB;

DROP TABLE IF EXISTS shipping_carriers;
CREATE TABLE shipping_carriers (
    id           INT PRIMARY KEY,
    code         VARCHAR(20) NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    tracking_url VARCHAR(255)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS product_attributes;
CREATE TABLE product_attributes (
    id   INT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL
) ENGINE=InnoDB;

DROP TABLE IF EXISTS roles;
CREATE TABLE roles (
    id   INT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL
) ENGINE=InnoDB;

DROP TABLE IF EXISTS permissions;
CREATE TABLE permissions (
    id   INT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

-- ═══════════════════════════════════════════════════════════════
--  GROUP 2: TENANT CORE (9 tables, WITH tenant_id)
-- ═══════════════════════════════════════════════════════════════

DROP TABLE IF EXISTS users;
CREATE TABLE users (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id     VARCHAR(50) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    role_id       INT NOT NULL,
    country_id    INT,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_users_tenant_email (tenant_id, email),
    INDEX idx_users_tenant (tenant_id),
    INDEX idx_users_role (tenant_id, role_id),
    FOREIGN KEY (role_id) REFERENCES roles(id),
    FOREIGN KEY (country_id) REFERENCES countries(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS addresses;
CREATE TABLE addresses (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id   VARCHAR(50) NOT NULL,
    customer_id BIGINT NULL,
    line1       VARCHAR(255) NOT NULL,
    line2       VARCHAR(255),
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100),
    postal_code VARCHAR(20),
    country_id  INT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_addr_tenant (tenant_id),
    INDEX idx_addr_tenant_customer (tenant_id, customer_id),
    FOREIGN KEY (country_id) REFERENCES countries(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS categories;
CREATE TABLE categories (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id  VARCHAR(50) NOT NULL,
    parent_id  BIGINT NULL,
    name       VARCHAR(200) NOT NULL,
    slug       VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cat_tenant (tenant_id),
    INDEX idx_cat_tenant_parent (tenant_id, parent_id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS suppliers;
CREATE TABLE suppliers (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id  VARCHAR(50) NOT NULL,
    name       VARCHAR(200) NOT NULL,
    email      VARCHAR(255),
    phone      VARCHAR(50),
    country_id INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sup_tenant (tenant_id),
    FOREIGN KEY (country_id) REFERENCES countries(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS products;
CREATE TABLE products (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id   VARCHAR(50) NOT NULL,
    category_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    sku         VARCHAR(100) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    base_price  DECIMAL(12,2) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_prod_tenant_sku (tenant_id, sku),
    INDEX idx_prod_tenant (tenant_id),
    INDEX idx_prod_tenant_cat (tenant_id, category_id),
    INDEX idx_prod_tenant_sup (tenant_id, supplier_id),
    INDEX idx_prod_tenant_active (tenant_id, active),
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS product_variants;
CREATE TABLE product_variants (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id    VARCHAR(50) NOT NULL,
    product_id   BIGINT NOT NULL,
    sku          VARCHAR(100) NOT NULL,
    variant_name VARCHAR(200) NOT NULL,
    price        DECIMAL(12,2) NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_var_tenant_sku (tenant_id, sku),
    INDEX idx_var_tenant (tenant_id),
    INDEX idx_var_tenant_product (tenant_id, product_id),
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS warehouses;
CREATE TABLE warehouses (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id  VARCHAR(50) NOT NULL,
    name       VARCHAR(200) NOT NULL,
    country_id INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wh_tenant (tenant_id),
    FOREIGN KEY (country_id) REFERENCES countries(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS inventory;
CREATE TABLE inventory (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id         VARCHAR(50) NOT NULL,
    variant_id        BIGINT NOT NULL,
    warehouse_id      BIGINT NOT NULL,
    quantity_on_hand  INT NOT NULL DEFAULT 0,
    quantity_reserved INT NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_inv_variant_wh (tenant_id, variant_id, warehouse_id),
    INDEX idx_inv_tenant (tenant_id),
    INDEX idx_inv_tenant_variant (tenant_id, variant_id),
    FOREIGN KEY (variant_id) REFERENCES product_variants(id),
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
) ENGINE=InnoDB;

-- ═══════════════════════════════════════════════════════════════
--  GROUP 3: COMMERCE (7 tables, WITH tenant_id)
-- ═══════════════════════════════════════════════════════════════

DROP TABLE IF EXISTS customers;
CREATE TABLE customers (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id  VARCHAR(50) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    full_name  VARCHAR(200) NOT NULL,
    phone      VARCHAR(50),
    country_id INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_cust_tenant_email (tenant_id, email),
    INDEX idx_cust_tenant (tenant_id),
    INDEX idx_cust_tenant_created (tenant_id, created_at),
    FOREIGN KEY (country_id) REFERENCES countries(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS carts;
CREATE TABLE carts (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id   VARCHAR(50) NOT NULL,
    customer_id BIGINT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_carts_tenant (tenant_id),
    INDEX idx_carts_tenant_customer (tenant_id, customer_id),
    INDEX idx_carts_tenant_status (tenant_id, status),
    FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS cart_items;
CREATE TABLE cart_items (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id  VARCHAR(50) NOT NULL,
    cart_id    BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity   INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    INDEX idx_citems_tenant (tenant_id),
    INDEX idx_citems_tenant_cart (tenant_id, cart_id),
    FOREIGN KEY (cart_id) REFERENCES carts(id),
    FOREIGN KEY (variant_id) REFERENCES product_variants(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id           VARCHAR(50) NOT NULL,
    customer_id         BIGINT NOT NULL,
    order_number        VARCHAR(50) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    subtotal            DECIMAL(12,2) NOT NULL,
    tax                 DECIMAL(12,2) NOT NULL,
    shipping_cost       DECIMAL(12,2) NOT NULL,
    total               DECIMAL(12,2) NOT NULL,
    currency_id         INT NOT NULL,
    shipping_address_id BIGINT,
    placed_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_orders_tenant_number (tenant_id, order_number),
    INDEX idx_orders_tenant (tenant_id),
    INDEX idx_orders_tenant_customer (tenant_id, customer_id),
    INDEX idx_orders_tenant_status (tenant_id, status),
    INDEX idx_orders_tenant_placed (tenant_id, placed_at),
    FOREIGN KEY (customer_id) REFERENCES customers(id),
    FOREIGN KEY (currency_id) REFERENCES currencies(id),
    FOREIGN KEY (shipping_address_id) REFERENCES addresses(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS order_items;
CREATE TABLE order_items (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id  VARCHAR(50) NOT NULL,
    order_id   BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity   INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,
    INDEX idx_oitems_tenant (tenant_id),
    INDEX idx_oitems_tenant_order (tenant_id, order_id),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (variant_id) REFERENCES product_variants(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS order_status_history;
CREATE TABLE order_status_history (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id   VARCHAR(50) NOT NULL,
    order_id    BIGINT NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    changed_by  BIGINT,
    changed_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes       VARCHAR(500),
    INDEX idx_osh_tenant (tenant_id),
    INDEX idx_osh_tenant_order (tenant_id, order_id),
    INDEX idx_osh_tenant_changed (tenant_id, changed_at),
    FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS coupons;
CREATE TABLE coupons (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id      VARCHAR(50) NOT NULL,
    code           VARCHAR(50) NOT NULL,
    discount_type  VARCHAR(20) NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL,
    max_uses       INT,
    valid_from     TIMESTAMP NOT NULL,
    valid_until    TIMESTAMP NOT NULL,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY uq_coup_tenant_code (tenant_id, code),
    INDEX idx_coup_tenant (tenant_id),
    INDEX idx_coup_tenant_active (tenant_id, active)
) ENGINE=InnoDB;

-- ═══════════════════════════════════════════════════════════════
--  GROUP 4: FINANCIAL & INFO (7 tables, WITH tenant_id)
-- ═══════════════════════════════════════════════════════════════

DROP TABLE IF EXISTS payments;
CREATE TABLE payments (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id   VARCHAR(50) NOT NULL,
    order_id    BIGINT NOT NULL,
    method_id   INT NOT NULL,
    amount      DECIMAL(12,2) NOT NULL,
    currency_id INT NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pay_tenant (tenant_id),
    INDEX idx_pay_tenant_order (tenant_id, order_id),
    INDEX idx_pay_tenant_status (tenant_id, status),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (method_id) REFERENCES payment_methods(id),
    FOREIGN KEY (currency_id) REFERENCES currencies(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS payment_transactions;
CREATE TABLE payment_transactions (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id        VARCHAR(50) NOT NULL,
    payment_id       BIGINT NOT NULL,
    transaction_ref  VARCHAR(100) NOT NULL,
    amount           DECIMAL(12,2) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    gateway_response TEXT,
    attempted_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ptx_tenant_ref (tenant_id, transaction_ref),
    INDEX idx_ptx_tenant (tenant_id),
    INDEX idx_ptx_tenant_payment (tenant_id, payment_id),
    INDEX idx_ptx_tenant_status (tenant_id, status),
    INDEX idx_ptx_tenant_time (tenant_id, attempted_at),
    FOREIGN KEY (payment_id) REFERENCES payments(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS refunds;
CREATE TABLE refunds (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id  VARCHAR(50) NOT NULL,
    payment_id BIGINT NOT NULL,
    amount     DECIMAL(12,2) NOT NULL,
    reason     VARCHAR(255),
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ref_tenant (tenant_id),
    INDEX idx_ref_tenant_payment (tenant_id, payment_id),
    FOREIGN KEY (payment_id) REFERENCES payments(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS invoices;
CREATE TABLE invoices (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id      VARCHAR(50) NOT NULL,
    order_id       BIGINT NOT NULL,
    invoice_number VARCHAR(50) NOT NULL,
    issued_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_at         TIMESTAMP NULL,
    total          DECIMAL(12,2) NOT NULL,
    currency_id    INT NOT NULL,
    status         VARCHAR(20) NOT NULL,
    UNIQUE KEY uq_inv_tenant_number (tenant_id, invoice_number),
    INDEX idx_inv_tenant (tenant_id),
    INDEX idx_inv_tenant_order (tenant_id, order_id),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (currency_id) REFERENCES currencies(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS invoice_lines;
CREATE TABLE invoice_lines (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id   VARCHAR(50) NOT NULL,
    invoice_id  BIGINT NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity    INT NOT NULL,
    unit_price  DECIMAL(12,2) NOT NULL,
    line_total  DECIMAL(12,2) NOT NULL,
    INDEX idx_il_tenant (tenant_id),
    INDEX idx_il_tenant_invoice (tenant_id, invoice_id),
    FOREIGN KEY (invoice_id) REFERENCES invoices(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS coupon_redemptions;
CREATE TABLE coupon_redemptions (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id       VARCHAR(50) NOT NULL,
    coupon_id       BIGINT NOT NULL,
    customer_id     BIGINT NOT NULL,
    order_id        BIGINT NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL,
    redeemed_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cr_tenant (tenant_id),
    INDEX idx_cr_tenant_coupon (tenant_id, coupon_id),
    FOREIGN KEY (coupon_id) REFERENCES coupons(id),
    FOREIGN KEY (customer_id) REFERENCES customers(id),
    FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS audit_logs;
CREATE TABLE audit_logs (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id   VARCHAR(50) NOT NULL,
    user_id     BIGINT,
    entity_type VARCHAR(50) NOT NULL,
    entity_id   BIGINT NOT NULL,
    action      VARCHAR(30) NOT NULL,
    changes     JSON,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_tenant (tenant_id),
    INDEX idx_audit_tenant_entity (tenant_id, entity_type, entity_id),
    INDEX idx_audit_tenant_time (tenant_id, occurred_at),
    INDEX idx_audit_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB;

SET FOREIGN_KEY_CHECKS = 1;
