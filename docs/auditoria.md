# Auditoria do srv-luthieria

**Data:** 2026-11-06 · **Branch:** `develop` (commit `24e5b08`) · **Escopo:** todos os módulos Maven, mais migrations, configuração, Docker, compose e CI.

## Como foi feita

A auditoria foi somente leitura: nenhum arquivo do projeto foi alterado, e não houve build, testes nem execução contra banco. Rodaram quatro revisões independentes, cada uma instruída a aplicar as skills `springboot-patterns`, `jpa-patterns` e `springboot-security`:

| Agente | Foco |
|---|---|
| `java-reviewer` (1) | shared, security, app: correção, transações, padrões Spring/JPA, testes |
| `java-reviewer` (2) | customer, product, service, notification: regras de negócio, concorrência, eventos, N+1 |
| `database-reviewer` | migrations Flyway, entidades, repositórios, índices, constraints, jobs de retenção |
| `security-reviewer` | isolamento entre tenants, autenticação e autorização, rate limit, LGPD, infraestrutura |

Os achados repetidos entre agentes foram fundidos num só, com a severidade reconciliada. Os itens marcados com ✔ foram conferidos de novo no código durante a consolidação. Os itens marcados com ⚠ dependem de reprodução ou confirmação, e o teste sugerido está descrito no próprio achado.

Ressalva: nem todos os agentes leram as três skills por completo. Os checklists de segurança e de JPA foram cobertos, mas os padrões gerais de Spring Boot foram cobertos só em parte.

## Severidades

| Severidade | Critério | Qtde |
|---|---|---|
| **CRÍTICO** | Vazamento entre tenants explorável, bypass de autenticação, exposição de segredo ou perda de dados | **0** |
| **ALTO** | Bug provável ou vulnerabilidade com pré-condição simples | **8** |
| **MÉDIO** | Hardening relevante, risco de integridade ou desempenho, lacuna de teste | **33** |
| **BAIXO** | Boa prática, estilo, melhoria opcional | **24** |

Nenhuma revisão encontrou IDOR entre tenants, SQL injection, consulta nativa que escape do filtro de tenant ou segredo de produção no código.

## Prioridades (ALTO)

