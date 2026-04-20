-- Migration VND Refactor — run ONE-TIME against Neon DB before deploying refactor build.
-- Test data only, wipe is safe. After running, Hibernate ddl-auto=update will auto-create
-- subscription_plan and user_subscription tables on next startup; initializers re-seed data.
--
-- Usage:
--   psql $NEON_DB_URL -f migration-vnd-refactor.sql
--
-- Order matters due to FK constraints.

BEGIN;

-- 1. Wipe transaction history (balance tracking đổi đơn vị từ đậu → VND)
TRUNCATE credit_transaction RESTART IDENTITY CASCADE;

-- 2. Wipe payment records (cần vì package_pricing sẽ bị xóa, và giá bonus khác)
TRUNCATE payment RESTART IDENTITY CASCADE;

-- 3. Reset user balance về 0. Trường `credit` giờ mang nghĩa VND.
UPDATE users SET credit = 0;

-- 4. Wipe service_pricing và package_pricing để initializer tự reseed theo VND default
TRUNCATE service_pricing RESTART IDENTITY CASCADE;
TRUNCATE package_pricing RESTART IDENTITY CASCADE;

-- 5. Nếu tồn tại bảng subscription_plan/user_subscription từ lần deploy trước, wipe luôn
--    (an toàn nếu bảng chưa có — sẽ lỗi, uncomment nếu cần)
-- TRUNCATE user_subscription RESTART IDENTITY CASCADE;
-- TRUNCATE subscription_plan RESTART IDENTITY CASCADE;

COMMIT;

-- Next steps:
-- 1. Deploy new backend build.
-- 2. On startup:
--    - ServicePricingInitializer seeds 4 services (2k/3k/30k/50k VND)
--    - PackagePricingInitializer seeds 4 top-up tiers (50k/100k+10%/200k+15%/500k+20%)
--    - SubscriptionPlanInitializer seeds 3 plans (Starter 49k / Standard 99k / Pro 199k)
-- 3. Verify via GET /packages, /services, /subscriptions (after Phase 2 controllers ready)
