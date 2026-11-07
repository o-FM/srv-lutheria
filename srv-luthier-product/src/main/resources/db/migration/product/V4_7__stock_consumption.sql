-- PRO-01: uma baixa de estoque por OS. O outbox do Spring Modulith entrega o evento ao menos uma
-- vez; o marcador único faz a reentrega ser ignorada. Não se usa unicidade em stock_movement_tb
-- porque o movimento manual aceita order_id livre (PRO-06).
-- Antes do deploy: as baixas antigas não têm marcador, então não pode haver baixa pendente no
-- outbox. Esta consulta precisa dar 0:
--   select count(*) from event_publication
--   where completion_date is null and listener_id like '%StockConsumptionListener%';
create table stock_consumption_tb (
    id          uuid          not null primary key,
    tenant_id   uuid          not null,
    order_id    uuid          not null,
    reference   varchar(255)  not null,
    created_at  timestamp with time zone not null,
    updated_at  timestamp with time zone not null,
    version     bigint        not null,
    constraint uk_stock_consumption_order unique (tenant_id, order_id),
    constraint fk_stock_consumption_tenant foreign key (tenant_id) references tenant_tb (id)
);
