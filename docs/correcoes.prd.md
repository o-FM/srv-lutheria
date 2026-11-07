# PRD: correção dos achados da auditoria

**Fonte:** `docs/auditoria.md` (2026-11-06, commit `24e5b08`) · **Criado em:** 2026-11-07 · **Escopo:** 65 achados (0 CRÍTICOS, 8 ALTOS, 33 MÉDIOS, 24 BAIXOS)

## Problema

A auditoria encontrou falhas de segurança, de isolamento entre luthierias, de integridade de dados e de desempenho. Nenhuma é explorável sem pré-condição, mas os 8 achados ALTOS têm pré-condições simples. Este PRD organiza a correção em fases com ordem, dependências e critérios de aceite verificáveis, para que cada fase vire um plano (`/ecc:plan docs/correcoes.prd.md`) e uma ou mais branches revisáveis.

## Objetivos

- Corrigir os 8 achados ALTOS primeiro, cada um com um teste de regressão que falhava antes da correção.
- Levar o banco a garantir os invariantes que hoje só a aplicação garante: FKs, CHECKs e unicidade.
- Manter os princípios do projeto: fronteiras entre módulos (`ModularityTest`), migrations que rodam em H2 e PostgreSQL e versões globais do Flyway.

## Fora do escopo

- Funcionalidades novas além do que o achado exige. O SEC-05 é a exceção: a confirmação de e-mail aparece como opção, mas depende de decisão.
- Mudanças no front-end (`srv-lutheria-front`). Quando um achado exigir contrato novo, isso fica registrado na fase.

## Regras gerais (valem para todas as fases)

1. **Uma branch por achado ou por grupo coeso**, sempre a partir de `develop`. Prefixos: `fix/` para defeitos, `change/` para mudanças de regra ou permissão, `perf/` para desempenho.
2. **Primeiro um teste que reproduz o problema e falha.** O teste só fica verde depois da correção. Achados ⚠ começam por um teste de caracterização.
3. **`./mvnw -B -ntp verify` verde.** O `ModularityTest` deve passar. Fases com migration exigem também o `PostgresMigrationTest` com Docker ligado.
4. **Revisão de código e de segurança** (esta quando o caminho for sensível) sem itens CRÍTICOS ou ALTOS em aberto.
5. **Migrations:** versão maior que todas as existentes em todos os módulos (hoje a maior é `V4_6`, na branch da fase 2). Cada uma fica na pasta do módulo dono da tabela. Toda FK ou CHECK nova sobre dados existentes vem acompanhada de uma consulta de pré-checagem para produção.
6. **Ao concluir:** marcar o achado como resolvido em `docs/auditoria.md` e atualizar `CLAUDE.md`, `docs/api.md` ou `docs/openapi.json` quando o comportamento documentado mudar.

## Decisões de negócio pendentes

> ⚠️ O pedido que gerou este PRD reservou um espaço para as decisões sobre SRV-01, SRV-05, SRV-06 e CUS-03, mas elas **não foram informadas**. As opções abaixo vêm da auditoria. Enquanto não houver decisão registrada aqui, os itens dependentes ficam **bloqueados**. O SRV-01 foi decidido em 2026-11-07. SRV-05, SRV-06 e CUS-03 foram decididos em 2026-11-07, o que desbloqueia a fase 6.

