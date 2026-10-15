# Cadastros e multi-tenancy

## Multi-tenancy (banco compartilhado)
- Cada luthieria é um **tenant** (`TENANT_TB`). Todas as tabelas de negócio têm `tenant_id`.
- O JWT carrega o claim `tid`. O `TenantContextFilter` (security) copia o `tid` para o
  `TenantContext` (shared), e o Hibernate (`@TenantId` em `TenantScopedEntity`) filtra **todas** as
  consultas e preenche o tenant no INSERT. Nenhum repositório precisa de `where tenant_id = ?`.
- Registro de outra luthieria responde **404** (não revela que existe).
- Unicidade é por luthieria: CPF do cliente, SKU/código de barras do produto, título do serviço.
- `USER_TB` tem `tenant_id` mas **não** é filtrada automaticamente (login acontece antes de saber o
  tenant); `UserManagementServiceImpl` filtra explicitamente. E-mail de usuário é único global.

## Endpoints
| Rota | Descrição | Papéis |
|---|---|---|
| `POST /auth/register` | Cadastra luthieria + dono (ADMIN) e devolve tokens | público (rate limit) |
| `GET /users/me`, `GET /users` | Usuário logado / usuários da luthieria | todos / ADMIN |
| `POST/GET /customers`, `GET/PUT/DELETE /customers/{id}` | Clientes (`?search=` nome, telefone, CPF) | ADMIN, LUTHIER, ATENDENTE (DELETE: ADMIN) |
| `POST/GET /customers/{id}/instruments`, `PUT .../{instrumentId}` | Instrumentos do cliente | ADMIN, LUTHIER, ATENDENTE |
| `POST/GET /products`, `GET/PUT/DELETE /products/{id}`, `GET /products/barcode/{code}` | Estoque (`?search=&category=&lowStock=`) | escrita: ADMIN, LUTHIER, ESTOQUISTA; leitura: todos |
| `POST/GET /products/{id}/movements` | Entrada / saída / ajuste (saldo nunca negativo) | idem |
| `POST/GET /service-catalog`, `GET/PUT/DELETE /service-catalog/{id}` | Catálogo de serviços | escrita: ADMIN, LUTHIER; leitura: todos |

Valores de enum seguem o front (`violao_aco`, `cordas`, `saida`...). Telefone e CPF aceitam máscara;
CPF sai sempre mascarado (`***.982.247-**`). DELETE é exclusão lógica.

## Exemplo
```bash
curl -X POST localhost:8080/auth/register -H 'Content-Type: application/json' -d '{
  "workshop": {"name": "Oficina do Armando"},
  "owner": {"name": "Armando", "email": "armando@oficina.com", "password": "SenhaForte@2026",
            "cpf": "529.982.247-25", "phone": "(11) 98765-4321"}}'
```

## Rodando
`mvn install` e `java -jar srv-luthier-app/target/srv-luthier-app-1.0.0-SNAPSHOT.jar`
(H2 em memória; schema pelo Hibernate `ddl-auto: update`, Flyway desligado até termos migrations).
