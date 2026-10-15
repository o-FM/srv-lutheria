-- Módulo notification: registro das notificações por luthieria.
create table notification_tb (
    id            uuid          not null primary key,
    tenant_id     uuid          not null,
    channel       varchar(10)   not null,
    status        varchar(10)   not null,
    recipient     varchar(254),
    subject       varchar(160)  not null,
    body          varchar(2000) not null,
    action_url    varchar(2500),
    related_type  varchar(30),
    related_id    uuid,
    error         varchar(500),
    sent_at       timestamp with time zone,
    created_at    timestamp with time zone not null,
    updated_at    timestamp with time zone not null,
    version       bigint        not null,
    constraint ck_notification_channel check (channel in ('EMAIL', 'WHATSAPP', 'INTERNAL')),
    constraint ck_notification_status check (status in ('SENT', 'FAILED', 'PENDING'))
);
create index idx_notification_tenant_created on notification_tb (tenant_id, created_at);