| ID | Pergunta | Opção A | Opção B | Decisão |
|---|---|---|---|---|
| SRV-01 | O que acontece quando uma edição deixaria o desconto maior que o subtotal ou o valor pago maior que o total? | Recusar a edição com 409, usando um `assertTotalsConsistent()` em `removeService`, `removePart`, `updateDetails` e `sendQuote` | Proibir a remoção de itens quando `paidAmount > 0` | **A e B juntas** (2026-11-07) |
| SRV-01 | Em quais status a OS pode receber pagamento? | Qualquer status exceto `CANCELADO`, como hoje, protegido pela regra acima | Só depois do orçamento aprovado (a partir de `EM_EXECUCAO`) | **B: só em `EM_EXECUCAO`, `PRONTO` e `ENTREGUE`** (2026-11-07) |
| SRV-05 | Sair de `AGUARDANDO_APROVACAO` por `changeStatus` sem resposta do cliente | Encerrar o orçamento automaticamente (`REJEITADO`, com motivo registrado) | Proibir a transição para `ORCAMENTO` e exigir `rejectQuote` | **A** (2026-11-07), vale também para `→ CANCELADO` |
| SRV-06 | Peça da OS desativada no estoque antes do `PRONTO` | Permitir a baixa de produto inativo | Permitir remover ou trocar a peça em `EM_EXECUCAO` | **A** (2026-11-07), só para peça que já está na OS |
| CUS-03 | Desativar um cliente que tem OS aberta | Recusar com 409 | `findCustomer` passa a incluir inativos; a checagem de ativo fica só na abertura de OS | **A** (2026-11-07) |
| SEC-17 | O mesmo CPF/CNPJ pode abrir mais de uma luthieria? | Pode (aceitar o achado) | Não: `unique (document)` | **A: pode**, filiais do mesmo dono (2026-11-07) |
| PRO-05 | SKU de produto desativado | Reativar o produto (`POST /products/{id}/activate`, 409 indica o id) | Liberar o SKU de inativos | **A** (2026-11-07) |
| SRV-12 | Quando o token do portal deixa de funcionar | N dias depois de a OS ser entregue ou cancelada (padrão 30) | Logo ao entregar ou cancelar | **A**, e o token sai dos eventos e dos spans (2026-11-07) |
| SRV-14 | Retenção de clientes desativados | Anonimizar automaticamente após 5 anos inativo (configurável) e incluir notificações na exportação | Só incluir notificações | **A** (2026-11-07) |

## Implementation Phases

| # | Fase | Status | Achados | Depende de |
|---|---|---|---|---|
| 1 | Segurança | done | SEC-01, NOT-01, SEC-02 | — |
| 2 | Isolamento de tenant | in-progress (mergeada; falta PostgreSQL e pré-checagem em produção) | SH-01, APP-03, NOT-03 | — |
| 3 | Integridade | in-progress | SRV-01, PRO-01, SH-02, APP-01 | 2 (só o PRO-01) |
| 4 | Desempenho | in-progress (mergeada; falta k6) | SRV-02, SRV-03, APP-04 | 3 |
| 5 | Migration de hardening | in-progress | SEC-09, SEC-10, SEC-11, CUS-05, PRO-02, PRO-04, SRV-07, SRV-08, SRV-09, NOT-04 | 2, 3 |
| 6 | MÉDIOS restantes | in-progress | SEC-03, SEC-04, SEC-05, SEC-06, SEC-07, SEC-08, SEC-12, CUS-01, CUS-02, CUS-03, CUS-04, PRO-03, SRV-04, SRV-05, SRV-06, SRV-10, SRV-11, NOT-02, APP-02 | 1, 3, 5; decisões SRV-05, SRV-06, CUS-03 |
| 7 | BAIXOS | in-progress (plano pronto; implementação depois da fase 6) | SH-03, SH-04, SH-05, SEC-13, SEC-14, SEC-15, SEC-16, SEC-17, SEC-18, CUS-06, PRO-05, PRO-06, SRV-12, SRV-13, SRV-14, SRV-15, NOT-05, NOT-06, APP-05, APP-06, APP-07, APP-08, APP-09 | 6 (por prioridade, não por dependência técnica) |

Cobertura: 3 + 3 + 4 + 3 + 10 + 19 + 23 = **65 achados**. O APP-04, que é BAIXO, foi antecipado para a fase 4 porque é a mitigação do N+1.

---

### Fase 1: Segurança

