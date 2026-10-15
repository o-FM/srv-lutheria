-- Spring Modulith: Event Publication Registry (outbox). Guarda eventos entregues a listeners
-- assíncronos até a conclusão, permitindo reprocessar o que falhou.
create table event_publication (
    id                      uuid          not null primary key,
    listener_id             varchar(512)  not null,
    event_type              varchar(512)  not null,
    serialized_event        varchar(10000) not null,
    publication_date        timestamp with time zone not null,
    completion_date         timestamp with time zone,
    status                  varchar(20),
    completion_attempts     integer       not null,
    last_resubmission_date  timestamp with time zone
);
create index idx_event_publication_completion on event_publication (completion_date);
