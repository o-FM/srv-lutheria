# Implementation Report: Fase 3, integridade (SRV-01, PRO-01, SH-02, APP-01)

## Summary
Os quatro achados de integridade foram implementados com TDD, cada um na sua branch e na sua worktree (`.claude/worktrees/<id>`):
- o teste novo falhou antes da correção e passou depois;
- `./mvnw -B -ntp verify` verde nas quatro branches;
- revisões de código e de segurança sem CRÍTICO ou ALTO; o MÉDIO que as duas apontaram no PRO-01 foi corrigido.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Confidence | 8/10 | Implementado de uma vez; dois testes precisaram ser refeitos porque passavam no código antigo |
| Files Changed | ~15 | 25 (contando `docs/auditoria.md` nas 4 branches e 1 teste unitário a mais no PRO-01) |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | SRV-01: testes vermelhos de entidade | done | 5 de 10 vermelhos antes |
| 2 | SRV-01: invariantes | done | O desconto passou a ser validado antes de alterar qualquer campo em `updateDetails` (LOW da revisão) |
| 3 | SRV-01: integração e `docs/api.md` | done | Desvio: o caso de pagamento precisou de OS com saldo (ver Issues) |
| 4 | PRO-01: teste vermelho de reentrega | done | Desvio: reentrega sequencial, não simultânea (ver Issues) |
| 5 | PRO-01: marcador idempotente | done | Mais o `alreadyApplied` + relançamento (MÉDIO da revisão) |
| 6 | SH-02: teste do handler | done | MockMvc standalone funcionou sem configurar conversor |
| 7 | SH-02: handler + `saveAndFlush` | done | Mais o `log.warn` só com SQLState (LOW da revisão) |
| 8 | APP-01: perfil padrão | done | O jar sem perfil cai em `"default"` e falha por `JWT_SECRET` (validado à mão) |
| 9 | Documentação e status | done | `docs/auditoria.md` nas 4 branches; PRD e relatório na `docs/correcoes-prd` |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | Compilação em todas as branches |
| Unit Tests | Pass | `ServiceOrderEntityTest` (10), `GlobalExceptionHandlerTest` (3), `StockConsumptionListenerTest` (2), `ProfileDefaultsTest` (1) |
| Build | Pass | `mvnw verify` BUILD SUCCESS nas 4 branches |
| Integration | Pass | `ServiceOrderFlowTest` (+2 casos), `StockConsumptionIdempotencyTest`; `TenantIsolationTest` e `ModularityTest` verdes |
| Edge Cases | Pass | Desconto igual ao subtotal permitido; pagamento em `entregue`; dado legado em `sendQuote`; violação que não é corrida é relançada |
| PostgreSQL | **Não executado** | Sem Docker na máquina; a `V4_7` só foi validada no H2 |

## Files Changed

| Branch | Arquivos | Linhas |
|---|---|---|
| `fix/srv-01-order-totals` | `ServiceOrderEntity`, `OrderStatus`, `ServiceOrderEntityTest` (novo), `ServiceOrderFlowTest`, `docs/api.md`, `docs/auditoria.md` | +303 / −12 |
| `fix/sh-02-integrity-409` | `GlobalExceptionHandler`, `GlobalExceptionHandlerTest` (novo), `TenantRegistrationServiceImpl`, `UserManagementServiceImpl`, `docs/auditoria.md` | +114 / −2 |
| `fix/app-01-no-default-profile` | `application.yaml`, `src/test/resources/config/application.yaml` (novo), `ProfileDefaultsTest` (novo), `srv-luthier-app/pom.xml`, `README.md`, `CLAUDE.md`, `docs/auditoria.md` | +55 / −7 |
| `fix/pro-01-idempotent-stock-consumption` | `V4_7__stock_consumption.sql`, `StockConsumptionEntity`, `StockConsumptionRepository`, `StockConsumptionListener`, `StockConsumptionListenerTest` (novo), `StockConsumptionIdempotencyTest` (novo), `docs/auditoria.md` | +243 / −5 |

