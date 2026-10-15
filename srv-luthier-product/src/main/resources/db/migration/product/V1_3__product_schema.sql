-- Módulo product: produtos e movimentações de estoque (imutáveis).

create table product_tb (
    id                uuid          not null primary key,
    tenant_id         uuid          not null,
    name              varchar(120)  not null,
    category          varchar(20)   not null,
    brand             varchar(60),
    sku               varchar(40)   not null,
    barcode           varchar(40),
    unit              varchar(10)   not null,
    cost_price        numeric(12,2) not null,
    sale_price        numeric(12,2) not null,
    current_quantity  integer       not null,
    min_quantity      integer       not null,
    location          varchar(60),
    active            boolean       not null,
    created_at        timestamp with time zone not null,
    updated_at        timestamp with time zone not null,
    version           bigint        not null,
    constraint uk_product_tenant_sku unique (tenant_id, sku),
    constraint uk_product_tenant_barcode unique (tenant_id, barcode),
    constraint ck_product_category check (category in ('PECAS', 'CORDAS', 'MADEIRAS', 'INSUMOS')),
    constraint ck_product_quantity check (current_quantity >= 0)
);
create index idx_product_tenant_name on product_tb (tenant_id, name);

create table stock_movement_tb (
    id             uuid          not null primary key,
    tenant_id      uuid          not null,
    product_id     uuid          not null,
    type           varchar(10)   not null,
    quantity       integer       not null,
    delta          integer       not null,
    balance_after  integer       not null,
    reason         varchar(255)  not null,
    order_id       uuid,
    created_by     varchar(254),
    created_at     timestamp with time zone not null,
    updated_at     timestamp with time zone not null,
    version        bigint        not null,
    constraint fk_movement_product foreign key (product_id) references product_tb (id),
    constraint ck_movement_type check (type in ('ENTRADA', 'SAIDA', 'AJUSTE'))
);
create index idx_movement_tenant_product on stock_movement_tb (tenant_id, product_id);
