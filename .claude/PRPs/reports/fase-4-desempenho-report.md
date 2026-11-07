# Implementation Report: Fase 4, desempenho (SRV-02, SRV-03, APP-04)

## Summary
Os três achados foram implementados com TDD, cada um na sua branch e worktree (`.claude/worktrees/<branch>`), a partir de `develop` (`7a16eaf`):
- **APP-04:** batch fetch de 50, batch JDBC de 50 e INSERTs/UPDATEs ordenados em `application.yaml`.
- **SRV-02:** a listagem de OS calcula os totais da página numa consulta JPQL só (`totalsOf`) e não carrega mais itens nem peças.
- **SRV-03:** o "a receber" virou uma agregação JPQL escalar (`sumReceivable`), sem carregar OS.

Revisadas (sem CRÍTICO nem ALTO; dois BAIXOS aceitos: a expressão de saldo repetida na JPQL do `sumReceivable`, porque JPQL não tem tabela derivada, e o `stableStatementCount` repetido nos dois testes) e commitadas localmente: `8791ee9` (APP-04), `81ce2f9` (SRV-02) e `b82aa07` (SRV-03). Ainda não publicadas no GitHub.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Medium | Medium |
| Confidence | 8/10 | Implementado em uma passada; nenhum fallback foi necessário (`0bd`, projeção por construtor e subquery dentro do `sum` funcionaram no H2) |
| Files Changed | 10 | 12 (contando `docs/auditoria.md` em cada uma das 3 branches) |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | APP-04: batch do Hibernate | done | Sem teste novo (configuração); suíte inteira verde |
| 2 | SRV-02: teste vermelho | done | 2 de 3 vermelhos antes: 3 itens carregados; 25 statements com 12 OS contra 5 com 2 |
| 3 | SRV-02: `OrderTotals` | done | |
| 4 | SRV-02: `totalsOf` | done | `0bd` aceito pelo Hibernate 7.4 |
| 5 | SRV-02: `summary` e `search` | done | Página vazia não consulta (método privado `totalsOf(Page)`, `Map.of()`) |
| 6 | SRV-03: teste vermelho | done | 2 de 3 vermelhos antes: 4 OS carregadas; 21 statements contra 11 depois de +5 OS entregues |
| 7 | SRV-03: `sumReceivable` | done | Aliases `i2`/`p2` nas subqueries do `where` desde o início |
| 8 | k6 e documentação | parcial | `/reports/financial` no grupo do balcão; ✔ na auditoria. **Medição k6 antes e depois não executada** (sem k6 nem Docker) |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | Compilação nas 3 branches |
| Testes da fase | Pass | `OrderListQueryCountTest` (3), `FinancialReceivableTest` (3), `ServiceOrderFlowTest` (5) |
| Build | Pass | `mvnw -B -ntp verify` BUILD SUCCESS: APP-04 (44 testes), SRV-02 (47), SRV-03 (47); 1 skip em cada (`PostgresMigrationTest`, sem Docker) |
| Integração com a fase 3 | Pass | Worktree descartável com as 4 branches da fase 3 mergeadas + as 3 da fase 4 aplicadas: `verify` verde, 54 testes. Cobre o GOTCHA do batch JDBC × SH-02 (`GlobalExceptionHandlerTest`, `RegistrationFlowTest`, `StockConsumptionIdempotencyTest`) e o SRV-01 com os testes novos |
| Edge Cases | Pass | OS sem itens (total 0), página vazia, outro tenant, entregue sem quitação, status não faturável, saldo 0 |
| PostgreSQL | **Não executado** | As JPQL novas só rodaram no H2 |
| k6 | **Não executado** | k6 e Docker não instalados |

## Files Changed

| Branch | Arquivo | Ação | Linhas |
|---|---|---|---|
| `perf/app-04-hibernate-batch` | `srv-luthier-app/src/main/resources/application.yaml` | UPDATED | +8 |
| | `docs/auditoria.md` | UPDATED | +1 / −1 |
| `perf/srv-02-order-list-totals` | `srv-luthier-service/.../domain/records/OrderTotals.java` | CREATED | +22 |
| | `srv-luthier-service/.../domain/records/OrderResponse.java` | UPDATED | +4 / −4 |
| | `srv-luthier-service/.../domain/repositories/ServiceOrderRepository.java` | UPDATED | +12 |
| | `srv-luthier-service/.../services/impl/ServiceOrderServiceImpl.java` | UPDATED | +18 / −2 |
| | `srv-luthier-app/src/test/java/com/luthier/OrderListQueryCountTest.java` | CREATED | +177 |
| | `docs/auditoria.md` | UPDATED | +1 / −1 |
| `perf/srv-03-receivable-aggregate` | `srv-luthier-service/.../domain/repositories/ServiceOrderRepository.java` | UPDATED | +17 / −6 |
| | `srv-luthier-service/.../services/impl/ReportServiceImpl.java` | UPDATED | +1 / −5 |
| | `srv-luthier-app/src/test/java/com/luthier/FinancialReceivableTest.java` | CREATED | +194 |
| | `load-tests/k6-luthieria.js` | UPDATED | +1 |
| | `docs/auditoria.md` | UPDATED | +1 / −1 |

## Deviations from Plan
1. **SRV-03, teste 2:** ficou só o assert de `ServiceOrderEntity` não carregada. O plano admitia tirar o `getCollectionFetchCount()`, e o assert por entidade já falhava antes da correção.
2. **SRV-02, `search`:** usei direto a forma com método privado `totalsOf(Page)` que o plano recomendava, em vez do `if` com dois `return`.
3. **Validação extra:** além do previsto, rodei a suíte com a fase 3 inteira junto (worktree descartável, já removida) para fechar o risco do batch JDBC × SH-02 antes do rebase.

## Issues Encountered
- Nenhum erro de implementação. Os dois testes de contagem usam medição "estável" (repete até duas leituras iguais), porque as estatísticas do Hibernate são globais e os listeners assíncronos de notificação somam consultas.
- `.claude/worktrees/` não está no `.gitignore`; foi acrescentado só em `.git/info/exclude` (local) para as worktrees não aparecerem como não rastreadas.

## Tests Written

| Test File | Tests | Coverage |
|---|---|---|
| `srv-luthier-app/src/test/java/com/luthier/OrderListQueryCountTest.java` | 3 | Sem carga de itens/peças, consultas constantes com a página, totais da listagem iguais aos do detalhe (inclusive OS sem itens) |
| `srv-luthier-app/src/test/java/com/luthier/FinancialReceivableTest.java` | 3 | Valor do "a receber" (410,00 em 6 OS, 2 tenants), sem carga de OS, consultas constantes com o histórico |

## Next Steps
- [ ] Revisar as três worktrees (`/code-review`) e commitar cada branch (`/prp-commit`)
- [ ] Abrir os PRs depois do merge da fase 3 (ou rebase sobre `develop` quando ela entrar)
- [ ] Rodar k6 antes e depois e o `PostgresMigrationTest` quando houver k6/Docker
- [ ] A fase só fica `done` depois do merge dos três PRs
