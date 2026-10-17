-- Fotos da OS (entrada, bancada, finalizado...). O binário fica no próprio banco: o backup
-- diário (pg_dump) já leva as fotos junto e não há outro serviço para manter.

create table service_order_photo_tb (
    id            uuid          not null primary key,
    tenant_id     uuid          not null,
    order_id      uuid          not null,
    tag           varchar(20)   not null,
    caption       varchar(200),
    content_type  varchar(30)   not null,
    size_bytes    integer       not null,
    data          bytea         not null,
    actor         varchar(254)  not null,
    created_at    timestamp with time zone not null,
    updated_at    timestamp with time zone not null,
    version       bigint        not null,
    constraint fk_order_photo_order foreign key (order_id) references service_order_tb (id),
    constraint ck_order_photo_tag check (tag in ('ENTRADA', 'DIAGNOSTICO', 'BANCADA',
        'FINALIZADO', 'DETALHE_DEFEITO'))
);
create index idx_order_photo_order on service_order_photo_tb (order_id);
