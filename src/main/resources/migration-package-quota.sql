-- Migration: Allow plan_id NULL on user_subscription table.
-- PackagePricing purchases now create UserSubscription records without a SubscriptionPlan.
-- Run ONE-TIME against the database before deploying.
--
-- Usage:
--   psql $NEON_DB_URL -f migration-package-quota.sql

ALTER TABLE user_subscription ALTER COLUMN plan_id DROP NOT NULL;
