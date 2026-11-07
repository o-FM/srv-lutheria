-- NOT-03: o expurgo de retenção filtra só por created_at, em todas as luthierias. O índice
-- idx_notification_tenant_created começa por tenant_id e não serve para esse filtro.
create index idx_notification_created on notification_tb (created_at);