- **Status:** done (2026-11-07)
- **Achados:** SEC-01 (ALTO), NOT-01 (ALTO), SEC-02 (ALTO)
- **Dependências:** nenhuma.
- **Situação atual:** mergeada em `develop`: SEC-02 no PR #14, NOT-01 no PR #15 e SEC-01 no PR #17. O SEC-19, achado na revisão desta fase, não entrou nela e foi para o plano da fase 6.
- **Critérios de aceite:**
  - [x] SEC-01: as variantes codificadas (`/auth/%6cogin`, `/%70ortal/...`) passam pelo mesmo balde da rota canônica e recebem 429 ao esgotá-lo (`RateLimitBypassTest`).
  - [x] NOT-01: o ESTOQUISTA só lista o canal `interno`; pedir `email` ou `whatsapp` dá 403, e `markSent` de mensagem externa dá 404. O balcão continua com o link wa.me (`NotificationFlowTest`). A matriz em `docs/api.md` está atualizada.
  - [x] SEC-02: dois logins simultâneos com pool de 2 conexões terminam com 200/401 e auditoria gravada; a senha é checada fora de transação (`LoginConnectionPoolTest`).
  - [x] Os três PRs foram revisados e mergeados em `develop`, com `verify` verde no CI.

### Fase 2: Isolamento de tenant

- **Status:** in-progress (código mergeado; faltam os dois últimos critérios)
- **Achados:** SH-01 (ALTO), APP-03 (MÉDIO), NOT-03 (MÉDIO)
- **Dependências:** nenhuma.
- **Situação atual:** mergeada em `develop` pelo PR #16 (`fix/sh-01-tenant-fail-closed`), com as migrations `V4_2` a `V4_6`. A fase só fica `done` depois do `PostgresMigrationTest` e da pré-checagem de órfãos em produção.
- **Critérios de aceite:**
  - [x] Sem tenant no contexto, as leituras de negócio voltam vazias e gravar um `TenantScopedEntity` lança `IllegalStateException` (`TenantIsolationTest`).
  - [x] A visão de todas as luthierias só existe dentro de `TenantContext.callAsPlatform`, com aninhamento e restauração corretos, inclusive depois de exceção (`TenantContextTest`).
  - [x] Um JWT com `tid` igual a `ROOT` ou `NO_TENANT` recebe 403.
  - [x] Toda tabela com `tenant_id`, exceto `audit_log_tb`, tem FK para `tenant_tb`, e o teste falha para uma tabela nova sem FK.
  - [x] O expurgo de notificações remove as antigas de todas as luthierias e preserva as recentes (`RetentionJobFlowTest`). O índice `idx_notification_created` existe.
  - [ ] O **`PostgresMigrationTest` passou com Docker ligado**. Isso ainda não foi feito.
  - [ ] Antes do deploy, a pré-checagem de órfãos (comentário nas migrations `V4_3` a `V4_6`) deu 0 em produção.

### Fase 3: Integridade

- **Status:** in-progress
- **Plano:** `.claude/PRPs/plans/completed/fase-3-integridade.plan.md`
- **Relatório:** `.claude/PRPs/reports/fase-3-integridade-report.md` (implementado nas 4 branches `fix/srv-01-order-totals`, `fix/sh-02-integrity-409`, `fix/app-01-no-default-profile` e `fix/pro-01-idempotent-stock-consumption`; a fase só fica `done` depois do merge)
- **Achados:** SRV-01 (ALTO), PRO-01 (ALTO), SH-02 (MÉDIO), APP-01 (MÉDIO)
- **Dependências:**
  - **Fase 2:** só o PRO-01 depende dela, porque usa a migration `V4_7` e a tabela nova precisa da FK de tenant. O SH-02 também passa a converter em 409 as violações das FKs criadas na fase 2.
  - **Decisão SRV-01:** tomada em 2026-11-07 (ver "Decisões de negócio pendentes").
