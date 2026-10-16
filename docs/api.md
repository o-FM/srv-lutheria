# API

Contrato completo (OpenAPI 3): [`openapi.json`](openapi.json) — em dev também em
`GET /v3/api-docs`. Valores de enum seguem o front (`violao_aco`, `em_execucao`, `pix`...).
Telefone e CPF aceitam máscara; CPF sai sempre mascarado (`***.982.247-**`), exceto na
exportação LGPD. DELETE é exclusão lógica. Erros seguem RFC 9457 (`application/problem+json`).

## Multi-tenancy (banco compartilhado)
- Cada luthieria é um **tenant** (`tenant_tb`); tabelas de negócio têm `tenant_id`.
- O JWT carrega `tid`; o `TenantContextFilter` coloca no `TenantContext` e o Hibernate
  (`@TenantId` em `TenantScopedEntity`) filtra **todas** as consultas e preenche o tenant no INSERT.
- Registro de outra luthieria responde **404**. Unicidade é por luthieria (CPF, SKU, título...).
- `user_tb` e `audit_log_tb` têm `tenant_id` sem filtro automático (login acontece antes de saber o
  tenant); os serviços filtram explicitamente.
- Rotas sem tenant: `/auth/**` e o portal do cliente, que descobre a luthieria pelo token da OS e
  então executa o caso de uso dentro dela.
- Listeners assíncronos recebem o `tenantId` no evento e o aplicam antes de abrir a transação.

## Endpoints

| Rota | Descrição | Papéis |
|---|---|---|
| `POST /auth/register` | Cadastra luthieria + dono (ADMIN) e devolve tokens | público (rate limit) |
| `POST /auth/login`, `/refresh`, `/logout`, `/forgot-password`, `/reset-password` | Sessão e senha | público (rate limit) |
| `GET /users/me`, `GET/POST /users`, `PATCH /users/{id}/profile`, `POST /users/{id}/(de)activate` | Usuários da luthieria | todos (`me`) / ADMIN |
| `/customers`, `/customers/{id}` | Clientes (`?search=` nome, telefone, CPF) | ADMIN, LUTHIER, ATENDENTE (DELETE: ADMIN) |
| `/customers/{id}/instruments` | Instrumentos do cliente | ADMIN, LUTHIER, ATENDENTE |
| `GET /instruments` | Todos os instrumentos da luthieria | ADMIN, LUTHIER, ATENDENTE |
| `POST /customers/{id}/anonymize` | LGPD: eliminação dos dados pessoais | ADMIN |
| `/products`, `/products/barcode/{code}`, `/products/{id}/movements` | Estoque e movimentações | escrita: ADMIN, LUTHIER, ESTOQUISTA; leitura: todos |
| `/service-catalog` | Catálogo de serviços | escrita: ADMIN, LUTHIER; leitura: todos |
| `/service-orders` | OS: abertura, lista/kanban (`?status=&customerId=&number=&full=`), detalhe, `PATCH` | balcão (ADMIN, LUTHIER, ATENDENTE) |
| `/service-orders/{id}/services`, `.../services/{item}/done` | Serviços da OS / concluir item | balcão / bancada (ADMIN, LUTHIER) |
| `/service-orders/{id}/parts` | Peças da OS | bancada |
| `POST /service-orders/{id}/status` | Transição de status (`pronto` só bancada) | balcão |
| `POST /service-orders/{id}/quote/send\|approve\|reject` | Orçamento | balcão |
| `POST /service-orders/{id}/notes`, `/payments` | Observações e pagamentos | balcão |
| `GET /portal/orders/{token}`, `POST .../quote/approve\|reject` | Portal do cliente sem senha | público (rate limit) |
| `GET /reports/dashboard` | Tela "Hoje" | todos |
| `GET /reports/financial?from=&to=` | Financeiro | ADMIN, LUTHIER |
| `GET /notifications?channel=&status=`, `POST /notifications/{id}/sent` | Mensagens (e-mail, WhatsApp, alertas) | todos |
| `GET /privacy/customers/{id}/export` | LGPD: exportação dos dados do titular | ADMIN |
| `GET /audit-logs` | Trilha de auditoria | ADMIN |
| `GET /actuator/health`, `/info`, `/prometheus` | Operação (em prod, porta 8081 interna) | público |

## Ciclo da OS

```
recebido → diagnostico → orcamento → aguardando_aprovacao → em_execucao → pronto → entregue
                             ↑               │ recusado
                             └───────────────┘
cancelado: até em_execucao
```

- Serviços/peças/desconto só mudam até `orcamento`. `aguardando_aprovacao` só via
  `quote/send`; `em_execucao` só pela aprovação (oficina ou portal).
- `pronto` (somente LUTHIER/ADMIN) confere o saldo das peças e publica
  `StockConsumptionRequested`; o módulo de produtos dá baixa de forma assíncrona e garantida
  (outbox do Spring Modulith).
- Cada mudança gera evento na linha do tempo e `ServiceOrderStatusChanged`, que vira e-mail e
  mensagem de WhatsApp pronta para o cliente.

## Exemplo

```bash
curl -X POST localhost:8080/auth/register -H 'Content-Type: application/json' -d '{
  "workshop": {"name": "Oficina do Armando"},
  "owner": {"name": "Armando", "email": "armando@oficina.com", "password": "SenhaForte@2026",
            "cpf": "529.982.247-25", "phone": "(11) 98765-4321"}}'
```
