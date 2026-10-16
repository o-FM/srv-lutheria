-- =============================================================================
-- srv-luthieria · CUIDADO: remove TODAS as tabelas e dados da aplicação.
-- Uso apenas em desenvolvimento/homologação:
--   psql -h <host> -U luthieria_app -d luthieria -f 99_drop_all.sql
-- =============================================================================
BEGIN;
DROP TABLE IF EXISTS
    payment_tb, service_order_timeline_tb, service_order_part_tb, service_order_item_tb,
    service_order_tb, service_order_counter_tb, service_catalog_tb,
    stock_movement_tb, product_tb,
    instrument_tb, customer_tb,
    notification_tb, event_publication,
    audit_log_tb, password_reset_token_tb, refresh_token_tb, user_tb, tenant_tb,
    flyway_schema_history
    CASCADE;
COMMIT;
