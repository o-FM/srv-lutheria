# Revisão de segurança e LGPD

Revisão feita na etapa de endurecimento do roadmap. Cada item cita onde está a proteção e o
teste que a cobre. Itens "Aceito" são riscos conhecidos com mitigação parcial.

## Autenticação e sessão

| Item | Situação |
|---|---|
| Senhas com Argon2id (fallback BCrypt para hashes antigos), mínimo 12 caracteres | OK — `PasswordEncoderConfig` |
| JWT HS256 de 15 min; segredo ≥ 32 bytes obrigatório e só por ambiente em prod | OK — `JwtConfig`, `application-prod.yaml` |
| JWT valida **emissor** (`iss=luthieria`), expiração e presença do `tid` | **Corrigido nesta etapa** — `PrivacyAndSecurityTest.tokensFromOtherIssuersOrWithoutTenantAreRejected` |
| Refresh token opaco, guardado só como SHA-256, rotação com detecção de reuso (revoga a família) | OK — `AuthServiceImpl.refresh` |
| Login, refresh e reset retornam a mesma mensagem genérica (anti-enumeração) | OK — `AuthExceptionHandler` |
| Token de reset: uso único, 30 min, hash no banco, link com `#fragment` | OK |
| Token de reset **não** passa pelo outbox (seria gravado em claro em `event_publication`) | **Corrigido nesta etapa** — listener síncrono em `NotificationListeners` |
| Usuário/luthieria desativados não renovam sessão; troca de papel revoga refresh tokens | OK |
| Rate limit por IP em `/auth/*` (5/min) e `/portal/*` (30/min) | OK — `RateLimitFilter` |
| Área do cliente: código `XXXX-XXXX` (30^8 combinações) guardado só como HMAC (chave do servidor), reutilizável por 90 dias, revogado por nova OS / reenvio do balcão / anonimização; rate limit por IP; 401 genérico; JWT CLIENTE sem acesso a rotas do balcão | OK — `CustomerAreaTest` |
| Login de profissionais por e-mail ou CPF; CPF repetido em mais de uma oficina exige e-mail; CPF inválido não consulta o banco | OK — `RegistrationFlowTest` |
| `/auth/register` revela se um e-mail já existe (409) | **Aceito** — necessário para UX de cadastro; mitigado pelo rate limit |
| Rate limit em memória (por instância) | **Aceito** — com mais de uma réplica, migrar para Bucket4j + Redis |
| Bloqueio por conta após N falhas (além do limite por IP) | **Pendente** — recomendado antes de abrir para o público |

## Autorização e isolamento entre luthierias

| Item | Situação |
|---|---|
| Filtro de tenant pelo Hibernate em todas as entidades de negócio | OK — `RegistrationFlowTest`, `ServiceOrderFlowTest`, `NotificationFlowTest` |
| Acesso a registro de outra luthieria → 404 (não revela existência) | OK |
| Tabelas sem filtro automático (`user_tb`, `audit_log_tb`) filtram explicitamente | OK — `UserManagementServiceImpl`, `AuditServiceImpl` |
| RBAC no serviço (`@PreAuthorize`), não só no controller | OK — inclusive `pronto` só para LUTHIER/ADMIN |
| Portal do cliente: token aleatório de 192 bits, formato validado, dados mínimos | OK — `PortalOrderResponse` |
| Fronteiras entre módulos verificadas no build | OK — `ModularityTest` |

## Borda HTTP

| Item | Situação |
|---|---|
| HTTPS obrigatório fora de dev (`require-https`), HSTS 1 ano | OK |
| CSP `default-src 'none'`, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, Permissions-Policy | OK — conferido nas respostas |
| CORS: origens explícitas, **sem credenciais** (API usa Bearer, não cookie) | **Corrigido nesta etapa** — `CorsConfig` |
| Actuator: só `health`, `info`, `prometheus`; demais `denyAll`; em prod em porta separada (8081) | **Corrigido nesta etapa** |
| Erros sem stacktrace/mensagem interna (`server.error.include-*: never`) | OK |
| Bean Validation em todos os requests; tamanhos máximos em textos e paginação limitada a 100 | OK |
| Console H2 e OpenAPI apenas em dev | OK — perfis |

## LGPD

| Direito / princípio | Implementação |
|---|---|
| Acesso e portabilidade (art. 18, II e V) | `GET /privacy/customers/{id}/export` — JSON com dados pessoais, instrumentos e OS |
| Eliminação (art. 18, VI) | `POST /customers/{id}/anonymize` — apaga PII e mantém o histórico fiscal sem identificar a pessoa; evento `CustomerAnonymized` apaga também destinatário e conteúdo das notificações |
| Minimização | Portal mostra só o primeiro nome; CPF sempre mascarado na API; notificações com segredo não são gravadas |
| Retenção | Jobs diários: notificações após 365 dias, auditoria após 5 anos (configuráveis) |
| Responsabilização (art. 37) | Trilha `audit_log_tb`: login (sucesso/falha), usuários, cadastro de luthieria, reset de senha, exportação/anonimização/desativação de cliente — com ator, IP e horário; e-mails mascarados |
| Logs | `tenantId`/`userId` no MDC; bind de parâmetros SQL só em dev; logs JSON em prod |

## Cadeia de suprimentos e operação

| Item | Situação |
|---|---|
| CI com build, testes, verificação de módulos e Trivy (dependências, segredos, Dockerfile) | OK — `.github/workflows/ci.yml` |
| Dependabot (Maven, Actions, Docker) | OK |
| Imagem com JRE, usuário não-root, healthcheck | OK — `Dockerfile` |
| Segredos de exemplo apenas em `application-dev.yaml` e `docker-compose.yml` (uso local) | **Aceito** — nunca reutilizar; produção lê do ambiente/cofre |

## Testes de carga

`load-tests/k6-luthieria.js`, executado localmente (H2 em memória, 1 instância, 20 VUs, 3 min,
20 luthierias simultâneas):

| Métrica | Resultado | Limite |
|---|---|---|
| Requisições | 37.488 (≈205 req/s) | — |
| Falhas | 0,00% | < 1% |
| `http_req_duration` p95 | 9,3 ms | < 500 ms |
| Ciclo completo da OS (9 chamadas) p95 | 78 ms | < 3 s |
| Baixas de estoque assíncronas concluídas | 2.881 de 2.881 | 100% |

Os números refletem banco em memória; repetir contra PostgreSQL (docker compose) antes de
dimensionar produção.
