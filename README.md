# srv-luthieria

Backend do sistema de gestão para oficinas de luthieria (multi-tenant): clientes e instrumentos,
estoque, catálogo de serviços, ordens de serviço com orçamento e portal do cliente, notificações,
relatórios, auditoria e LGPD. Front-end: [srv-lutheria-front](https://github.com/o-FM/srv-lutheria-front).

Monólito modular em **Java 21 + Spring Boot 4 + Spring Modulith**, pronto para extração em
microsserviços: cada módulo é um artefato Maven com suas próprias migrations e só conversa com os
outros por API pública ou eventos.

## Módulos

| Módulo | Pacote | Responsabilidade | Depende de |
|---|---|---|---|
| `srv-luthier-shared` | `com.luthier.shared` | Erros, tenancy, validações, auditoria (kernel aberto) | — |
| `srv-luthier-security` | `com.luthier.security` | Luthierias, usuários, JWT, RBAC, trilha de auditoria | shared |
| `srv-luthier-customer` | `com.luthier.customer` | Clientes e instrumentos | shared |
| `srv-luthier-product` | `com.luthier.product` | Produtos e estoque | shared |
| `srv-luthier-service` | `com.luthier.service` | Catálogo, ordens de serviço, portal, relatórios, exportação LGPD | shared, customer, product |
| `srv-luthier-notification` | `com.luthier.notification` | E-mail, WhatsApp e alertas (só ouve eventos) | shared + APIs públicas |
| `srv-luthier-app` | `com.luthier` | Executável, configuração, testes de integração | todos |

Eventos entre módulos: `StockConsumptionRequested` (OS pronta → baixa de estoque),
`ServiceOrderStatusChanged`, `CustomerAnonymized`, `StockLow`, `TenantRegistered`,
`PasswordResetRequested` e `AuditEvent`. Assíncronos usam o outbox do Modulith (`event_publication`).

## Rodando

```bash
mvn install                                   # build + todos os testes
java -jar srv-luthier-app/target/srv-luthier-app-1.0.0-SNAPSHOT.jar   # perfil dev: H2, porta 8080

# Ambiente completo: PostgreSQL, app (perfil prod), Mailpit, Prometheus e Jaeger
mvn install -DskipTests && docker compose up --build
```

Admin inicial em dev: `admin@luthieria.local` / `dev-admin-12345`. Novas luthierias:
`POST /auth/register`.

### Com o PostgreSQL local (perfil `local`)

Perfil `local` = facilidades de dev (OpenAPI em `/v3/api-docs`, código do cliente no log, CORS do front) +
PostgreSQL (`application-postgres.yaml`). Padrões: `localhost:5432/luthieria`, usuário
`luthieria_app`, senha `TroqueEstaSenha` (os mesmos do `db/postgresql/01_create_database.sql`).

```bash
# Linux/macOS
SPRING_PROFILES_ACTIVE=local DB_PASSWORD=suaSenha java -jar srv-luthier-app/target/srv-luthier-app-1.0.0-SNAPSHOT.jar
# Windows (PowerShell)
$env:SPRING_PROFILES_ACTIVE="local"; $env:DB_PASSWORD="suaSenha"; java -jar srv-luthier-app\target\srv-luthier-app-1.0.0-SNAPSHOT.jar
# ou pelo Maven / IDE: -Dspring-boot.run.profiles=local  |  VM option -Dspring.profiles.active=local
```

| Variável | Padrão |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/luthieria` |
| `DB_USERNAME` / `DB_PASSWORD` | `luthieria_app` / `TroqueEstaSenha` |
| `DB_BASELINE_VERSION` | `4.4` — versão do `02_schema.sql` usado para criar as tabelas |

Tabelas criadas pelos scripts: na primeira subida o Flyway registra o schema na versão
`DB_BASELINE_VERSION` (log `Successfully baselined schema with version: 4.4`) e não recria nada.
Banco vazio: o Flyway cria tudo. Se o Hibernate acusar coluna/tabela faltando, o script usado era de
uma versão anterior: rode `99_drop_all.sql` + `02_schema.sql` atuais (ou apague tudo e deixe o
Flyway criar).

### Postman

Importe `postman/srv-luthieria.postman_collection.json` (Import → arquivo) e rode na ordem, ou use
o **Collection Runner**. O primeiro request cadastra uma luthieria nova (e-mail e CPF aleatórios),
e tokens/IDs ficam salvos nas variáveis da coleção — dá para rodar quantas vezes quiser.
Área do cliente: copie o código `XXXX-XXXX` do log da API (`[DEV] Código de acesso`) para a
variável `accessCode` e rode a pasta 7. `baseUrl` padrão: `http://localhost:8080`.

Mesmo fluxo em curl (requer `jq`): `bash postman/fluxo-curl.sh` — no passo 7 ele pede o código do log.

### Teste e2e local com o front-end

O [srv-lutheria-front](https://github.com/o-FM/srv-lutheria-front) chama esta API direto do
navegador: o CORS do perfil dev já libera `http://localhost:3000` e `:5173`. Com o backend no ar:

```bash
cd srv-lutheria-front
npm install
npm run seed:api   # oficina, usuários, estoque, clientes e OS pela própria API
npm run dev:api    # front em http://localhost:3000 com VITE_API_URL=http://localhost:8080
```

Logins: `armando.luthier@oficina.com` (ou CPF `123.456.789-09`) / `Luthier@2026` e
`mariana.balcao@oficina.com` / `Balcao@2026!`. Detalhes no README do front ("Teste e2e local").

## Documentação

- [API, multi-tenancy e ciclo da OS](docs/api.md) · [OpenAPI](docs/openapi.json)
- [Revisão de segurança e LGPD](docs/security-review.md)
- [Scripts PostgreSQL (criação manual do banco)](db/postgresql/README.md)
- [Testes de carga](load-tests/README.md)
- Diagramas dos módulos: gerados em `srv-luthier-app/target/spring-modulith-docs` pelo `ModularityTest`

## Configuração (produção)

| Variável | Uso |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Perfil de produção |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL |
| `JWT_SECRET` (≥ 32 bytes), `ADMIN_SENHA` (≥ 12) | Obrigatórios, vindos de cofre |
| `CORS_ALLOWED_ORIGINS`, `FRONTEND_URL` | Front-end |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP (sem isso e-mails ficam registrados como não enviados) |
| `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_TOKEN` | WhatsApp Cloud API (Meta): envio automático do acesso do cliente. Templates em `docs/api.md` |
| `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`, `TRACING_SAMPLING` | Traces OTLP |
| `RETENTION_NOTIFICATION_DAYS`, `RETENTION_AUDIT_DAYS` | Retenção LGPD |

## Roadmap

1. ✅ Fundação: Maven multi-módulo, Spring Modulith, Flyway, segurança, CI
2. ✅ Cadastros: clientes, instrumentos, produtos, catálogo (multi-tenant)
3. ✅ Núcleo: ordens de serviço, orçamento, portal do cliente, estoque via eventos
4. ✅ Complementos: notificações, relatórios, observabilidade
5. ✅ Endurecimento: LGPD, auditoria, testes de carga, revisão de segurança
