CREATE TABLE orders (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    schema_version   VARCHAR(2) NOT NULL,
    -- V1 columns
    customer_name    VARCHAR(200),    -- V1 only; NULL in V2+
    amount           BIGINT NOT NULL, -- V1-V2: cents in JPY; V3: amount in minor units
    -- V2 columns (added)
    first_name       VARCHAR(100),    -- NULL in V1
    last_name        VARCHAR(100),    -- NULL in V1
    -- V3 columns (added)
    currency         VARCHAR(3)       -- NULL in V1-V2 (default JPY assumed)
);

-- Seed data: rows from different schema versions coexist in the same table.

-- V1 rows (legacy): single customer_name field, amount in JPY
INSERT INTO orders (schema_version, customer_name, amount)
VALUES ('1', 'Taro Yamada', 1500);
INSERT INTO orders (schema_version, customer_name, amount)
VALUES ('1', 'Jiro Tanaka', 800);

-- V2 rows: customer_name split into first_name + last_name, amount still in JPY
INSERT INTO orders (schema_version, first_name, last_name, amount)
VALUES ('2', 'Hanako', 'Suzuki', 3200);

-- V3 rows: first_name/last_name + explicit currency
INSERT INTO orders (schema_version, first_name, last_name, amount, currency)
VALUES ('3', 'John', 'Smith', 4999, 'USD');
INSERT INTO orders (schema_version, first_name, last_name, amount, currency)
VALUES ('3', 'Alice', 'Johnson', 2500, 'EUR');
