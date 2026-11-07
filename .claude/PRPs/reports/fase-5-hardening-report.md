# Implementation Report: Fase 5, migration de hardening

## Summary
Os 10 achados (SEC-09, SEC-10, SEC-11, CUS-05, PRO-02, PRO-04, SRV-07, SRV-08, SRV-09, NOT-04) foram implementados com TDD na branch única `fix/db-hardening` (worktree `.claude/worktrees/db-hardening`), a partir de `develop` (`12f86a3`):
- **5 migrations**, uma por módulo: `V5_0` (security), `V5_1` (customer), `V5_2` (product), `V5_3` (service), `V5_4` (notification). Cada uma traz no cabeçalho as consultas de pré-checagem para produção.
- **SEC-09:** e-mail normalizado em `UserEntity.normalizeEmail` (inclusive no `AdminBootstrap`, via `newUser`) e buscado por igualdade (`findByEmail`/`existsByEmail`).
- **SEC-11:** expurgo da auditoria em lotes (`luthieria.retention.audit-batch-size`, padrão 5.000), uma transação por lote.
- **NOT-04:** anonimização das notificações num único `update versioned`, restrito à luthieria do evento.

As mudanças estão **sem commit**, aguardando revisão.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Confidence | 8/10 | Implementado em uma passada, sem nenhum fallback (o `update versioned` e o `set updatedAt` funcionaram) |
| Files Changed | ~26 | 28 (654 linhas a mais, 42 a menos) |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | `SchemaHardeningTest` vermelho | done | 8 de 8 vermelhos antes das migrations. Nas FKs compostas, a mesma escrita é aceita antes com a luthieria certa (controle positivo embutido) |
| 2 | `V5_0` security | done | |
| 3 | `V5_1` customer | done | |
| 4 | `V5_2` product | done | Com a ordem "troca o índice antes de recriar a FK", descoberta no planejamento |
| 5 | `V5_3` service | done | |
| 6 | `V5_4` notification | done | `SchemaHardeningTest` 8 de 8 verde depois das migrations |
| 7 | `@Index` das entidades | done | |
| 8 | SEC-09 | done | `UserEmailNormalizationTest` vermelho antes (não compilava: `normalizeEmail` não existia); teste de fluxo com maiúsculas é regressão |
| 9 | SEC-11 | done | 6 linhas antigas apagadas com lote de 2 (4 rodadas); a recente ficou |
| 10 | NOT-04 | done | Outra luthieria com o mesmo `customer_id` não foi tocada: o filtro de tenant vale também no UPDATE em massa |
| 11 | Documentação e suíte | done | `CLAUDE.md` (versões), `README.md` (variável nova), `docs/auditoria.md` (✔ nos 10 e notas de resolução no PRO-04 e no SRV-07) |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | Compilação de todos os módulos |
| Testes da fase | Pass | `SchemaHardeningTest` (8), `UserEmailNormalizationTest` (2), `RegistrationFlowTest` (9), `RetentionJobFlowTest` (2), `PrivacyAndSecurityTest` (5), `TenantIsolationTest` (10), `ServiceOrderFlowTest` (5), `LoginConnectionPoolTest` (3), `RateLimitBypassTest` (5) |
| Build | Pass | `mvnw -B -ntp verify`: 63 testes, 1 pulado (`PostgresMigrationTest`, sem Docker) |
| Integração com a fase 3 | Pass | Worktree descartável com as 4 branches da fase 3 mergeadas + esta branch: `verify` verde, 67 testes. Confirma a ordem `V4_7` → `V5_x` e o SH-02/SRV-01 junto com as constraints novas. Aplicou sem conflito |
| PostgreSQL | **Não executado** | Sem Docker. **O PRD exige o `PostgresMigrationTest` nesta fase; a fase não fecha sem ele** |
| Edge Cases | Pass | AJUSTE com quantidade 0 aceito; lote vazio não chama o delete; luthieria diferente com o mesmo `customer_id` intocada |

## Files Changed

