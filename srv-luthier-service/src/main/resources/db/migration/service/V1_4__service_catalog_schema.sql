-- Módulo service: catálogo de serviços por luthieria.

create table service_catalog_tb (
    id              uuid          not null primary key,
    tenant_id       uuid          not null,
    title           varchar(120)  not null,
    description     varchar(1000),
    labor_price     numeric(12,2) not null,
    estimated_days  integer,
    active          boolean       not null,
    created_at      timestamp with time zone not null,
    updated_at      timestamp with time zone not null,
    version         bigint        not null,
    constraint uk_service_tenant_title unique (tenant_id, title)
);
