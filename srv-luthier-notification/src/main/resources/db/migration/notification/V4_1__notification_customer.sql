-- LGPD: permite localizar e apagar o conteúdo das notificações de um cliente anonimizado.
alter table notification_tb add column customer_id uuid;
create index idx_notification_customer on notification_tb (customer_id);
