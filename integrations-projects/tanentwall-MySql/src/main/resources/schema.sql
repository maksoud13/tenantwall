CREATE TABLE IF NOT EXISTS categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100),
    tenant_id VARCHAR(50) NOT NULL,
    INDEX idx_cat_tenant (tenant_id)
);

CREATE TABLE IF NOT EXISTS suppliers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(150),
    phone VARCHAR(50),
    tenant_id VARCHAR(50) NOT NULL,
    INDEX idx_sup_tenant (tenant_id)
);

CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    category_id BIGINT,
    supplier_id BIGINT,
    tenant_id VARCHAR(50) NOT NULL,
    INDEX idx_prod_tenant (tenant_id),
    INDEX idx_prod_category (category_id),
    INDEX idx_prod_supplier (supplier_id),
    CONSTRAINT fk_prod_cat FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT fk_prod_sup FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);