| File | Action | Lines |
|---|---|---|
| `srv-luthier-security/.../db/migration/security/V5_0__security_hardening.sql` | CREATED | +20 |
| `srv-luthier-customer/.../db/migration/customer/V5_1__customer_tenant_fk.sql` | CREATED | +11 |
| `srv-luthier-product/.../db/migration/product/V5_2__product_hardening.sql` | CREATED | +26 |
| `srv-luthier-service/.../db/migration/service/V5_3__service_hardening.sql` | CREATED | +54 |
| `srv-luthier-notification/.../db/migration/notification/V5_4__notification_customer_index.sql` | CREATED | +3 |
| `srv-luthier-security/.../domain/entities/UserEntity.java` | UPDATED | +10 / −1 |
| `srv-luthier-security/.../domain/repositories/UserRepository.java` | UPDATED | +3 / −2 |
| `srv-luthier-security/.../domain/repositories/AuditLogRepository.java` | UPDATED | +8 / −2 |
| `srv-luthier-security/.../jobs/AuditRetentionJob.java` | UPDATED | +44 / −7 |
| `srv-luthier-security/.../services/impl/AuthServiceImpl.java` | UPDATED | +3 / −3 |
| `srv-luthier-security/.../services/impl/UserServiceImpl.java` | UPDATED | +2 / −1 |
| `srv-luthier-security/.../services/impl/PasswordRecoveryServiceImpl.java` | UPDATED | +1 / −1 |
| `srv-luthier-security/.../services/impl/TenantRegistrationServiceImpl.java` | UPDATED | +1 / −1 |
| `srv-luthier-security/.../services/impl/UserManagementServiceImpl.java` | UPDATED | +1 / −1 |
| `srv-luthier-notification/.../domain/entities/NotificationEntity.java` | UPDATED | +3 / −6 |
| `srv-luthier-notification/.../domain/repositories/NotificationRepository.java` | UPDATED | +14 / −1 |
| `srv-luthier-notification/.../listeners/NotificationListeners.java` | UPDATED | +2 / −1 |
| `srv-luthier-product/.../domain/entities/StockMovementEntity.java` | UPDATED | +2 / −1 |
| `srv-luthier-service/.../domain/entities/ServiceOrderEntity.java` | UPDATED | +2 / −1 |
| `srv-luthier-app/src/main/resources/application.yaml` | UPDATED | +2 |
| `srv-luthier-app/src/test/java/com/luthier/SchemaHardeningTest.java` | CREATED | +269 |
| `srv-luthier-app/src/test/java/com/luthier/UserEmailNormalizationTest.java` | CREATED | +29 |
| `srv-luthier-app/src/test/java/com/luthier/RegistrationFlowTest.java` | UPDATED | +30 |
| `srv-luthier-app/src/test/java/com/luthier/RetentionJobFlowTest.java` | UPDATED | +47 / −1 |
| `srv-luthier-app/src/test/java/com/luthier/PrivacyAndSecurityTest.java` | UPDATED | +52 |
| `CLAUDE.md`, `README.md`, `docs/auditoria.md` | UPDATED | +14 / −11 |

## Deviations from Plan
1. **SEC-09, `TenantRegistrationServiceImpl` e `UserManagementServiceImpl`:** só troquei `existsByEmailIgnoreCase` por `existsByEmail`, sem passar a usar `UserEntity.normalizeEmail` (o plano deixava opcional). Os dois já normalizavam igual (`strip().toLowerCase(Locale.ROOT)`), e mexer menos nesses arquivos reduz o conflito com o SH-02 da fase 3.
2. **`AuditRetentionJob`:** o construtor ficou package-private, como o plano previa caso o `Purger` (package-private) incomodasse.
3. **`docs/auditoria.md`:** além do ✔, entraram notas de resolução no PRO-04 (CHECK do AJUSTE 0) e no SRV-07 (FKs de cliente e instrumento e `estimated_days > 0`).

## Issues Encountered
- O `sed` que marca os títulos não casou com "MÉDIO" (o `[A-Z]` não pega o "É"). Troquei o padrão por `[^]]*`.
- Na validação combinada, o patch temporário foi gravado por engano em `projetos_pessoais/fase5.patch` (caminho relativo errado). Foi apagado logo depois, junto com a worktree descartável.
- Três testes são de regressão, não vermelhos (e-mail com maiúsculas no fluxo, lotes da auditoria, escopo da anonimização), porque o comportamento visível não muda. O vermelho de cada achado está no `SchemaHardeningTest` e no `UserEmailNormalizationTest`, como o plano previa.

## Tests Written

| Test File | Tests | Coverage |
|---|---|---|
| `SchemaHardeningTest` | 8 | CHECKs, FKs compostas (com controle positivo), FK dos tokens, unicidade do recibo, metadados de FK com tenant (vale para tabelas futuras), índices novos e removidos |
| `UserEmailNormalizationTest` | 2 | `newUser` normaliza; `normalizeEmail(null)` |
| `RegistrationFlowTest.emailIsCaseInsensitiveAndStoredInLowercase` | 1 | Cadastro e login com maiúsculas; duplicado em outra caixa dá 409; gravado minúsculo |
| `RetentionJobFlowTest.auditPurgeDeletesInBatchesAcrossWorkshops` | 1 | Expurgo em vários lotes, duas luthierias, registro recente preservado |
| `PrivacyAndSecurityTest.anonymizationScrubsOnlyThatCustomersNotificationsInThatWorkshop` | 1 | UPDATE único, versão incrementada, outro cliente e outra luthieria intactos |

## Next Steps
- [ ] Revisão de código e de segurança (o caminho de login mudou)
- [ ] Commit na `fix/db-hardening` e PR para `develop`. **O merge só depois da fase 3** (por causa da `V4_7`)
- [ ] Rebase sobre `develop` depois do merge da fase 3; trocar a lista de versões do `CLAUDE.md` para incluir `V4_7`
- [ ] `PostgresMigrationTest` com Docker (obrigatório pelo PRD)
- [ ] Rodar as pré-checagens dos cabeçalhos das migrations numa cópia do banco de produção antes do deploy
