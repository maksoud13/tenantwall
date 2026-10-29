-- ══════════════ SHOP_ALPHA ══════════════
INSERT IGNORE INTO categories (id, name, slug, tenant_id) VALUES
(1, 'Electronics',  'electronics',  'SHOP_ALPHA'),
(2, 'Accessories',  'accessories',  'SHOP_ALPHA'),
(3, 'Books',        'books',        'SHOP_ALPHA');

INSERT IGNORE INTO suppliers (id, name, email, phone, tenant_id) VALUES
(1, 'TechSource Ltd',   'sales@techsource.com',   '+20-100-111-0001', 'SHOP_ALPHA'),
(2, 'Global Gadgets',   'info@globalgadgets.io', '+20-100-111-0002', 'SHOP_ALPHA'),
(3, 'Reader House',     'orders@readerhouse.com','+20-100-111-0003', 'SHOP_ALPHA');

INSERT IGNORE INTO products (id, name, sku, price, stock_quantity, category_id, supplier_id, tenant_id) VALUES
(1, 'Smartphone X100',   'ELEC-SP-001', 8999.00, 15, 1, 1, 'SHOP_ALPHA'),
(2, 'Bluetooth Headset', 'ELEC-BT-002',  499.00, 80, 1, 2, 'SHOP_ALPHA'),
(3, 'USB-C Cable 2m',    'ACC-UC-001',    59.00, 200, 2, 2, 'SHOP_ALPHA'),
(4, 'Phone Case',        'ACC-PC-002',    89.00, 120, 2, 1, 'SHOP_ALPHA'),
(5, 'Spring Boot Guide', 'BOOK-SB-01',   450.00, 40, 3, 3, 'SHOP_ALPHA'),
(6, 'Clean Code',        'BOOK-CC-02',   380.00, 25, 3, 3, 'SHOP_ALPHA');

-- ══════════════ SHOP_BETA ══════════════
INSERT IGNORE INTO categories (id, name, slug, tenant_id) VALUES
(101, 'Clothing',  'clothing',  'SHOP_BETA'),
(102, 'Shoes',     'shoes',     'SHOP_BETA');

INSERT IGNORE INTO suppliers (id, name, email, phone, tenant_id) VALUES
(101, 'Fashion Hub',    'orders@fashionhub.com', '+20-100-222-0001', 'SHOP_BETA'),
(102, 'Sneaker World',  'sneakers@world.com',    '+20-100-222-0002', 'SHOP_BETA');

INSERT IGNORE INTO products (id, name, sku, price, stock_quantity, category_id, supplier_id, tenant_id) VALUES
(101, 'Cotton T-Shirt',   'CL-TS-001',  149.00, 300, 101, 101, 'SHOP_BETA'),
(102, 'Denim Jacket',     'CL-DJ-002',  799.00,  60, 101, 101, 'SHOP_BETA'),
(103, 'Running Sneakers', 'SH-RS-001', 1299.00,  45, 102, 102, 'SHOP_BETA'),
(104, 'Casual Loafers',   'SH-CL-002',  899.00,  30, 102, 102, 'SHOP_BETA');