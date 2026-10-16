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

## Documentação

- [API, multi-tenancy e ciclo da OS](docs/api.md) · [OpenAPI](docs/openapi.json)
- [Revisão de segurança e LGPD](docs/security-review.md)
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
| `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`, `TRACING_SAMPLING` | Traces OTLP |
| `RETENTION_NOTIFICATION_DAYS`, `RETENTION_AUDIT_DAYS` | Retenção LGPD |

## Roadmap

1. ✅ Fundação: Maven multi-módulo, Spring Modulith, Flyway, segurança, CI
2. ✅ Cadastros: clientes, instrumentos, produtos, catálogo (multi-tenant)
3. ✅ Núcleo: ordens de serviço, orçamento, portal do cliente, estoque via eventos
4. ✅ Complementos: notificações, relatórios, observabilidade
5. ✅ Endurecimento: LGPD, auditoria, testes de carga, revisão de segurança
