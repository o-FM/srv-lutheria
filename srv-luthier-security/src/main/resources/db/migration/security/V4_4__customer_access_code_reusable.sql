-- Área do cliente: login só pelo código (amigável, reutilizável até expirar ou ser substituído).
delete from customer_access_code_tb;
drop index idx_access_code_tenant_phone;
alter table customer_access_code_tb drop column attempts;
alter table customer_access_code_tb rename column used_at to revoked_at;
alter table customer_access_code_tb add column last_login_at timestamp with time zone;
alter table customer_access_code_tb add constraint uk_access_code_hash unique (code_hash);
create index idx_access_code_tenant_customer on customer_access_code_tb (tenant_id, customer_id);
