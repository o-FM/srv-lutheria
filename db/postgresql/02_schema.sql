-- =============================================================================
-- srv-luthieria · PostgreSQL 15+ · 02 — tabelas, índices e restrições
--
-- Consolidação das migrations Flyway V1.0 a V4.2 (uma por módulo, em
-- srv-luthier-*/src/main/resources/db/migration). Executar conectado ao banco da aplicação:
--   psql -h <host> -U luthieria_app -d luthieria -f 02_schema.sql
--
-- Multi-tenant em banco compartilhado: toda tabela de negócio tem tenant_id (a luthieria) e o
-- Hibernate filtra todas as consultas por ele. Unicidades de negócio são por luthieria.
--
-- IMPORTANTE: a aplicação já cria este schema sozinha via Flyway. Use este script só se o banco
-- for criado manualmente (DBA); depois suba a aplicação com
--   SPRING_FLYWAY_BASELINE_ON_MIGRATE=true  SPRING_FLYWAY_BASELINE_VERSION=4.2
-- para o Flyway registrar que o schema já está na versão 4.2 (ver README.md desta pasta).
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- Módulo security: luthierias, usuários, tokens e auditoria
-- -----------------------------------------------------------------------------

CREATE TABLE tenant_tb (
    id          uuid         NOT NULL PRIMARY KEY,
    name        varchar(120) NOT NULL,
    slug        varchar(60)  NOT NULL,
    document    varchar(14),
    active      boolean      NOT NULL,
    created_at  timestamptz  NOT NULL,
    CONSTRAINT uk_tenant_slug UNIQUE (slug)
);
COMMENT ON TABLE tenant_tb IS 'Luthierias (tenants). Tabela de plataforma, sem tenant_id.';

CREATE TABLE user_tb (
    uuid           uuid         NOT NULL PRIMARY KEY,
    tenant_id      uuid         NOT NULL,
    name           varchar(100),
    email          varchar(255) NOT NULL,
    cpf            varchar(11),
    phone          varchar(11),
    password_hash  varchar(255) NOT NULL,
    profile        varchar(20)  NOT NULL,
    active         boolean      NOT NULL,
    theme_mode     varchar(10),
    theme_accent   varchar(15),
    theme_tone     varchar(10),
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT uk_user_tenant_cpf UNIQUE (tenant_id, cpf),
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant_tb (id),
    CONSTRAINT ck_user_profile CHECK (profile IN ('ADMIN', 'LUTHIER', 'ATTENDANT', 'STOCK_CLERK'))
);
CREATE INDEX idx_user_tenant ON user_tb (tenant_id);
COMMENT ON TABLE user_tb IS 'Usuários internos. E-mail único na plataforma (login); senha em Argon2id.';
COMMENT ON COLUMN user_tb.theme_accent IS 'Preferência de aparência do usuário (modo, cor de destaque e tom).';

