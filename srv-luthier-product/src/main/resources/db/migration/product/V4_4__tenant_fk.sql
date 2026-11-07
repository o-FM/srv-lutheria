-- APP-03: toda linha de negócio pertence a uma luthieria que existe. Barra o tenant ROOT
-- (visão de plataforma) e UUIDs inventados, a última defesa do isolamento (SH-01).
-- Antes do deploy, por tabela: select count(*) from <tabela> where tenant_id not in
-- (select id from tenant_tb); precisa dar 0, senão a migration falha. Não usamos NOT VALID
-- porque o H2 (dev e testes) não aceita a sintaxe.
alter table product_tb
    add constraint fk_product_tenant foreign key (tenant_id) references tenant_tb (id);
alter table stock_movement_tb
    add constraint fk_stock_movement_tenant foreign key (tenant_id) references tenant_tb (id);