| ID | Módulo | Resumo |
|---|---|---|
| [SEC-01](#sec-01) | security | O rate limit compara a URI crua e pode ser contornado com path codificado (`/auth/%6cogin`) ⚠ |
| [SEC-02](#sec-02) | security | O login segura uma conexão durante o Argon2 e pede outra para a auditoria, o que pode esgotar o pool |
| [NOT-01](#not-01) | notification | Qualquer papel interno lê contatos dos clientes e tokens do portal, e com eles aprova ou recusa orçamentos |
| [SH-01](#sh-01) | shared | Sem tenant no contexto, o filtro de tenant é desligado (falha aberta) |
| [SRV-01](#srv-01) | service | O desconto e o valor pago podem passar do total da OS, deixando total ou saldo negativo |
| [PRO-01](#pro-01) | product | A baixa de estoque não é idempotente, e a reentrega do outbox baixa duas vezes |
| [SRV-02](#srv-02) | service | N+1 na listagem de OS (2 selects extras por OS) |
| [SRV-03](#srv-03) | service | O relatório financeiro carrega todo o histórico de OS em memória |

---

## srv-luthier-shared

<a id="sh-01"></a>
### [ALTO] SH-01: o isolamento de tenant falha aberto ✔
- **Status:** resolvido na branch `fix/sh-01-tenant-fail-closed`. A sugestão de lançar exceção no resolver foi trocada por um sentinela `NO_TENANT`, porque o Hibernate resolve o tenant ao abrir qualquer sessão, inclusive no login e no outbox. O `@PrePersist` barra a gravação sem tenant, e o escopo de plataforma passou a ser explícito. Testes: `TenantIsolationTest` e `TenantContextTest`.
- **Arquivo:** `srv-luthier-shared/src/main/java/com/luthier/shared/tenancy/TenantIdentifierResolver.java:24-26, 34-36`
- **Problema:** sem tenant no `TenantContext`, o resolver devolve `ROOT`, e `isRoot` faz o Hibernate parar de filtrar todas as entidades `@TenantId`. Hoje os caminhos sem tenant (portal, jobs de retenção) são intencionais e corretos. Mas um listener assíncrono novo que esqueça o `runAs`, ou um endpoint `permitAll` novo, passa a ler e gravar dados de **todas** as luthierias, sem erro nem log. Um INSERT nessa situação grava `tenant_id = 00000000-…`, e nenhuma FK impede isso (ver APP-03). `OrderCounterRepository.lockCurrent` (`select c from OrderCounterEntity c`, sem `where`) também passaria a enxergar o contador de outro tenant.
- **Sugestão:** falhar fechado. `resolveCurrentTenantIdentifier()` deve lançar `IllegalStateException` quando não houver tenant. Crie também um escopo de plataforma explícito, por exemplo `TenantContext.callAsPlatform(...)` com um `ThreadLocal<Boolean>`, usado só pelo lookup do portal e pelos jobs. Isso transforma qualquer esquecimento em erro visível em teste.

### [MÉDIO] SH-02: violação de unicidade vira HTTP 500 ✔
- **Arquivo:** `srv-luthier-shared/src/main/java/com/luthier/shared/errors/GlobalExceptionHandler.java:20-98`
- **Problema:** não existe handler para `DataIntegrityViolationException`. Os fluxos do tipo "checa se existe e depois insere" quebram quando duas requisições concorrentes passam juntas pela checagem: registro de tenant ou usuário (e-mail, CPF, slug), primeira OS do tenant (SRV-04) e SKU (PRO-05). O índice único dispara no flush e o cliente recebe 500. Além disso, o evento de auditoria já gravado em `REQUIRES_NEW` (`USER_CREATED`, `TENANT_REGISTERED`) fica na trilha como registro "fantasma".
- **Sugestão:** adicionar um handler que responda 409:
  ```java
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Registro duplicado ou em conflito. Recarregue e tente novamente."));
  }
  ```
  Nos serviços de cadastro, usar `saveAndFlush` **antes** de publicar o evento de auditoria.

### [BAIXO] SH-03: mensagens de erro em idiomas misturados
- **Arquivo:** `srv-luthier-shared/src/main/java/com/luthier/shared/errors/GlobalExceptionHandler.java:35, 45, 57` e `srv-luthier-security/src/main/java/com/luthier/security/services/impl/UserManagementServiceImpl.java:50, 52, 115`
- **Problema:** as mensagens alternam entre inglês e português: "Invalid Data", "Parametros inválidos" (sem acento), "Email already exists", "User not found!". O restante da API responde em pt-BR.
- **Sugestão:** padronizar em pt-BR, ou usar códigos estáveis (`type` do ProblemDetail) que o front traduz.

### [BAIXO] SH-04: chave primária UUIDv4 e entidades sem `equals`/`hashCode`
- **Arquivo:** entidades que estendem `srv-luthier-shared/src/main/java/com/luthier/shared/tenancy/TenantScopedEntity.java` (`@GeneratedValue(strategy = GenerationType.UUID)` em cada entidade)
- **Problema:** um UUID aleatório como PK fragmenta o índice B-tree no PostgreSQL. Nenhuma entidade define `equals`/`hashCode`. Isso é inofensivo hoje, porque as coleções são `List`, mas vira bug se alguém usar `Set`.
- **Sugestão:** usar `@UuidGenerator(style = UuidGenerator.Style.TIME)` (ordenável). Se um dia houver `Set`, implementar `equals`/`hashCode` pelo id.

### [BAIXO] SH-05: o núcleo do isolamento não tem teste unitário
- **Arquivo:** `srv-luthier-shared/src/test/java/com/luthier/shared/validation/CpfTest.java` (único teste do módulo)
- **Problema:** não há teste de `TenantContext.callAs/runAs` (aninhamento, restauração após exceção, restauração de `null`), `Masks.email` (sem `@`, `@` no início) nem `GlobalExceptionHandler`.
- **Sugestão:** testes JUnit simples, sem contexto Spring.

---

## srv-luthier-security

<a id="sec-01"></a>
### [ALTO] SEC-01: rate limit contornável com path codificado ✔ código / ⚠ exploração
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/domain/filters/RateLimitFilter.java:55-56, 63-64`
- **Problema:** o filtro compara `getRequestURI()`, que vem cru e sem decodificar, com um `Set` exato (`AUTH_PATHS`) e com `startsWith("/portal/")`. O Spring MVC decodifica os segmentos antes de rotear, e o `StrictHttpFirewall` não bloqueia letras codificadas como `%6c`. Então `POST /auth/%6cogin` tende a chegar ao `AuthController.login` sem passar pelo limitador. Com isso ficam liberados força bruta de senha, `forgot-password` (bombardeio de e-mails), `/auth/register` e varredura de tokens no portal (`/%70ortal/...`). Como cada login executa Argon2 (19 MiB, `PasswordEncoderConfig.java:19`), também vira DoS de memória sem autenticação. O comportamento contradiz o "rate limit em /auth/*" afirmado em `docs/security-review.md`.
- **Como confirmar:**
  ```bash
  for i in $(seq 10); do curl -s -o /dev/null -w "%{http_code}\n" -XPOST localhost:8080/auth/%6cogin \
    -H 'Content-Type: application/json' -d '{"email":"a@b.co","password":"12345678"}'; done
  ```
  Se nunca aparecer 429, está confirmado.
- **Sugestão:** casar a rota com o caminho normalizado, usando `PathPatternRequestMatcher` do Spring Security, ou `ServletRequestPathUtils.parseAndCache(request).pathWithinApplication()`. Adicionar um teste de regressão com `%6c` e `%70ortal`.

<a id="sec-02"></a>
### [ALTO] SEC-02: o login pode esgotar o pool de conexões ✔
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/services/impl/AuthServiceImpl.java:51-74`, `srv-luthier-security/src/main/java/com/luthier/security/services/impl/AuditServiceImpl.java:33`
- **Problema:** `login` é `@Transactional` e segura uma conexão do Hikari durante `authenticationManager.authenticate` (Argon2, lento de propósito). Os `publishEvent(AuditEvent…)` (L63, L70) chamam `AuditServiceImpl.record` com `REQUIRES_NEW`, que pede uma **segunda** conexão. Com N logins simultâneos e N ≥ tamanho do pool, cada thread segura uma conexão e espera outra. O login trava até o timeout do Hikari (30 s). Basta tráfego distribuído, porque o rate limit é por IP (ver SEC-01). O mesmo padrão aparece em `UserManagementServiceImpl`, que é transacional na classe inteira e audita com `REQUIRES_NEW`.
- **Sugestão:** tirar o `@Transactional` do `login`. Deixar transacional só a emissão de tokens (`issue`, em outro bean ou com `TransactionTemplate`), autenticar fora de transação e gravar a auditoria de sucesso em `@TransactionalEventListener(phase = AFTER_COMMIT)`.

### [MÉDIO] SEC-03: chave do rate limit por IPv6 /128
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/domain/filters/RateLimitFilter.java:56, 64`
- **Problema:** a chave é o IPv6 completo. Quem tem um bloco /64, padrão em redes residenciais e em nuvem, gera 2^64 chaves e nunca esgota o balde. O `X-Forwarded-For` não é forjável por padrão (`forward-headers-strategy: native`), desde que a borda sobrescreva o header.
- **Sugestão:** para IPv6, usar o prefixo /64 como chave. Documentar no deploy que o proxy de borda precisa sobrescrever `X-Forwarded-For`.

### [MÉDIO] SEC-04: `forgot-password` permite enumerar e-mails pelo tempo de resposta
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/services/impl/PasswordRecoveryServiceImpl.java:52-55`, `srv-luthier-notification/src/main/java/com/luthier/notification/listeners/NotificationListeners.java:75-91`
- **Problema:** a resposta é sempre 202, mas para um e-mail ativo o listener síncrono envia o SMTP dentro da requisição e da transação. Para um e-mail inexistente há só um SELECT. Medindo a latência dá para descobrir quais e-mails pertencem a usuários ativos, anulando a proteção anti-enumeração documentada. Um SMTP lento também segura uma conexão do pool por requisição.
- **Sugestão:** gravar o token, responder e enviar o e-mail de forma assíncrona após o commit, com executor próprio e o evento em memória (sem outbox, para não persistir o token bruto). Opcionalmente, igualar o tempo de resposta.

### [MÉDIO] SEC-05: cadastro público sem confirmação de e-mail
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/services/impl/TenantRegistrationServiceImpl.java:50, 68-70`, `srv-luthier-notification/src/main/java/com/luthier/notification/listeners/NotificationListeners.java:137-144`
- **Problema:** `/auth/register` devolve a sessão sem verificar o e-mail. Há dois abusos possíveis. No primeiro, alguém cadastra o e-mail de uma vítima com `owner.name` ou `workshop.name` contendo texto de phishing, e a plataforma envia o e-mail de boas-vindas a partir do próprio domínio. No segundo, como o e-mail é único globalmente, a vítima não consegue mais se cadastrar e recebe 409. O documento `docs/security-review.md` aceitou o 409, mas não considerou o e-mail enviado a terceiros.
- **Sugestão:** ativar a conta só depois de confirmar o e-mail. Enquanto isso não existir, não incluir texto controlado pelo usuário no corpo do e-mail de boas-vindas.

### [MÉDIO] SEC-06: a luthieria pode ficar sem nenhum ADMIN
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/services/impl/UserManagementServiceImpl.java:83-92, 103-110`
- **Problema:** `changeProfile` e `deactivate` não protegem o próprio usuário nem o último ADMIN ativo. Um ADMIN pode se desativar ou se rebaixar, ou desativar todos os outros ADMINs. Não há recuperação: o `AdminBootstrap` só roda quando não existe nenhum ADMIN em toda a plataforma, e o reset de senha exige usuário ativo.
- **Sugestão:** responder `ConflictException` quando `id` for o usuário do JWT, ou quando a operação deixar o tenant com zero ADMINs ativos (`countByTenantIdAndProfileAndActiveTrue`).

### [MÉDIO] SEC-07: o ator da trilha de auditoria fica errado ou exposto
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/services/impl/AuditServiceImpl.java:47-50`
- **Problema:** `currentActor()` só trata `auth == null`. Nas rotas `permitAll` o contexto tem um `AnonymousAuthenticationToken`, então `LOGIN_SUCCESS`, `LOGIN_FAILED`, `PASSWORD_RESET` e `TENANT_REGISTERED` gravam o ator `anonymousUser`. Nas rotas autenticadas, o ator é o e-mail completo do JWT, mas `docs/security-review.md` afirma que os e-mails são mascarados.
- **Sugestão:** tratar `AnonymousAuthenticationToken` como ausência de ator e deixar o `AuditEvent` carregar o ator explicitamente (UUID do usuário no login, reset e registro). Gravar o UUID do usuário em vez do e-mail, ou aplicar `Masks.email`.

### [MÉDIO] SEC-08: a validação aceita valores que o banco rejeita
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/domain/records/RegisterTenantRequest.java:20, 27`, `srv-luthier-security/src/main/java/com/luthier/security/domain/records/CreateUserRequest.java:15`
- **Problema:** o `document` aceita `^[\d./-]{11,18}$`, mas a coluna é `varchar(14)`. Um valor com 15 a 18 dígitos estoura a coluna, e não há verificação de dígitos de CPF/CNPJ. O `phone` aceita até 20 caracteres, mas só os dígitos são gravados numa coluna `varchar(11)`, e um número com DDI estoura. Em todos esses casos a rota pública `/auth/register` responde 500.
- **Sugestão:** validar os dígitos já normalizados, aceitando só 11 ou 14 (`@ValidCpf` ou um `@ValidCnpj`). Aceitar telefone com 10 ou 11 dígitos, ou aumentar a coluna.

### [MÉDIO] SEC-09: a busca de e-mail no login não usa índice
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/domain/repositories/UserRepository.java:14-15`, `srv-luthier-security/src/main/resources/db/migration/security/V1_0__security_schema.sql:23`
- **Problema:** `findByEmailIgnoreCase` gera `lower(email) = lower(?)`, que não usa o índice `uk_user_email` (criado sobre `email` puro). O login faz essa busca até duas vezes por tentativa, numa rota pública. A unicidade também diferencia maiúsculas: `A@x.com` e `a@x.com` podem coexistir se algum caminho não normalizar o valor.
- **Sugestão:** normalizar para minúsculas em todos os caminhos de escrita e de login, com `toLowerCase(Locale.ROOT)`, e usar `findByEmail`. Uma migration compatível com H2 e PostgreSQL:
  ```sql
  update user_tb set email = lower(email);
  alter table user_tb add constraint ck_user_email_lower check (email = lower(email));
  ```

### [MÉDIO] SEC-10: tokens sem FK e limpeza sem índice
- **Arquivo:** `srv-luthier-security/src/main/resources/db/migration/security/V1_0__security_schema.sql:30-52`, `srv-luthier-security/src/main/java/com/luthier/security/domain/repositories/RefreshTokenRepository.java:39-41`, `srv-luthier-security/src/main/java/com/luthier/security/domain/repositories/PasswordResetTokenRepository.java:29-31`
- **Problema:** `refresh_token_tb.user_id` e `password_reset_token_tb.user_id` não têm FK para `user_tb`, o que permite tokens órfãos. A limpeza `delete … where expires_at < :cutoff` não tem índice e varre a tabela inteira.
- **Sugestão:**
  ```sql
  alter table refresh_token_tb add constraint fk_refresh_user foreign key (user_id) references user_tb(uuid) on delete cascade;
  alter table password_reset_token_tb add constraint fk_reset_user foreign key (user_id) references user_tb(uuid) on delete cascade;
  create index idx_refresh_expires on refresh_token_tb (expires_at);
  create index idx_reset_expires on password_reset_token_tb (expires_at);
  ```

### [MÉDIO] SEC-11: a retenção da auditoria faz um delete gigante numa única transação
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/domain/repositories/AuditLogRepository.java:19-21`, `srv-luthier-security/src/main/java/com/luthier/security/jobs/AuditRetentionJob.java:28-33`
- **Problema:** o delete por `created_at < :cutoff` não tem índice adequado, porque o único índice é `(tenant_id, created_at)`. Ele roda numa transação só, sobre uma tabela com 5 anos de retenção, o que causa transação longa, locks e inchaço da tabela.
- **Sugestão:** `create index idx_audit_created on audit_log_tb (created_at);` e apagar em lotes de cerca de 5.000 linhas, em loop, uma transação por lote. Se a trilha for append-only, tirar `UPDATE`/`DELETE` do usuário da aplicação e deixar o purge para um papel dedicado.

### [MÉDIO] SEC-12: fluxos críticos de autenticação sem teste
- **Arquivo:** `srv-luthier-app/src/test/java/com/luthier/` (o módulo security não tem `src/test`)
- **Problema:** `docs/security-review.md` marca como OK itens que nenhum teste exercita:
  - rotação de refresh token e detecção de reuso;
  - logout;
  - `forgot-password`/`reset-password`: uso único, expiração e revogação das sessões;
  - 429 do `RateLimitFilter`, que todos os testes desativam com capacidade 1000;
  - revogação de sessão ao desativar o usuário ou mudar seu perfil;
  - 404 ao gerenciar usuário de outro tenant;
  - login com usuário ou tenant inativo.

  O `PostgresMigrationTest` só confere `count >= 5` no histórico do Flyway.
- **Sugestão:** criar `AuthFlowTest` (refresh, reuso, logout, reset), `UserManagementFlowTest` (isolamento, último ADMIN, revogação) e um `RateLimitFilterTest` unitário que cubra os casos de SEC-01.

### [BAIXO] SEC-13: tempo de resposta revela contas desativadas
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/services/impl/UserServiceImpl.java:31`
- **Problema:** o `DaoAuthenticationProvider` rejeita a conta desativada antes de verificar a senha. Essa resposta é rápida, enquanto uma conta ativa com senha errada (ou inexistente) passa pelo Argon2. Pela diferença de tempo dá para saber quais e-mails existem e estão desativados.
- **Sugestão:** aceitável. Para igualar os tempos, verificar a senha antes de rejeitar contas desativadas.

### [BAIXO] SEC-14: qualquer um pode encher a auditoria de outra luthieria
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/services/impl/AuthServiceImpl.java:61-65`
- **Problema:** tentativas de login com o e-mail de uma vítima gravam `LOGIN_FAILED` no tenant dela, e esses registros ficam retidos por 5 anos. O limite é só por IP, e pode ser contornado (SEC-01).
- **Sugestão:** limitar a gravação por tenant ou agregar falhas repetidas (contador por janela de tempo).

### [BAIXO] SEC-15: `@Transactional` do Jakarta misturado com o do Spring
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/services/impl/PasswordRecoveryServiceImpl.java:15`, `srv-luthier-security/src/main/java/com/luthier/security/services/impl/UserManagementServiceImpl.java:16`, `srv-luthier-security/src/main/java/com/luthier/security/jobs/TokenCleanupJob.java:5`
- **Problema:** esses arquivos usam `jakarta.transaction.Transactional`. Ele não tem `readOnly` e o rollback segue outras regras. Por isso `list()` e `me()` rodam em transação de escrita.
- **Sugestão:** usar `org.springframework.transaction.annotation.Transactional`, com `readOnly = true` nas leituras.

### [BAIXO] SEC-16: `user_tb` sem `@Version` e sem timestamps
- **Arquivo:** `srv-luthier-security/src/main/resources/db/migration/security/V1_0__security_schema.sql:13-27`, `srv-luthier-security/src/main/java/com/luthier/security/domain/entities/UserEntity.java:30-32`
- **Problema:** duas edições simultâneas do mesmo usuário (perfil, ativo, senha) sobrescrevem uma à outra, e não há `created_at`/`updated_at`. A PK se chama `uuid`, fora do padrão `id` das outras tabelas.
- **Sugestão:** adicionar `version bigint not null default 0` mapeado com `@Version`, e as colunas de timestamp.

### [BAIXO] SEC-17: `tenant_tb.document` sem unicidade
- **Arquivo:** `srv-luthier-security/src/main/resources/db/migration/security/V1_0__security_schema.sql:3-11`
- **Problema:** o mesmo CPF/CNPJ pode abrir várias luthierias.
- **Sugestão:** se a regra de negócio exigir, criar `unique (document)`. O PostgreSQL trata vários NULL como distintos, então a regra só vale para quem informou o documento.

### [BAIXO] SEC-18: o H2 console de dev está ligado mas inacessível
- **Arquivo:** `srv-luthier-security/src/main/java/com/luthier/security/configs/SecurityConfig.java:64-75`, `srv-luthier-app/src/main/resources/application-dev.yaml:16-19`
- **Problema:** `/h2-console` cai em `anyRequest().authenticated()` (só Bearer) e ainda recebe `frame-options: deny` e CSP `default-src 'none'`, então fica inutilizável. O `CLAUDE.md` também afirma que o console está ligado.
- **Sugestão:** desligar `spring.h2.console.enabled`, ou criar uma `SecurityFilterChain` própria `@Profile("dev")` para o console. Depois, alinhar o `CLAUDE.md`.

---

## srv-luthier-customer

### [MÉDIO] CUS-01: a busca serve de oráculo para descobrir o CPF mascarado
- **Arquivo:** `srv-luthier-customer/src/main/java/com/luthier/customer/domain/repositories/CustomerRepository.java:35`
- **Problema:** a resposta mostra `***.456.789-**`, mas a busca aceita qualquer pedaço do CPF (`c.cpf like %digits%`). Um ATENDENTE pode testar `?456789` e `456789?` com cada dígito de 0 a 9 e reconstruir o CPF completo em cerca de 50 requisições por cliente. Isso contorna o mascaramento e a regra de que só ADMIN exporta os dados. Rotas autenticadas não têm rate limit.
- **Sugestão:** no CPF, buscar só por igualdade com os 11 dígitos completos. Nome e telefone podem continuar com busca parcial.

### [MÉDIO] CUS-02: a anonimização LGPD é incompleta
- **Arquivo:** `srv-luthier-customer/src/main/java/com/luthier/customer/domain/entities/CustomerEntity.java:78-86`, `srv-luthier-notification/src/main/java/com/luthier/notification/domain/entities/NotificationEntity.java:92-96`, `srv-luthier-notification/src/main/java/com/luthier/notification/services/impl/Notifier.java:47`
- **Problema:** a anonimização limpa só o cadastro do cliente. Continuam ligados ao titular:
  - nos instrumentos, `conditionNotes` e `serialNumber`;
  - nas OS, os textos livres `reportedIssue`, `diagnosisNotes`, `technicianNotes`, `rejectionReason` e as descrições da linha do tempo, que costumam citar nome e telefone;
  - nas notificações, o campo `error`, que `scrub` não limpa e que pode conter o endereço do destinatário (vem de `e.getMessage()` do SMTP);
  - as notificações anteriores à V4_1, que não têm `customer_id` e nunca são varridas.
- **Sugestão:** quando receber `CustomerAnonymized`, anonimizar também os instrumentos e os textos livres das OS do cliente (no módulo service) e limpar o `error` no `scrub`. Gravar o `error` sem o destinatário.

### [MÉDIO] CUS-03: desativar o cliente com OS aberta silencia as notificações
- **Arquivo:** `srv-luthier-customer/src/main/java/com/luthier/customer/services/impl/CustomerServiceImpl.java:110-114, 157-162`, `srv-luthier-notification/src/main/java/com/luthier/notification/listeners/NotificationListeners.java:118-121`
- **Problema:** `deactivate` não verifica se há OS abertas, e `findCustomer` só devolve clientes ativos. Depois da desativação, o handler de `ServiceOrderStatusChanged` recebe `customer == null` e descarta a notificação sem registro, e o portal passa a mostrar `customerFirstName` nulo.
- **Sugestão:** responder 409 ao desativar um cliente com OS abertas, ou fazer `findCustomer` incluir inativos e manter a checagem de ativo só na abertura de OS.

### [MÉDIO] CUS-04: a busca com LIKE não escapa curingas e não usa índice
- **Arquivo:** `srv-luthier-customer/src/main/java/com/luthier/customer/domain/repositories/CustomerRepository.java:30-38`, `srv-luthier-customer/src/main/java/com/luthier/customer/services/impl/CustomerServiceImpl.java:76-81`
- **Problema:** não há injeção, porque o valor entra como parâmetro. Mas um `%` ou `_` digitado pelo usuário funciona como curinga, e `lower(name) like '%x%'` varre todos os clientes do tenant em cada busca.
- **Sugestão:** escapar `\`, `%` e `_` e usar `like … escape '\'`. Se o volume crescer, criar um índice GIN `pg_trgm` em `lower(name)` numa migration exclusiva do PostgreSQL.

### [MÉDIO] CUS-05: as FKs entre tabelas não incluem o tenant
- **Arquivo:** `srv-luthier-customer/src/main/resources/db/migration/customer/V1_2__customer_schema.sql:38`. O mesmo vale para as FKs filhas em `V1_3` e `V2_0`.
- **Problema:** `instrument_tb.customer_id → customer_tb(id)` não leva `tenant_id`. Se houver erro de código, nada impede que o instrumento do tenant A aponte para um cliente do tenant B.
- **Sugestão:** criar uma FK composta:
  ```sql
  alter table customer_tb add constraint uk_customer_tenant_id unique (tenant_id, id);
  alter table instrument_tb drop constraint fk_instrument_customer;
  alter table instrument_tb add constraint fk_instrument_customer
    foreign key (tenant_id, customer_id) references customer_tb (tenant_id, id);
  ```

### [BAIXO] CUS-06: faltam CHECKs de formato e índice de ordenação
- **Arquivo:** `srv-luthier-customer/src/main/resources/db/migration/customer/V1_2__customer_schema.sql:3-19`, `srv-luthier-customer/src/main/java/com/luthier/customer/domain/repositories/InstrumentRepository.java:13`
- **Problema:** o formato de `cpf` e `phone` só é validado na aplicação. A listagem de instrumentos ordena por `created_at` sem índice que cubra essa ordem.
- **Sugestão:** `check (cpf is null or length(cpf) = 11)`, que funciona em H2 e PostgreSQL. Criar o índice `(tenant_id, customer_id, created_at desc)` se houver muitos instrumentos por cliente.

---

## srv-luthier-product

<a id="pro-01"></a>
### [ALTO] PRO-01: a baixa de estoque não é idempotente
- **Arquivo:** `srv-luthier-product/src/main/java/com/luthier/product/services/impl/StockConsumptionListener.java:52-60`, `srv-luthier-product/src/main/resources/db/migration/product/V1_3__product_schema.sql:28-45`
- **Problema:** o outbox do Modulith entrega o evento ao menos uma vez (`republish-outstanding-events-on-restart: true`). Se o processo cair depois do commit da baixa e antes de a publicação ser marcada como concluída, o evento é reprocessado e o estoque da mesma OS é baixado de novo. Não há checagem de baixa já aplicada, nem constraint única em `stock_movement_tb` por OS e produto. `order_id` também não tem índice.
- **Sugestão:** antes de aplicar, chamar `existsByOrderIdAndProductIdAndType(orderId, productId, SAIDA)` e pular o que já foi baixado. Como garantia no banco, criar `create unique index uk_movement_order_product on stock_movement_tb (tenant_id, order_id, product_id, type);` e tratar a violação como "já aplicado". Antes, confirme que uma OS nunca gera dois movimentos do mesmo tipo para o mesmo produto. Entradas manuais têm `order_id` nulo, então o índice não as afeta.

### [MÉDIO] PRO-02: o histórico de movimentos ordena em memória
- **Arquivo:** `srv-luthier-product/src/main/java/com/luthier/product/domain/repositories/StockMovementRepository.java:13-14`
- **Problema:** o histórico ordena por `created_at`, mas o índice é só `(tenant_id, product_id)`.
- **Sugestão:** `create index idx_movement_tenant_product_created on stock_movement_tb (tenant_id, product_id, created_at desc);` e remover o índice antigo, que fica redundante.

### [MÉDIO] PRO-03: a busca de produtos não escapa curingas e não usa índice
- **Arquivo:** `srv-luthier-product/src/main/java/com/luthier/product/domain/repositories/ProductRepository.java:34-46`
- **Problema:** é o mesmo problema de CUS-04, só que com `OR` entre `name`, `brand` e `sku` e a condição `:text = ''`, o que impede um plano de execução estável.
- **Sugestão:** escapar o valor como em CUS-04. Se a tabela crescer, criar um índice `pg_trgm` (só PostgreSQL).

### [MÉDIO] PRO-04: faltam CHECKs de domínio em preços e quantidades
- **Arquivo:** `srv-luthier-product/src/main/resources/db/migration/product/V1_3__product_schema.sql:3-25`
- **Problema:** só existe `current_quantity >= 0`. Nada impede preço ou estoque mínimo negativos, nem quantidade zero ou negativa num movimento.
- **Sugestão:**
  ```sql
  alter table product_tb add constraint ck_product_prices check (cost_price >= 0 and sale_price >= 0);
  alter table product_tb add constraint ck_product_min_qty check (min_quantity >= 0);
  alter table stock_movement_tb add constraint ck_movement_qty check (quantity <> 0);
  ```

### [BAIXO] PRO-05: SKU de produto excluído não pode ser reutilizado
- **Arquivo:** `srv-luthier-product/src/main/java/com/luthier/product/services/impl/ProductServiceImpl.java:73, 116`
- **Problema:** `existsBySku` conta também produtos desativados, mas o usuário não enxerga esses produtos. Ele recebe 409 "SKU already registered" e não tem como ver nem reativar o produto antigo.
- **Sugestão:** documentar a regra e oferecer a reativação, ou liberar o SKU de produtos inativos ajustando a constraint.

### [BAIXO] PRO-06: o `orderId` dos movimentos manuais não é validado
- **Arquivo:** `srv-luthier-product/src/main/java/com/luthier/product/services/impl/ProductServiceImpl.java:104`, `srv-luthier-product/src/main/resources/db/migration/product/V1_3__product_schema.sql:37, 42`
- **Problema:** um ESTOQUISTA pode ligar um movimento a qualquer UUID de OS, o que polui a rastreabilidade. `stock_movement_tb.product_id` também tem FK sem `tenant_id` (ver CUS-05).
- **Sugestão:** aceitar `orderId` só em movimentos internos, ou validá-lo por meio de uma API pública do módulo service.

---

## srv-luthier-service

<a id="srv-01"></a>
### [ALTO] SRV-01: desconto e pagamentos podem deixar total ou saldo negativos ✔
- **Arquivo:** `srv-luthier-service/src/main/java/com/luthier/service/domain/entities/ServiceOrderEntity.java:159-164, 186-191, 205-211, 241-251, 273-290`
- **Problema:**
  - **Desconto:** só `updateDetails` compara o desconto com o total (L207). `removeService` e `removePart` não fazem essa checagem. Se o usuário aplicar R$ 100 de desconto e depois remover itens, o desconto passa do subtotal e `grandTotal()` fica negativo. `sendQuote` (L241) só verifica se a lista está vazia, então o orçamento é enviado com valor negativo.
  - **Pagamento:** `receivePayment` aceita qualquer status que não seja `CANCELADO`, inclusive `RECEBIDO` e `ORCAMENTO`, em que os itens ainda são editáveis. Se o cliente paga um sinal e depois um item é removido, `paidAmount` passa do total e `balance()` fica negativo. Esse valor vai para `ServiceOrderStatusChanged` e para os relatórios.
- **Sugestão:** centralizar as duas regras (desconto ≤ subtotal e valor pago ≤ total) num `assertTotalsConsistent()` chamado em `removeService`, `removePart`, `updateDetails` e `sendQuote`. A alternativa é proibir a remoção de itens quando `paidAmount > 0`. Cobrir os dois cenários com teste.

<a id="srv-02"></a>
### [ALTO] SRV-02: N+1 na listagem de OS
- **Arquivo:** `srv-luthier-service/src/main/java/com/luthier/service/domain/records/OrderResponse.java:61-67`, `srv-luthier-service/src/main/java/com/luthier/service/services/impl/ServiceOrderServiceImpl.java:85-91`, `srv-luthier-service/src/main/java/com/luthier/service/domain/repositories/ServiceOrderRepository.java:31-38`
- **Problema:** `summary()` chama `grandTotal()` e `balance()`, que percorrem `services` e `parts`, ambos `@OneToMany` LAZY. Uma página de 20 OS gera cerca de 40 SELECTs extras, e o kanban é a tela mais usada. O projeto não configura `default_batch_fetch_size` (ver APP-04).
- **Sugestão:** projetar a listagem com os totais calculados no banco (subqueries `sum(...)` por `order_id`). Como mitigação rápida, usar `@BatchSize(size = 50)` nas coleções ou `hibernate.default_batch_fetch_size=50`.

<a id="srv-03"></a>
### [ALTO] SRV-03: o relatório financeiro carrega todo o histórico
- **Arquivo:** `srv-luthier-service/src/main/java/com/luthier/service/domain/repositories/ServiceOrderRepository.java:66-73`, `srv-luthier-service/src/main/java/com/luthier/service/services/impl/ReportServiceImpl.java:100-103`
- **Problema:** `findBillable()` traz todas as OS em `EM_EXECUCAO`, `PRONTO` e `ENTREGUE`, sem limite, e soma `balance()` em Java, com N+1 nas coleções. Como `ENTREGUE` nunca sai do filtro, o custo cresce com toda a história da oficina. Um LUTHIER pode repetir a chamada e degradar o serviço.
- **Sugestão:** calcular o "a receber" com uma agregação em JPQL, que mantém o filtro de tenant (SQL nativo não mantém), usando subqueries de soma de mão de obra e de peças menos `discount` e `paidAmount`. Também é possível materializar `total_amount` na OS e atualizá-lo na entidade.

### [MÉDIO] SRV-04: duas OS simultâneas na primeira abertura do tenant
- **Arquivo:** `srv-luthier-service/src/main/java/com/luthier/service/services/impl/ServiceOrderServiceImpl.java:265-269`, `srv-luthier-service/src/main/java/com/luthier/service/domain/repositories/OrderCounterRepository.java:15-18`
- **Problema:** na primeira OS de um tenant, `lockCurrent()` volta vazio. Duas aberturas simultâneas criam o contador e a segunda viola `uk_order_counter_tenant`, o que resulta em 500 (ver SH-02). Depois disso, o `FOR UPDATE` serializa corretamente.
- **Sugestão:** criar o contador junto com o tenant (listener de `TenantRegistered`), ou capturar a violação e repetir `lockCurrent()`. Adicionar `check (last_number >= 0)`.

### [MÉDIO] SRV-05: sair de "aguardando aprovação" deixa o orçamento pendente ✔
- **Arquivo:** `srv-luthier-service/src/main/java/com/luthier/service/domain/entities/ServiceOrderEntity.java:221-239`
- **Problema:** `changeStatus` permite `AGUARDANDO_APROVACAO → ORCAMENTO` e `→ CANCELADO` sem encerrar o orçamento. `quoteStatus` continua `PENDENTE`, sem `quoteAnsweredAt`, e a API e o portal mostram um estado inconsistente.
- **Sugestão:** quando `changeStatus` sair de `AGUARDANDO_APROVACAO`, encerrar o orçamento (`quoteStatus = null` ou `REJEITADO`, com motivo). Outra opção é proibir a ida para `ORCAMENTO` por ali e exigir `rejectQuote`.

### [MÉDIO] SRV-06: peça desativada impede a OS de ir para PRONTO
- **Arquivo:** `srv-luthier-service/src/main/java/com/luthier/service/services/impl/ServiceOrderServiceImpl.java:219-231`
- **Problema:** `requireStockFor` usa `ProductQueries.find`, que filtra `active = true`. Se uma peça da OS for desativada, a mensagem "Peça não está mais no estoque" bloqueia o `PRONTO` para sempre. Em `EM_EXECUCAO` também não é possível cancelar a OS nem remover a peça.
- **Sugestão:** definir uma regra explícita: permitir baixar produto inativo, ou liberar a remoção ou troca da peça em `EM_EXECUCAO`.

### [MÉDIO] SRV-07: faltam CHECKs monetários e FKs nas OS
- **Arquivo:** `srv-luthier-service/src/main/resources/db/migration/service/V2_0__service_orders.sql:13-44, 48-111`
- **Problema:** nenhum valor monetário tem CHECK (`discount`, `paid_amount`, `labor_price`, `unit_price`, `estimated_days`). As FKs filhas não incluem `tenant_id`. `customer_id` e `instrument_id` não têm FK, mesmo estando no mesmo banco. As referências entre módulos (`product_id`, `catalog_id`) podem continuar sem FK.
- **Sugestão:**
  ```sql
  alter table service_order_tb add constraint ck_order_money check (discount >= 0 and paid_amount >= 0);
  alter table service_order_item_tb add constraint ck_item_price check (labor_price >= 0);
  alter table service_order_part_tb add constraint ck_part_price check (unit_price >= 0 and quantity > 0);
  alter table service_order_tb add constraint uk_order_tenant_id unique (tenant_id, id);
  ```
  E criar FKs compostas `(tenant_id, order_id)` nas tabelas filhas, como em CUS-05.

### [MÉDIO] SRV-08: recibo sem unicidade e `payment_tb.order_id` sem índice
- **Arquivo:** `srv-luthier-service/src/main/resources/db/migration/service/V2_0__service_orders.sql:111`, `srv-luthier-service/src/main/java/com/luthier/service/domain/entities/ServiceOrderEntity.java:283`
- **Problema:** o número do recibo é `REC-<n>-<payments.size()+1>`. Hoje só o `@Version` da OS impede um número repetido. Carregar `payments` usa uma FK sem índice.
- **Sugestão:** `alter table payment_tb add constraint uk_payment_receipt unique (tenant_id, receipt_number);` e `create index idx_payment_order on payment_tb (order_id);`.

### [MÉDIO] SRV-09: o índice do kanban não cobre a ordenação
- **Arquivo:** `srv-luthier-service/src/main/resources/db/migration/service/V2_0__service_orders.sql:45-46`, `srv-luthier-service/src/main/java/com/luthier/service/controllers/ServiceOrderController.java:60-61`
- **Problema:** a listagem filtra por status e ordena por `order_number desc`, mas o índice `(tenant_id, status)` obriga uma ordenação extra.
- **Sugestão:** `create index idx_order_tenant_status_number on service_order_tb (tenant_id, status, order_number desc);` no lugar de `idx_order_tenant_status`.

### [MÉDIO] SRV-10: a exportação LGPD faz N+1 e não tem limite
- **Arquivo:** `srv-luthier-service/src/main/java/com/luthier/service/services/impl/PrivacyServiceImpl.java:35-37`
- **Problema:** a exportação carrega todas as OS do cliente e, para cada uma, `OrderResponse::from` percorre 4 coleções: serviços, peças, linha do tempo e pagamentos.
- **Sugestão:** usar `@EntityGraph`/`join fetch` em etapas, ou `@BatchSize`.

### [MÉDIO] SRV-11: lacunas de teste nas regras de negócio
- **Arquivo:** `srv-luthier-app/src/test/java/com/luthier/` (só há testes de fluxo, nenhum unitário)
- **Problema:** não há teste para:
  - desconto ou pagamento depois de remover itens (SRV-01);
  - cancelamento com pagamentos;
  - conflito de `@Version`;
  - reentrega do outbox (PRO-01);
  - baixa assíncrona que falha por saldo insuficiente;
  - recusa do orçamento pelo portal;
  - falha no envio de e-mail;
  - `NotificationRetentionJob`;
  - numeração concorrente de OS;
  - `AJUSTE` que deixa o estoque negativo.
- **Sugestão:** criar testes unitários de `ServiceOrderEntity` (máquina de estados e totais, sem Spring) e testes de integração para os eventos.

### [BAIXO] SRV-12: o token do portal não expira e se espalha por logs e eventos
- **Arquivo:** `srv-luthier-service/src/main/java/com/luthier/service/controllers/CustomerPortalController.java:148, 152`, `srv-luthier-service/src/main/java/com/luthier/service/domain/entities/ServiceOrderEntity.java:90`, `srv-luthier-service/src/main/java/com/luthier/service/ServiceOrderStatusChanged.java:13`
- **Problema:** o token de 192 bits permite ver a OS e responder o orçamento. Ele nunca expira, nem depois da entrega ou da anonimização. Vai no path da URL, então aparece nos logs de acesso, e é serializado em claro no outbox (`event_publication`) e nas notificações (ver NOT-01).
- **Sugestão:** invalidar o token em `ENTREGUE`/`CANCELADO` ou depois de N dias. Não colocar o token no evento (o listener pode buscá-lo). Mascarar `/portal/orders/*` nos logs de acesso.

### [BAIXO] SRV-13: título do catálogo duplicado por diferença de maiúsculas
- **Arquivo:** `srv-luthier-service/src/main/java/com/luthier/service/domain/repositories/ServiceCatalogRepository.java:18-20`, `srv-luthier-service/src/main/resources/db/migration/service/V1_4__service_catalog_schema.sql:14`
- **Problema:** `existsByTitleIgnoreCase` não diferencia maiúsculas, mas `uk_service_tenant_title` diferencia. Duas requisições concorrentes conseguem gravar "Regulagem" e "regulagem".
- **Sugestão:** normalizar o título, ou criar um índice único em `lower(title)` (só PostgreSQL).

### [BAIXO] SRV-14: a exportação LGPD omite notificações e os inativos ficam para sempre
- **Arquivo:** `srv-luthier-service/src/main/java/com/luthier/service/services/impl/PrivacyServiceImpl.java:42-51`
- **Problema:** a exportação não inclui as notificações enviadas ao titular. Clientes desativados (soft delete) mantêm os dados pessoais indefinidamente, sem prazo de retenção.
- **Sugestão:** incluir as notificações na exportação e criar um job que anonimize clientes inativos depois de um prazo.

### [BAIXO] SRV-15: código morto
- **Arquivo:** `srv-luthier-service/src/main/java/com/luthier/service/domain/repositories/ServiceOrderRepository.java:75-76`
- **Problema:** `sumPaid()` não é usado em lugar nenhum, e somaria todas as OS do tenant sem período.
- **Sugestão:** remover.

---

## srv-luthier-notification

<a id="not-01"></a>
### [ALTO] NOT-01: qualquer papel interno obtém contatos e tokens do portal ✔
- **Arquivo:** `srv-luthier-notification/src/main/java/com/luthier/notification/services/NotificationService.java:13, 17`, `srv-luthier-notification/src/main/java/com/luthier/notification/listeners/NotificationListeners.java:160-169`
- **Problema:** `GET /notifications` e `POST /notifications/{id}/sent` aceitam `ANY_STAFF`. A resposta traz `recipient` (telefone ou e-mail do cliente), `body` e `actionUrl`, e os dois últimos contêm o link `/acompanhar/<token>`. Um ESTOQUISTA, que não acessa `/customers` nem `/service-orders`, pode listar `?channel=whatsapp`, obter contatos e tokens, e depois chamar `POST /portal/orders/{token}/quote/approve` ou `/reject`. Assim ele move a OS sozinho, numa ação que a matriz de `docs/api.md` reserva ao balcão. A afirmação do `docs/security-review.md` de que "notificações com segredo não são gravadas" vale só para o token de reset.
- **Sugestão:** restringir a listagem e o `markSent` a `Access.CUSTOMER_DESK`, e deixar o ESTOQUISTA ver só o canal `INTERNAL` (alertas de estoque). Não expor o token na API: mascarar `actionUrl` e `body` na resposta, ou montar o link só no momento do envio.

### [MÉDIO] NOT-02: e-mail enviado dentro da transação pode sair duplicado
- **Arquivo:** `srv-luthier-notification/src/main/java/com/luthier/notification/listeners/NotificationListeners.java:112-134`, `srv-luthier-notification/src/main/java/com/luthier/notification/services/impl/Notifier.java:35-44`
- **Problema:** o SMTP bloqueante roda dentro da transação do handler e segura uma conexão do pool. Se o `whatsapp(...)` ou o commit falhar depois do envio, a transação desfaz o registro, mas o e-mail já saiu. O outbox reprocessa o evento e o cliente recebe outro e-mail.
- **Sugestão:** gravar a `NotificationEntity` como pendente na transação e enviar depois do commit ou por um job de envio. Outra opção é tornar o handler idempotente por `(relatedId, channel, status destino)`.

### [MÉDIO] NOT-03: o job de retenção faz varredura total e não está comprovado ⚠
- **Status:** resolvido na branch `fix/sh-01-tenant-fail-closed`. O `RetentionJobFlowTest` confirmou que o `DELETE` sob `ROOT` apagava de todos os tenants. Com o SH-01, o job passou a abrir o escopo de plataforma explicitamente. O índice `idx_notification_created` foi criado na `V4_2`. A exclusão em lotes ficou de fora: o volume ainda é pequeno.
- **Arquivo:** `srv-luthier-notification/src/main/java/com/luthier/notification/domain/repositories/NotificationRepository.java:19-22`, `srv-luthier-notification/src/main/java/com/luthier/notification/services/impl/NotificationRetentionJob.java:28-34`
- **Problema:** o delete por `created_at < :cutoff` não tem índice adequado e roda numa única transação, sem lotes. Ainda não foi confirmado que um `DELETE` HQL sob o tenant `ROOT` apaga linhas de todos os tenants. Se o filtro continuar valendo, o job apaga zero linhas e não dá erro. Nenhum teste cobre o job. O mesmo vale para `AuditRetentionJob` (SEC-11).
- **Sugestão:** `create index idx_notification_created on notification_tb (created_at);`, apagar em lotes e criar um teste de integração que comprove o expurgo com dados de dois tenants.

### [MÉDIO] NOT-04: a limpeza da anonimização roda linha a linha e o índice está errado
- **Arquivo:** `srv-luthier-notification/src/main/resources/db/migration/notification/V4_1__notification_customer.sql:3`, `srv-luthier-notification/src/main/java/com/luthier/notification/domain/repositories/NotificationRepository.java:17`, `srv-luthier-notification/src/main/java/com/luthier/notification/listeners/NotificationListeners.java:149`
- **Problema:** o índice cobre só `customer_id`, mas toda consulta filtra `tenant_id and customer_id`. `findAllByCustomerId` não tem limite, e o `scrub` dispara um UPDATE por notificação, sem batch.
- **Sugestão:**
  ```sql
  drop index idx_notification_customer;
  create index idx_notification_tenant_customer on notification_tb (tenant_id, customer_id);
  ```
  E trocar o `scrub` por um único `@Modifying update NotificationEntity n set … where n.customerId = :id`.

### [BAIXO] NOT-05: logs e banco gravam dados pessoais vindos de erros SMTP
- **Arquivo:** `srv-luthier-notification/src/main/java/com/luthier/notification/services/impl/Notifier.java:47`, `srv-luthier-notification/src/main/java/com/luthier/notification/listeners/NotificationListeners.java:89`
- **Problema:** `e.getMessage()` das exceções de SMTP costuma trazer o endereço do destinatário, e vai para o log e para a coluna `error`. Isso é dado pessoal em log (LGPD).
- **Sugestão:** registrar só a classe da exceção e um código de erro.

### [BAIXO] NOT-06: métrica contada antes do commit
- **Arquivo:** `srv-luthier-notification/src/main/java/com/luthier/notification/services/impl/Notifier.java:68-72`
- **Problema:** o contador é incrementado antes do `save`, então um rollback deixa a métrica maior que o real.
- **Sugestão:** incrementar depois do commit, ou aceitar e documentar a imprecisão.

---

## srv-luthier-app, infraestrutura e CI

### [MÉDIO] APP-01: sem perfil definido, a aplicação sobe em dev com segredos públicos
- **Arquivo:** `srv-luthier-app/src/main/resources/application.yaml:6-7`, `srv-luthier-app/src/main/resources/application-dev.yaml:41, 43`
- **Problema:** `spring.profiles.default: dev` ativa o perfil permissivo quando ninguém define um perfil. Um `java -jar` sem `SPRING_PROFILES_ACTIVE`, ou um deploy fora do Dockerfile (que fixa `prod`), sobe com `JWT_SECRET=dev-only-secret-…`, valor que está no repositório. Com ele, qualquer pessoa forja um JWT HS256 de ADMIN para qualquer `tid`, o que dá acesso total a todos os tenants. O perfil dev também desliga `require-https`.
- **Sugestão:** remover o `default: dev` e fazer a aplicação falhar sem perfil, com o perfil dev ativado explicitamente na IDE ou no `mvn spring-boot:run`. Outra opção é recusar a subida quando o segredo for o de dev e o perfil não for `dev`/`test`.

### [MÉDIO] APP-02: esquema do outbox escrito à mão e com payload limitado ⚠
- **Arquivo:** `srv-luthier-app/src/main/resources/db/migration/app/V1_1__event_publication.sql:3-14`
- **Problema:** `serialized_event varchar(10000)` limita o tamanho do evento. Um `StockConsumptionRequested` com muitas peças pode estourar esse limite, e o INSERT do outbox falha na mesma transação do negócio, derrubando a operação. Os índices não seguem as consultas de republicação do Modulith. Falta conferir contra o schema oficial do Spring Modulith 2.1.
- **Sugestão:** alinhar com o DDL oficial do Modulith 2.1 (`text` para `serialized_event`, índices do schema oficial) e rodar `PostgresMigrationTest` com Docker ligado.

### [MÉDIO] APP-03: nenhuma tabela de negócio tem FK para `tenant_tb`
- **Status:** resolvido na branch `fix/sh-01-tenant-fail-closed`. Foram criadas FKs nas 12 tabelas de negócio (`V4_3` a `V4_6`). O `TenantIsolationTest` falha se uma tabela nova com `tenant_id` ficar sem FK; a única exceção é `audit_log_tb`.
- **Arquivo:** migrations de customer, product, service e notification (`V1_2`, `V1_3`, `V1_4`, `V2_0`, `V3_0`)
- **Problema:** é a defesa que falta contra SH-01, ou seja, contra INSERT com tenant `ROOT` ou tenant inexistente.
- **Sugestão:** `alter table <tabela> add constraint fk_<tabela>_tenant foreign key (tenant_id) references tenant_tb (id);`. Se a FK for indesejada, para manter os módulos independentes para uma futura extração, use pelo menos `check (tenant_id <> '00000000-0000-0000-0000-000000000000')`.

### [BAIXO] APP-04: sem configuração de batch do Hibernate
- **Arquivo:** `srv-luthier-app/src/main/resources/application.yaml:11-15`
- **Problema:** sem batch, nada mitiga o N+1 (SRV-02, SRV-10) nem o UPDATE linha a linha (NOT-04).
- **Sugestão:**
  ```yaml
  spring.jpa.properties.hibernate:
    default_batch_fetch_size: 50
    jdbc.batch_size: 50
    order_inserts: true
    order_updates: true
  ```

### [BAIXO] APP-05: sem limite de tamanho para o corpo JSON
- **Arquivo:** `srv-luthier-app/src/main/resources/application.yaml:75-79`
- **Problema:** o Jackson aceita strings de cerca de 20 MB, e o `@Size` só é aplicado depois do parse. Nas rotas públicas (`/auth/*`, `/portal/*`), isso permite gastar memória do servidor sem autenticação.
- **Sugestão:** limitar o tamanho do corpo na borda (proxy) ou num filtro, e configurar `StreamReadConstraints.maxStringLength` num `Jackson2ObjectMapperBuilderCustomizer`.

### [BAIXO] APP-06: o compose publica serviços em todas as interfaces
- **Arquivo:** `docker-compose.yml:11-12, 23-24, 46-47, 59-61`
- **Problema:** Postgres (com `luthieria/luthieria`), Mailpit (que mostra os links de reset de senha), Actuator e Jaeger ficam abertos em `0.0.0.0`. Numa rede compartilhada, qualquer um lê o banco de todos os tenants e os e-mails de reset.
- **Sugestão:** publicar só em localhost (`127.0.0.1:5432:5432`, e o mesmo para as outras portas) e não publicar a 8081.

### [BAIXO] APP-07: endurecimento do CI
- **Arquivo:** `.github/workflows/ci.yml:16, 47-49, 54`
- **Problema:** `security-events: write` é concedido sem envio de SARIF. As actions são fixadas por tag e não por SHA. O Trivy varre só o filesystem, não a imagem.
- **Sugestão:** remover a permissão sem uso, fixar as actions por SHA (o Dependabot atualiza) e adicionar `trivy image` depois do build.

### [BAIXO] APP-08: imagem base sem digest
- **Arquivo:** `Dockerfile:2`
- **Problema:** `eclipse-temurin:21-jre-alpine` muda sem revisão, então o build não é reproduzível. O usuário não-root e o HEALTHCHECK estão corretos.
- **Sugestão:** fixar `@sha256:…` e deixar o Dependabot (ecossistema `docker`) atualizar.

### [BAIXO] APP-09: helper duplicado nos testes
- **Arquivo:** `srv-luthier-app/src/test/java/com/luthier/RegistrationFlowTest.java:158-170, 184-187`
- **Problema:** `register(...)` e `auth(...)` repetem o que `support/ApiClient` já oferece.
- **Sugestão:** reutilizar o `ApiClient`.

---

## Pontos fortes identificados

- **Multi-tenancy:** o `@TenantId` está numa base comum, com `@Version`. Registros de outro tenant respondem 404 em vez de 403. Os listeners assíncronos aplicam `TenantContext.runAs` antes de abrir a transação, com beans `Consumer`/`Handlers` separados. O portal resolve o tenant pelo token (`callAsPlatform` → `callAs`).
- **Autenticação:**
  - Senhas com Argon2id, com fallback para BCrypt.
  - JWT HS256 fixo, com validação de `iss`, `exp` e `tid` e expiração de 15 minutos.
  - Refresh opaco guardado como hash SHA-256, com rotação, detecção de reuso via `FOR UPDATE` e `noRollbackFor` justificado.
  - Reset de senha de uso único, que revoga as sessões.
  - Mensagens de falha genéricas.
- **Autorização:** `@PreAuthorize` nas interfaces de serviço com constantes SpEL (`Access`), coerente com a matriz de `docs/api.md` (exceto NOT-01). A regra "pronto só bancada" é aplicada no serviço.
- **Dados:**
  - Dinheiro em `NUMERIC(12,2)` com `BigDecimal` e `@Digits`, e `timestamptz` em todas as tabelas.
  - Unicidade por tenant e índices compostos começando por `tenant_id`.
  - Trilhas `@Immutable`.
  - JPQL sempre parametrizado, sem SQL nativo.
  - `open-in-view: false`, `ddl-auto: validate` e paginação limitada a 100.
  - Numeração de OS com `PESSIMISTIC_WRITE`.
  - `StockLedger` com `Propagation.MANDATORY`.
- **Arquitetura:** fronteiras validadas pelo `ModularityTest`. Eventos garantidos pelo outbox. Máquina de estados centralizada em `OrderStatus.next()` e na entidade.
- **Configuração e infraestrutura:**
  - A base é segura por padrão: HTTPS obrigatório, Swagger e H2 desligados, erros sem stack trace (RFC 9457).
  - `JWT_SECRET` e `ADMIN_SENHA` são obrigatórios em prod.
  - CSP, HSTS, `X-Frame-Options` e CORS sem credenciais.
  - Actuator restrito a health, info e prometheus.
  - Container não-root, Trivy e Dependabot.

## Próximos passos sugeridos

1. **Imediato:** reproduzir e corrigir SEC-01 (rate limit), restringir NOT-01 (notificações) e corrigir SEC-02 (transação do login).
2. **Curto prazo:**
   - Fazer o resolver de tenant falhar fechado (SH-01) e acrescentar a FK para o tenant (APP-03).
   - Garantir os invariantes de total e saldo da OS (SRV-01).
   - Tornar a baixa de estoque idempotente (PRO-01).
   - Tratar `DataIntegrityViolationException` com 409 (SH-02).
   - Remover `profiles.default: dev` (APP-01).
3. **Desempenho:** SRV-02, SRV-03 e APP-04, com batch fetch e agregações em JPQL.
4. **Migrations de hardening:** juntar numa única migration nova (versão maior que `V4_1`) os CHECKs, as FKs e os índices de SEC-09, SEC-10, SEC-11, CUS-05, PRO-02, PRO-04, SRV-07, SRV-08, SRV-09 e NOT-04.
5. **Testes:** SEC-12 e SRV-11, mais um teste de regressão para cada achado ALTO.