## Deviations from Plan
1. **PRO-01: base da branch.** Parte de `fix/sh-01-tenant-fail-closed`, porque a fase 2 ainda não foi mergeada. O plano previa isso como alternativa. Depois do merge da fase 2, é preciso fazer rebase sobre `develop`.
2. **PRO-01: o `catch` deixou de engolir qualquer violação.** O plano tratava todo `DataIntegrityViolationException` como "já aplicada". As duas revisões apontaram que uma violação que não é corrida (por exemplo, referência longa demais) faria a baixa sumir sem nova tentativa. Agora uma transação nova (`alreadyApplied`) confere o marcador: se ele existe, a exceção é ignorada; senão, é relançada para o outbox reprocessar. Coberto por `StockConsumptionListenerTest`.
3. **SH-02: sem teste da reordenação `saveAndFlush`.** Uma corrida real de cadastro no H2 não é determinística. O handler tem teste, e a reordenação foi conferida nas duas revisões.
4. **SH-02: log.** Acrescentado `log.warn` só com o SQLState, sem a mensagem do driver, que traz e-mail e CPF.

## Issues Encountered
- **Dois testes passavam no código antigo:**
  - SRV-01: a OS do "pagamento antes da aprovação" tinha saldo 0, então o 409 vinha do saldo e não da regra. Agora usa uma OS com serviço de 180, e o código antigo respondia 201.
  - PRO-01: as duas entregas simultâneas esbarravam no `@Version` do produto, e a segunda falhava; na produção, o outbox a reprocessaria e baixaria de novo. Agora a reentrega vem depois da primeira, e o código antigo deixava saldo 1 em vez de 3.
- **Hook GateGuard:** pediu fatos antes do primeiro Edit ou Write de cada arquivo. Algumas edições em lote entraram parcialmente e foram refeitas depois de conferir o estado.

## Tests Written

| Test File | Tests | Coverage |
|---|---|---|
| `srv-luthier-service/.../ServiceOrderEntityTest.java` | 10 | Invariantes de desconto, remoção, pagamento por status, dado legado |
| `ServiceOrderFlowTest.java` | +2 | SRV-01 pela API (409) |
| `srv-luthier-shared/.../GlobalExceptionHandlerTest.java` | 3 | 23505 e 23503 dão 409, 22001 dá 400, sem vazamento |
| `ProfileDefaultsTest.java` | 1 | Sem perfil padrão no `application.yaml` |
| `StockConsumptionIdempotencyTest.java` | 1 | Reentrega sequencial: uma baixa, um movimento |
| `srv-luthier-product/.../StockConsumptionListenerTest.java` | 2 | Corrida ignorada; outra violação relançada |

## Pendências levantadas nas revisões (não corrigidas)
- **MÉDIO (fora do escopo):** `docker-compose.yml:36-37` tem `JWT_SECRET` e `ADMIN_SENHA` fixos. A sugestão é `${VAR:?}` com `.env` fora do git. Fica junto com o APP-06, na fase 7.
- **LOW:** cancelar uma OS `em_execucao` com pagamento não registra estorno; está no "NOT Building" do plano. Candidato a achado novo.
- **LOW:** `StockConsumptionIdempotencyTest` usa `await().during(2s)` depois de uma chamada assíncrona. O ramo de "pular" fica coberto de forma determinística pelo teste unitário.
- **LOW:** o SH-02 responde 409 também para NOT NULL (23502) e CHECK (23514). Revisitar quando a fase 5 criar os CHECKs de domínio.

## Next Steps
- [ ] Commit das 4 branches (GATE 2)
- [ ] `PostgresMigrationTest` com Docker antes do merge do PRO-01
- [ ] PRs para `develop`; o PRO-01 só depois do merge da fase 2, com rebase
- [ ] Avisar o front: pagamento antes da aprovação do orçamento agora dá 409
