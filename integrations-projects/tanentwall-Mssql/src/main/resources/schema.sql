-- ═══ MSSQL Schema ═══
IF OBJECT_ID('products', 'U') IS NOT NULL DROP TABLE products;
IF OBJECT_ID('categories', 'U') IS NOT NULL DROP TABLE categories;
IF OBJECT_ID('suppliers', 'U') IS NOT NULL DROP TABLE suppliers;

CREATE TABLE categories (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    slug NVARCHAR(100),
    tenant_id NVARCHAR(50) NOT NULL
);
CREATE INDEX idx_cat_tenant ON categories(tenant_id);

CREATE TABLE suppliers (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(150) NOT NULL,
    email NVARCHAR(150),
    phone NVARCHAR(50),
    tenant_id NVARCHAR(50) NOT NULL
);
CREATE INDEX idx_sup_tenant ON suppliers(tenant_id);

CREATE TABLE products (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(200) NOT NULL,
    sku NVARCHAR(100) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    category_id BIGINT,
    supplier_id BIGINT,
    tenant_id NVARCHAR(50) NOT NULL,
    CONSTRAINT fk_prod_cat FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT fk_prod_sup FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);
CREATE INDEX idx_prod_tenant ON products(tenant_id);
CREATE INDEX idx_prod_category ON products(category_id);
CREATE INDEX idx_prod_supplier ON products(supplier_id);