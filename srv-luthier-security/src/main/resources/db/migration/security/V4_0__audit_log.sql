-- Endurecimento (LGPD): trilha de auditoria de ações sensíveis.
create table audit_log_tb (
    id             uuid          not null primary key,
    tenant_id      uuid,
    actor          varchar(254)  not null,
    action         varchar(60)   not null,
    resource_type  varchar(40),
    resource_id    varchar(64),
    details        varchar(500),
    ip             varchar(45),
    created_at     timestamp with time zone not null
);
create index idx_audit_tenant_created on audit_log_tb (tenant_id, created_at);
