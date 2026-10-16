-- Módulo security: luthierias (tenants), usuários e tokens opacos.

create table tenant_tb (
    id          uuid         not null primary key,
    name        varchar(120) not null,
    slug        varchar(60)  not null,
    document    varchar(14),
    active      boolean      not null,
    created_at  timestamp with time zone not null,
    constraint uk_tenant_slug unique (slug)
);

create table user_tb (
    uuid          uuid         not null primary key,
    tenant_id     uuid         not null,
    name          varchar(100),
    email         varchar(255) not null,
    cpf           varchar(11),
    phone         varchar(11),
    password_hash varchar(255) not null,
    profile       varchar(20)  not null,
    active        boolean      not null,
    constraint uk_user_email unique (email),
    constraint uk_user_tenant_cpf unique (tenant_id, cpf),
    constraint fk_user_tenant foreign key (tenant_id) references tenant_tb (id),
    constraint ck_user_profile check (profile in ('ADMIN', 'LUTHIER', 'ATTENDANT', 'STOCK_CLERK'))
);
create index idx_user_tenant on user_tb (tenant_id);

create table refresh_token_tb (
    id          uuid        not null primary key,
    token_hash  varchar(64) not null,
    user_id     uuid        not null,
    family_id   uuid        not null,
    expires_at  timestamp with time zone not null,
    revoked_at  timestamp with time zone,
    created_at  timestamp with time zone not null,
    constraint uk_refresh_token_hash unique (token_hash)
);
create index idx_refresh_family on refresh_token_tb (family_id);
create index idx_refresh_user on refresh_token_tb (user_id);

create table password_reset_token_tb (
    id          uuid        not null primary key,
    token_hash  varchar(64) not null,
    user_id     uuid        not null,
    expires_at  timestamp with time zone not null,
    used_at     timestamp with time zone,
    created_at  timestamp with time zone not null,
    constraint uk_reset_token_hash unique (token_hash)
);
create index idx_reset_user on password_reset_token_tb (user_id);
