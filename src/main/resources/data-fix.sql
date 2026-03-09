-- Fix: Add auto-increment to package_pricing.id for existing databases
-- Hibernate ddl-auto:update does not alter existing columns to add identity/sequence
CREATE SEQUENCE IF NOT EXISTS package_pricing_id_seq OWNED BY package_pricing.id;
SELECT setval('package_pricing_id_seq', COALESCE((SELECT MAX(id) FROM package_pricing), 0) + 1);
ALTER TABLE package_pricing ALTER COLUMN id SET DEFAULT nextval('package_pricing_id_seq');
