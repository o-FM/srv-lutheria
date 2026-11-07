-- APP-03: toda linha de negócio pertence a uma luthieria que existe. Barra o tenant ROOT
-- (visão de plataforma) e UUIDs inventados, a última defesa do isolamento (SH-01).
-- Antes do deploy, por tabela: select count(*) from <tabela> where tenant_id not in
-- (select id from tenant_tb); precisa dar 0, senão a migration falha. Não usamos NOT VALID
-- porque o H2 (dev e testes) não aceita a sintaxe.
alter table service_catalog_tb
    add constraint fk_service_catalog_tenant foreign key (tenant_id) references tenant_tb (id);
alter table service_order_counter_tb
    add constraint fk_order_counter_tenant foreign key (tenant_id) references tenant_tb (id);
alter table service_order_tb
    add constraint fk_order_tenant foreign key (tenant_id) references tenant_tb (id);
alter table service_order_item_tb
    add constraint fk_order_item_tenant foreign key (tenant_id) references tenant_tb (id);
alter table service_order_part_tb
    add constraint fk_order_part_tenant foreign key (tenant_id) references tenant_tb (id);
alter table service_order_timeline_tb
    add constraint fk_order_timeline_tenant foreign key (tenant_id) references tenant_tb (id);
alter table payment_tb
    add constraint fk_payment_tenant foreign key (tenant_id) references tenant_tb (id);
