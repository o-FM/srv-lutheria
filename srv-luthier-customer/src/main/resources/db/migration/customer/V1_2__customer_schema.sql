-- Módulo customer: clientes e instrumentos (tenant_id em todas as tabelas).

create table customer_tb (
    id          uuid          not null primary key,
    tenant_id   uuid          not null,
    name        varchar(100)  not null,
    phone       varchar(11)   not null,
    email       varchar(254),
    cpf         varchar(11),
    address     varchar(255),
    notes       varchar(1000),
    active      boolean       not null,
    created_at  timestamp with time zone not null,
    updated_at  timestamp with time zone not null,
    version     bigint        not null,
    constraint uk_customer_tenant_cpf unique (tenant_id, cpf)
);
create index idx_customer_tenant_name on customer_tb (tenant_id, name);
create index idx_customer_tenant_phone on customer_tb (tenant_id, phone);

create table instrument_tb (
    id                       uuid          not null primary key,
    tenant_id                uuid          not null,
    customer_id              uuid          not null,
    type                     varchar(20)   not null,
    brand                    varchar(60)   not null,
    model                    varchar(80)   not null,
    serial_number            varchar(60),
    manufacture_year         varchar(10),
    finish_color             varchar(60),
    strings_gauge            varchar(30),
    tuning_preference        varchar(60),
    action_height_12th_fret  varchar(30),
    condition_notes          varchar(1000),
    created_at               timestamp with time zone not null,
    updated_at               timestamp with time zone not null,
    version                  bigint        not null,
    constraint fk_instrument_customer foreign key (customer_id) references customer_tb (id),
    constraint ck_instrument_type check (type in ('VIOLAO_ACO', 'VIOLAO_NYLON', 'GUITARRA', 'BAIXO',
        'CAVAQUINHO', 'VIOLA', 'VIOLINO', 'OUTRO'))
);
create index idx_instrument_tenant_customer on instrument_tb (tenant_id, customer_id);
create index idx_instrument_tenant_serial on instrument_tb (tenant_id, serial_number);
