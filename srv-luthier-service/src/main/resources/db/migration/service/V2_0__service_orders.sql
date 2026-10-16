-- Núcleo: ordens de serviço, itens, peças, linha do tempo, pagamentos e numeração por luthieria.

create table service_order_counter_tb (
    id           uuid    not null primary key,
    tenant_id    uuid    not null,
    last_number  integer not null,
    created_at   timestamp with time zone not null,
    updated_at   timestamp with time zone not null,
    version      bigint  not null,
    constraint uk_order_counter_tenant unique (tenant_id)
);

create table service_order_tb (
    id                       uuid          not null primary key,
    tenant_id                uuid          not null,
    order_number             integer       not null,
    customer_id              uuid          not null,
    instrument_id            uuid          not null,
    status                   varchar(25)   not null,
    priority                 varchar(10)   not null,
    reported_issue           varchar(2000) not null,
    diagnosis_notes          varchar(2000),
    technician_notes         varchar(2000),
    estimated_delivery_date  date,
    completed_at             timestamp with time zone,
    delivered_at             timestamp with time zone,
    tracking_token           varchar(43)   not null,
    discount                 numeric(12,2) not null,
    payment_terms            varchar(255),
    quote_status             varchar(10),
    quote_sent_at            timestamp with time zone,
    quote_answered_at        timestamp with time zone,
    rejection_reason         varchar(1000),
    paid_amount              numeric(12,2) not null,
    created_at               timestamp with time zone not null,
    updated_at               timestamp with time zone not null,
    version                  bigint        not null,
    constraint uk_order_tenant_number unique (tenant_id, order_number),
    constraint uk_order_tracking_token unique (tracking_token),
    constraint ck_order_status check (status in ('RECEBIDO', 'DIAGNOSTICO', 'ORCAMENTO',
        'AGUARDANDO_APROVACAO', 'EM_EXECUCAO', 'PRONTO', 'ENTREGUE', 'CANCELADO')),
    constraint ck_order_priority check (priority in ('BAIXA', 'NORMAL', 'ALTA', 'URGENTE')),
    constraint ck_order_quote_status check (quote_status in ('PENDENTE', 'APROVADO', 'REJEITADO'))
);
create index idx_order_tenant_status on service_order_tb (tenant_id, status);
create index idx_order_tenant_customer on service_order_tb (tenant_id, customer_id);

create table service_order_item_tb (
    id           uuid          not null primary key,
    tenant_id    uuid          not null,
    order_id     uuid          not null,
    catalog_id   uuid,
    title        varchar(120)  not null,
    description  varchar(1000),
    labor_price  numeric(12,2) not null,
    done         boolean       not null,
    created_at   timestamp with time zone not null,
    updated_at   timestamp with time zone not null,
    version      bigint        not null,
    constraint fk_order_item_order foreign key (order_id) references service_order_tb (id)
);
create index idx_order_item_order on service_order_item_tb (order_id);

create table service_order_part_tb (
    id            uuid          not null primary key,
    tenant_id     uuid          not null,
    order_id      uuid          not null,
    product_id    uuid          not null,
    product_name  varchar(120)  not null,
    quantity      integer       not null,
    unit_price    numeric(12,2) not null,
    created_at    timestamp with time zone not null,
    updated_at    timestamp with time zone not null,
    version       bigint        not null,
    constraint fk_order_part_order foreign key (order_id) references service_order_tb (id),
    constraint ck_order_part_quantity check (quantity > 0)
);
create index idx_order_part_order on service_order_part_tb (order_id);

create table service_order_timeline_tb (
    id           uuid          not null primary key,
    tenant_id    uuid          not null,
    order_id     uuid          not null,
    type         varchar(20)   not null,
    title        varchar(160)  not null,
    description  varchar(1000),
    actor        varchar(254)  not null,
    created_at   timestamp with time zone not null,
    updated_at   timestamp with time zone not null,
    version      bigint        not null,
    constraint fk_order_timeline_order foreign key (order_id) references service_order_tb (id)
);
create index idx_order_timeline_order on service_order_timeline_tb (order_id);

create table payment_tb (
    id              uuid          not null primary key,
    tenant_id       uuid          not null,
    order_id        uuid          not null,
    amount          numeric(12,2) not null,
    method          varchar(20)   not null,
    receipt_number  varchar(30)   not null,
    received_by     varchar(254)  not null,
    created_at      timestamp with time zone not null,
    updated_at      timestamp with time zone not null,
    version         bigint        not null,
    constraint fk_payment_order foreign key (order_id) references service_order_tb (id),
    constraint ck_payment_amount check (amount > 0),
    constraint ck_payment_method check (method in ('PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO',
        'DINHEIRO', 'TRANSFERENCIA'))
);
create index idx_payment_tenant_created on payment_tb (tenant_id, created_at);