CREATE TABLE refresh_token_tb (
    id          uuid        NOT NULL PRIMARY KEY,
    token_hash  varchar(64) NOT NULL,
    user_id     uuid        NOT NULL,
    family_id   uuid        NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    created_at  timestamptz NOT NULL,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_family ON refresh_token_tb (family_id);
CREATE INDEX idx_refresh_user ON refresh_token_tb (user_id);
COMMENT ON TABLE refresh_token_tb IS 'Refresh tokens (só o SHA-256). Rotação com detecção de reuso por família.';

CREATE TABLE password_reset_token_tb (
    id          uuid        NOT NULL PRIMARY KEY,
    token_hash  varchar(64) NOT NULL,
    user_id     uuid        NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL,
    CONSTRAINT uk_reset_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_reset_user ON password_reset_token_tb (user_id);

CREATE TABLE audit_log_tb (
    id             uuid         NOT NULL PRIMARY KEY,
    tenant_id      uuid,
    actor          varchar(254) NOT NULL,
    action         varchar(60)  NOT NULL,
    resource_type  varchar(40),
    resource_id    varchar(64),
    details        varchar(500),
    ip             varchar(45),
    created_at     timestamptz  NOT NULL
);
CREATE INDEX idx_audit_tenant_created ON audit_log_tb (tenant_id, created_at);
COMMENT ON TABLE audit_log_tb IS 'Trilha de auditoria (LGPD). tenant_id nulo = evento de plataforma.';

-- -----------------------------------------------------------------------------
-- Spring Modulith: outbox de eventos entre módulos
-- -----------------------------------------------------------------------------

CREATE TABLE event_publication (
    id                      uuid           NOT NULL PRIMARY KEY,
    listener_id             varchar(512)   NOT NULL,
    event_type              varchar(512)   NOT NULL,
    serialized_event        varchar(10000) NOT NULL,
    publication_date        timestamptz    NOT NULL,
    completion_date         timestamptz,
    status                  varchar(20),
    completion_attempts     integer        NOT NULL,
    last_resubmission_date  timestamptz
);
CREATE INDEX idx_event_publication_completion ON event_publication (completion_date);
COMMENT ON TABLE event_publication IS 'Event Publication Registry: eventos pendentes/falhos para reprocessamento.';

-- -----------------------------------------------------------------------------
-- Módulo customer: clientes e instrumentos
-- -----------------------------------------------------------------------------

CREATE TABLE customer_tb (
    id          uuid          NOT NULL PRIMARY KEY,
    tenant_id   uuid          NOT NULL,
    name        varchar(100)  NOT NULL,
    phone       varchar(11)   NOT NULL,
    email       varchar(254),
    cpf         varchar(11),
    address     varchar(255),
    notes       varchar(1000),
    active      boolean       NOT NULL,
    created_at  timestamptz   NOT NULL,
    updated_at  timestamptz   NOT NULL,
    version     bigint        NOT NULL,
    CONSTRAINT uk_customer_tenant_cpf UNIQUE (tenant_id, cpf)
);
CREATE INDEX idx_customer_tenant_name ON customer_tb (tenant_id, name);
CREATE INDEX idx_customer_tenant_phone ON customer_tb (tenant_id, phone);
COMMENT ON TABLE customer_tb IS 'Clientes. CPF e telefone só com dígitos; CPF único por luthieria.';

CREATE TABLE instrument_tb (
    id                       uuid          NOT NULL PRIMARY KEY,
    tenant_id                uuid          NOT NULL,
    customer_id              uuid          NOT NULL,
    type                     varchar(20)   NOT NULL,
    brand                    varchar(60)   NOT NULL,
    model                    varchar(80)   NOT NULL,
    serial_number            varchar(60),
    manufacture_year         varchar(10),
    finish_color             varchar(60),
    strings_gauge            varchar(30),
    tuning_preference        varchar(60),
    action_height_12th_fret  varchar(30),
    condition_notes          varchar(1000),
    created_at               timestamptz   NOT NULL,
    updated_at               timestamptz   NOT NULL,
    version                  bigint        NOT NULL,
    CONSTRAINT fk_instrument_customer FOREIGN KEY (customer_id) REFERENCES customer_tb (id),
    CONSTRAINT ck_instrument_type CHECK (type IN ('VIOLAO_ACO', 'VIOLAO_NYLON', 'GUITARRA', 'BAIXO',
        'CAVAQUINHO', 'VIOLA', 'VIOLINO', 'OUTRO'))
);
CREATE INDEX idx_instrument_tenant_customer ON instrument_tb (tenant_id, customer_id);
CREATE INDEX idx_instrument_tenant_serial ON instrument_tb (tenant_id, serial_number);

-- -----------------------------------------------------------------------------
-- Módulo product: produtos e movimentações de estoque
-- -----------------------------------------------------------------------------

CREATE TABLE product_tb (
    id                uuid          NOT NULL PRIMARY KEY,
    tenant_id         uuid          NOT NULL,
    name              varchar(120)  NOT NULL,
    category          varchar(20)   NOT NULL,
    brand             varchar(60),
    sku               varchar(40)   NOT NULL,
    barcode           varchar(40),
    unit              varchar(10)   NOT NULL,
    cost_price        numeric(12,2) NOT NULL,
    sale_price        numeric(12,2) NOT NULL,
    current_quantity  integer       NOT NULL,
    min_quantity      integer       NOT NULL,
    location          varchar(60),
    active            boolean       NOT NULL,
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    version           bigint        NOT NULL,
    CONSTRAINT uk_product_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT uk_product_tenant_barcode UNIQUE (tenant_id, barcode),
    CONSTRAINT ck_product_category CHECK (category IN ('PECAS', 'CORDAS', 'MADEIRAS', 'INSUMOS')),
    CONSTRAINT ck_product_quantity CHECK (current_quantity >= 0)
);
CREATE INDEX idx_product_tenant_name ON product_tb (tenant_id, name);
COMMENT ON TABLE product_tb IS 'Itens de estoque. Saldo só muda via stock_movement_tb; version = lock otimista.';

CREATE TABLE stock_movement_tb (
    id             uuid          NOT NULL PRIMARY KEY,
    tenant_id      uuid          NOT NULL,
    product_id     uuid          NOT NULL,
    type           varchar(10)   NOT NULL,
    quantity       integer       NOT NULL,
    delta          integer       NOT NULL,
    balance_after  integer       NOT NULL,
    reason         varchar(255)  NOT NULL,
    order_id       uuid,
    created_by     varchar(254),
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    version        bigint        NOT NULL,
    CONSTRAINT fk_movement_product FOREIGN KEY (product_id) REFERENCES product_tb (id),
    CONSTRAINT ck_movement_type CHECK (type IN ('ENTRADA', 'SAIDA', 'AJUSTE'))
);
CREATE INDEX idx_movement_tenant_product ON stock_movement_tb (tenant_id, product_id);
COMMENT ON TABLE stock_movement_tb IS 'Movimentações imutáveis (trilha do saldo). order_id referencia a OS sem FK (outro módulo).';

-- -----------------------------------------------------------------------------
-- Módulo service: catálogo de serviços e ordens de serviço
-- -----------------------------------------------------------------------------

CREATE TABLE service_catalog_tb (
    id              uuid          NOT NULL PRIMARY KEY,
    tenant_id       uuid          NOT NULL,
    title           varchar(120)  NOT NULL,
    description     varchar(1000),
    labor_price     numeric(12,2) NOT NULL,
    estimated_days  integer,
    active          boolean       NOT NULL,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL,
    CONSTRAINT uk_service_tenant_title UNIQUE (tenant_id, title)
);

CREATE TABLE service_order_counter_tb (
    id           uuid        NOT NULL PRIMARY KEY,
    tenant_id    uuid        NOT NULL,
    last_number  integer     NOT NULL,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    version      bigint      NOT NULL,
    CONSTRAINT uk_order_counter_tenant UNIQUE (tenant_id)
);
COMMENT ON TABLE service_order_counter_tb IS 'Numeração sequencial de OS por luthieria (lida com SELECT ... FOR UPDATE).';

CREATE TABLE service_order_tb (
    id                       uuid          NOT NULL PRIMARY KEY,
    tenant_id                uuid          NOT NULL,
    order_number             integer       NOT NULL,
    customer_id              uuid          NOT NULL,
    instrument_id            uuid          NOT NULL,
    status                   varchar(25)   NOT NULL,
    priority                 varchar(10)   NOT NULL,
    reported_issue           varchar(2000) NOT NULL,
    diagnosis_notes          varchar(2000),
    technician_notes         varchar(2000),
    estimated_delivery_date  date,
    completed_at             timestamptz,
    delivered_at             timestamptz,
    tracking_token           varchar(43)   NOT NULL,
    discount                 numeric(12,2) NOT NULL,
    payment_terms            varchar(255),
    quote_status             varchar(10),
    quote_sent_at            timestamptz,
    quote_answered_at        timestamptz,
    rejection_reason         varchar(1000),
    paid_amount              numeric(12,2) NOT NULL,
    created_at               timestamptz   NOT NULL,
    updated_at               timestamptz   NOT NULL,
    version                  bigint        NOT NULL,
    CONSTRAINT uk_order_tenant_number UNIQUE (tenant_id, order_number),
    CONSTRAINT uk_order_tracking_token UNIQUE (tracking_token),
    CONSTRAINT ck_order_status CHECK (status IN ('RECEBIDO', 'DIAGNOSTICO', 'ORCAMENTO',
        'AGUARDANDO_APROVACAO', 'EM_EXECUCAO', 'PRONTO', 'ENTREGUE', 'CANCELADO')),
    CONSTRAINT ck_order_priority CHECK (priority IN ('BAIXA', 'NORMAL', 'ALTA', 'URGENTE')),
    CONSTRAINT ck_order_quote_status CHECK (quote_status IN ('PENDENTE', 'APROVADO', 'REJEITADO'))
);
CREATE INDEX idx_order_tenant_status ON service_order_tb (tenant_id, status);
CREATE INDEX idx_order_tenant_customer ON service_order_tb (tenant_id, customer_id);
COMMENT ON TABLE service_order_tb IS 'Ordens de serviço. customer_id/instrument_id sem FK: pertencem ao módulo customer.';
COMMENT ON COLUMN service_order_tb.tracking_token IS 'Token aleatório (192 bits) do portal do cliente.';

CREATE TABLE service_order_item_tb (
    id           uuid          NOT NULL PRIMARY KEY,
    tenant_id    uuid          NOT NULL,
    order_id     uuid          NOT NULL,
    catalog_id   uuid,
    title        varchar(120)  NOT NULL,
    description  varchar(1000),
    labor_price  numeric(12,2) NOT NULL,
    done         boolean       NOT NULL,
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    version      bigint        NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES service_order_tb (id)
);
CREATE INDEX idx_order_item_order ON service_order_item_tb (order_id);

CREATE TABLE service_order_part_tb (
    id            uuid          NOT NULL PRIMARY KEY,
    tenant_id     uuid          NOT NULL,
    order_id      uuid          NOT NULL,
    product_id    uuid          NOT NULL,
    product_name  varchar(120)  NOT NULL,
    quantity      integer       NOT NULL,
    unit_price    numeric(12,2) NOT NULL,
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    version       bigint        NOT NULL,
    CONSTRAINT fk_order_part_order FOREIGN KEY (order_id) REFERENCES service_order_tb (id),
    CONSTRAINT ck_order_part_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_order_part_order ON service_order_part_tb (order_id);

CREATE TABLE service_order_timeline_tb (
    id           uuid          NOT NULL PRIMARY KEY,
    tenant_id    uuid          NOT NULL,
    order_id     uuid          NOT NULL,
    type         varchar(20)   NOT NULL,
    title        varchar(160)  NOT NULL,
    description  varchar(1000),
    actor        varchar(254)  NOT NULL,
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    version      bigint        NOT NULL,
    CONSTRAINT fk_order_timeline_order FOREIGN KEY (order_id) REFERENCES service_order_tb (id)
);
CREATE INDEX idx_order_timeline_order ON service_order_timeline_tb (order_id);

CREATE TABLE payment_tb (
    id              uuid          NOT NULL PRIMARY KEY,
    tenant_id       uuid          NOT NULL,
    order_id        uuid          NOT NULL,
    amount          numeric(12,2) NOT NULL,
    method          varchar(20)   NOT NULL,
    receipt_number  varchar(30)   NOT NULL,
    received_by     varchar(254)  NOT NULL,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES service_order_tb (id),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_method CHECK (method IN ('PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO',
        'DINHEIRO', 'TRANSFERENCIA'))
);
CREATE INDEX idx_payment_tenant_created ON payment_tb (tenant_id, created_at);

-- -----------------------------------------------------------------------------
-- Módulo notification
-- -----------------------------------------------------------------------------

CREATE TABLE notification_tb (
    id            uuid          NOT NULL PRIMARY KEY,
    tenant_id     uuid          NOT NULL,
    channel       varchar(10)   NOT NULL,
    status        varchar(10)   NOT NULL,
    recipient     varchar(254),
    subject       varchar(160)  NOT NULL,
    body          varchar(2000) NOT NULL,
    action_url    varchar(2500),
    related_type  varchar(30),
    related_id    uuid,
    error         varchar(500),
    sent_at       timestamptz,
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    version       bigint        NOT NULL,
    customer_id   uuid,
    CONSTRAINT ck_notification_channel CHECK (channel IN ('EMAIL', 'WHATSAPP', 'INTERNAL')),
    CONSTRAINT ck_notification_status CHECK (status IN ('SENT', 'FAILED', 'PENDING'))
);
CREATE INDEX idx_notification_tenant_created ON notification_tb (tenant_id, created_at);
CREATE INDEX idx_notification_customer ON notification_tb (customer_id);
COMMENT ON COLUMN notification_tb.customer_id IS 'Permite apagar o conteúdo enviado a um cliente anonimizado (LGPD).';

COMMIT;