- **Desvio de desenho (PRO-01):** a idempotência vai por OS, num marcador `stock_consumption_tb (tenant_id, order_id)` único, em vez do índice `(tenant_id, order_id, product_id, type)` em `stock_movement_tb`. Motivo: o movimento manual aceita `orderId` livre (PRO-06), o que faria a baixa real ser pulada ou a migration falhar.
- **Critérios de aceite:**
  - [ ] SRV-01: remover serviço ou peça, editar o desconto ou enviar o orçamento nunca deixa `grandTotal()` ou `balance()` negativos. O comportamento segue a regra registrada em "Decisões de negócio pendentes". Os cenários "desconto depois de remover itens" e "sinal pago depois de remover itens" têm teste de entidade, sem Spring.
  - [ ] PRO-01: reentregar o mesmo `StockConsumptionRequested` não baixa o estoque de novo. O marcador único por OS existe na aplicação e no banco (`uk_stock_consumption_order`). O teste reprocessa o evento e confere o saldo e o número de movimentos.
  - [ ] SH-02: `DataIntegrityViolationException` responde 409 `application/problem+json`, sem detalhes internos. Nos cadastros (tenant, usuário), o `saveAndFlush` acontece antes do evento de auditoria, então não sobra auditoria "fantasma". Há teste com cadastro duplicado concorrente ou simulado.
  - [ ] APP-01: sem `SPRING_PROFILES_ACTIVE`, a aplicação não sobe com segredos de dev. O `spring.profiles.default: dev` foi removido, ou a subida é recusada quando o segredo é o de dev fora do perfil `dev`/`test`. O `README.md` e o `CLAUDE.md` explicam como ativar o dev localmente.

### Fase 4: Desempenho

