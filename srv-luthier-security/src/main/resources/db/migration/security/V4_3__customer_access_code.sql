-- Área do cliente: códigos de acesso de uso único (só o HMAC do código é guardado).
create table customer_access_code_tb (
    id           uuid        not null primary key,
    tenant_id    uuid        not null,
    customer_id  uuid        not null,
    phone        varchar(11) not null,
    code_hash    varchar(64) not null,
    expires_at   timestamp with time zone not null,
    attempts     integer     not null,
    used_at      timestamp with time zone,
    created_at   timestamp with time zone not null
);
create index idx_access_code_tenant_phone on customer_access_code_tb (tenant_id, phone);