- **Status:** in-progress (código mergeado; falta o critério do k6)
- **Plano:** `.claude/PRPs/plans/completed/fase-4-desempenho.plan.md`
- **Relatório:** `.claude/PRPs/reports/fase-4-desempenho-report.md` (mergeado em `develop`: SRV-02 no PR #18, APP-04 no PR #19 e SRV-03 no PR #20. O k6 e o PostgreSQL não foram executados. A fase só fica `done` depois do antes e depois do k6)
- **Achados:** SRV-02 (ALTO), SRV-03 (ALTO), APP-04 (BAIXO, antecipado)
- **Dependências:**
  - **Fase 3:** o "a receber" agregado do SRV-03 precisa refletir os invariantes do SRV-01, ou seja, saldo nunca negativo.
  - **APP-04:** não depende de nada e pode entrar primeiro, como mitigação.
- **Critérios de aceite:**
  - [x] SRV-02: a listagem de uma página de 20 OS faz um número constante de consultas, que não cresce com o tamanho da página. Isso é medido com estatísticas do Hibernate ou um contador de SQL no teste.
  - [x] SRV-03: o relatório financeiro calcula o "a receber" com agregação em JPQL, mantendo o filtro de tenant (sem SQL nativo). O resultado é igual ao cálculo atual num cenário com várias OS, e o custo não cresce com o histórico de OS `ENTREGUE`.
  - [x] APP-04: `default_batch_fetch_size`, `jdbc.batch_size`, `order_inserts` e `order_updates` configurados em `application.yaml`, com a suíte inteira verde.
  - [ ] Os testes de carga do k6 (`load-tests/README.md`) mostram antes e depois para a listagem de OS e o relatório.

### Fase 5: Migration de hardening

- **Status:** in-progress
- **Plano:** `.claude/PRPs/plans/completed/fase-5-hardening.plan.md` (branch única `fix/db-hardening`, migrations `V5_0` a `V5_4`; o merge exige a fase 3 mergeada antes, por causa da `V4_7`)
- **Relatório:** `.claude/PRPs/reports/fase-5-hardening-report.md` (implementado, ainda sem commit; `verify` verde sozinho e junto com a fase 3. **Falta o `PostgresMigrationTest` com Docker**, obrigatório nesta fase)
- **Achados:** SEC-09, SEC-10, SEC-11, CUS-05, PRO-02, PRO-04, SRV-07, SRV-08, SRV-09, NOT-04 (todos MÉDIOS)
- **Dependências:**
  - **Fase 2:** já existe FK `tenant_id`, e as versões vão até `V4_6`.
  - **Fase 3:** o PRO-01 cria índice em `stock_movement_tb`, e os CHECKs monetários do SRV-07 pressupõem os invariantes do SRV-01.
- **Observação:** a auditoria sugere "uma única migration". Isso conflita com a regra do projeto de que cada módulo publica as migrations das próprias tabelas. A proposta é uma migration por módulo, com versões consecutivas (por exemplo `V5_0`, security, a `V5_4`, notification), todas na mesma branch e no mesmo PR. O SEC-09 (normalizar o e-mail no login e no cadastro), o SEC-11 (purge em lotes) e o NOT-04 (`scrub` com um único `@Modifying update`) exigem também mudança de código, que entra nesta fase.
- **Critérios de aceite:**
  - [ ] Cada CHECK, FK e índice listado nos 10 achados existe no schema, e um teste por constraint grava um valor inválido e espera rejeição. O teste de metadados no estilo do `TenantIsolationTest` cobre FKs e índices.
  - [ ] As FKs compostas `(tenant_id, …)` do CUS-05 e do SRV-07 impedem que a filha de um tenant aponte para o pai de outro.
  - [ ] SEC-09: o login usa índice (e-mail gravado em minúsculas, mais `ck_user_email_lower`), e o fluxo de cadastro e login com maiúsculas continua funcionando.
  - [ ] SEC-11: o purge da auditoria apaga em lotes, com uma transação por lote, e tem teste com dados de duas luthierias.
  - [ ] Há pré-checagem de dados que violariam cada constraint nova, documentada na migration. As migrations rodam no H2 e no PostgreSQL, com o `PostgresMigrationTest` verde.

### Fase 6: MÉDIOS restantes

- **Status:** in-progress
- **Plano:** `.claude/PRPs/plans/fase-6-medios.plan.md` (7 branches, migrations `V6_0` a `V6_3`; inclui também SEC-19, SEC-20 e SH-06 de "Achados surgidos". A implementação só começa depois do merge das fases 3 e 5)
- **Achados (19):**

  | Tema | Achados |
  |---|---|
  | Autenticação e conta | SEC-03, SEC-04, SEC-05, SEC-06, SEC-07, SEC-08 |
  | Testes de autenticação | SEC-12 |
  | Clientes e LGPD | CUS-01, CUS-02, CUS-03, CUS-04 |
  | Produtos | PRO-03 |
  | Ordens de serviço | SRV-04, SRV-05, SRV-06, SRV-10 |
  | Testes de regras de negócio | SRV-11 |
  | Notificações | NOT-02 |
  | Outbox | APP-02 |

- **Dependências:**
  - **Fase 1:** o SEC-12 cobre com testes os fluxos corrigidos na fase 1.
  - **Fase 3:** o SRV-04 depende do 409 do SH-02, e o SRV-11 testa SRV-01 e PRO-01.
  - **Fase 5:** o CUS-04 e o PRO-03 mexem nas mesmas buscas que podem ganhar índice.
  - **Decisões:** SRV-05, SRV-06 e CUS-03 decididos em 2026-11-07, todos pela opção A (ver "Decisões de negócio pendentes").
- **Critérios de aceite:**
  - [ ] Cada achado tem teste de regressão que falhava antes da correção.
  - [ ] SEC-12: existem `AuthFlowTest` (refresh, reuso, logout, reset), `UserManagementFlowTest` (isolamento, último ADMIN, revogação) e um teste do rate limit com capacidade real (429).
  - [ ] SEC-06: nenhuma operação deixa a luthieria sem ADMIN ativo (409).
  - [ ] CUS-01: a busca por CPF só aceita os 11 dígitos completos.
  - [ ] CUS-02: a anonimização limpa instrumentos, textos livres das OS e o campo `error` das notificações.
  - [ ] SRV-05, SRV-06 e CUS-03 seguem a decisão registrada neste PRD.
  - [ ] APP-02: o DDL do `event_publication` está conferido contra o schema oficial do Spring Modulith 2.1 e roda no PostgreSQL. `serialized_event` comporta um evento com muitas peças.
  - [ ] As mudanças de contrato (status HTTP, campos) estão refletidas em `docs/api.md` e `docs/openapi.json`.

### Fase 7: BAIXOS

- **Status:** in-progress
- **Plano:** `.claude/PRPs/plans/fase-7-baixos.plan.md` (6 branches, migrations `V7_0` a `V7_4`; inclui SH-07 e SEC-21 de "Achados surgidos". A implementação só começa depois do merge da fase 6)
- **Achados (23):**

  | Tema | Achados |
  |---|---|
  | Shared | SH-03, SH-04, SH-05 |
  | Security | SEC-13, SEC-14, SEC-15, SEC-16, SEC-17, SEC-18 |
  | Customer | CUS-06 |
  | Product | PRO-05, PRO-06 |
  | Service | SRV-12, SRV-13, SRV-14, SRV-15 |
  | Notification | NOT-05, NOT-06 |
  | App e infraestrutura | APP-05, APP-06, APP-07, APP-08, APP-09 |

- **Dependências:** nenhuma dependência técnica forte. A fase vem depois da 6 por prioridade.
- **Observações:**
  - SH-05 já está parcialmente coberto: o `TenantContextTest` da fase 2 testa o `TenantContext`. Faltam `Masks.email` e o `GlobalExceptionHandler`.
  - SEC-18 inclui corrigir o `CLAUDE.md`, que afirma que o `/h2-console` funciona em dev.
  - SEC-17, PRO-05, SRV-12 e SRV-14 dependiam de regra de negócio. As decisões foram tomadas em 2026-11-07 (ver "Decisões de negócio pendentes").
- **Critérios de aceite:**
  - [ ] Cada achado foi corrigido, ou aceito com a justificativa registrada em `docs/auditoria.md`; a auditoria já marca SEC-13 e NOT-06 como aceitáveis.
  - [ ] As mudanças de infraestrutura (APP-06, APP-07, APP-08) foram validadas: `docker compose up` funciona só em localhost, o CI passa com actions fixadas por SHA e o Trivy varre a imagem.
  - [ ] `verify` verde e documentação alinhada.

---

## Achados surgidos durante as correções (fora das fases, a classificar)

As revisões das fases 1 e 2 encontraram problemas que não estão em `docs/auditoria.md`. A proposta é registrá-los lá com IDs novos e encaixá-los numa fase:

| Proposta de ID | Severidade | Achado | Fase sugerida |
|---|---|---|---|
| SEC-19 | ALTO | `/auth/register`, que é público, tem o mesmo deadlock do SEC-02: gera o hash Argon2 dentro de `@Transactional` e audita com `REQUIRES_NEW` | 1 (é o mesmo defeito do SEC-02) |
| SEC-20 | MÉDIO | `/auth/reset-password` e `UserManagementServiceImpl` têm o mesmo padrão do SEC-02 | 6 |
| SH-06 | MÉDIO | Nenhuma regra de arquitetura (ArchUnit ou teste) restringe quem pode chamar `TenantContext.callAsPlatform` | 6 |
| SH-07 | BAIXO | Um evento sem `tenantId` cai no modo restrito sem log | 7 |
| SEC-21 | BAIXO | No login, o usuário desativado entre a autenticação e a emissão ainda recebe um access token de 15 minutos | 7 |

## Validação

```bash
./mvnw -B -ntp verify                       # suíte completa, como no CI
# com Docker ligado, em fases com migration:
./mvnw -pl srv-luthier-app -am test -Dtest=PostgresMigrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

## Riscos

| Risco | Probabilidade | Mitigação |
|---|---|---|
| Dados de produção que violam as FKs ou CHECKs novos fazem a migration falhar no deploy | Média | Pré-checagem documentada em cada migration e rodada antes do deploy |
| As decisões de negócio pendentes travam as fases 3 e 6 | Alta | Registrar as decisões neste PRD antes de iniciar o plano dessas fases |
| As migrations nunca rodaram no PostgreSQL (sem Docker no ambiente atual) | Alta | Tornar o `PostgresMigrationTest` obrigatório nas fases 2, 3 e 5 |
| Conflito de versão de migration entre branches paralelas | Média | Reservar as versões na descrição de cada fase: fase 3 a partir de `V4_7`, fase 5 a partir de `V5_0` |
