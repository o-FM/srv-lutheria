# Scripts PostgreSQL

Scripts para criar o banco da aplicação manualmente (DBA, ambientes sem permissão de DDL para a
aplicação, revisão do modelo). Testados no PostgreSQL 17; compatíveis com 15+.

| Arquivo | O que faz | Executar como |
|---|---|---|
| `01_create_database.sql` | Cria o usuário `luthieria_app` e o banco `luthieria` (UTF-8) | superusuário (`postgres`) |
| `02_schema.sql` | Cria as 19 tabelas, índices e restrições (numa transação) | `luthieria_app`, no banco `luthieria` |
| `99_drop_all.sql` | **Apaga tudo** (só dev/homologação) | `luthieria_app` |

```bash
psql -h localhost -U postgres -v app_password="'TroqueEstaSenha'" -f db/postgresql/01_create_database.sql
psql -h localhost -U luthieria_app -d luthieria -f db/postgresql/02_schema.sql
```

## Relação com o Flyway

A aplicação **já cria e evolui o schema sozinha** pelas migrations Flyway de cada módulo
(`srv-luthier-*/src/main/resources/db/migration`, versões 1.0 a 4.4). O `02_schema.sql` é a
consolidação dessas migrations no estado atual — o schema resultante é idêntico (conferido coluna
a coluna, restrições e índices).

Escolha **um** dos caminhos:

1. **Flyway cria tudo (recomendado):** rode só o `01_create_database.sql` e suba a aplicação.
2. **Scripts manuais:** rode `01` e `02` e, na **primeira** subida da aplicação, informe a versão
   já aplicada para o Flyway não tentar recriar as tabelas:

   ```bash
   SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
   SPRING_FLYWAY_BASELINE_VERSION=4.4
   ```

   Migrations futuras (V4.4 em diante) continuam sendo aplicadas normalmente pelo Flyway.

Ao criar uma migration nova num módulo, atualize também o `02_schema.sql` e a versão de baseline
acima.

## Modelo

```
tenant_tb ─┬─ user_tb                        (security)
           └─ (tenant_id em todas as tabelas de negócio)

customer_tb ── instrument_tb                 (customer)
product_tb ── stock_movement_tb              (product)
service_catalog_tb                           (service)
service_order_tb ─┬─ service_order_item_tb
                  ├─ service_order_part_tb
                  ├─ service_order_timeline_tb
                  └─ payment_tb
service_order_counter_tb  (numeração por luthieria)
notification_tb                              (notification)
audit_log_tb, refresh_token_tb, password_reset_token_tb, customer_access_code_tb
event_publication          (outbox do Spring Modulith)
```

- Chaves estrangeiras só **dentro** de cada módulo. Referências entre módulos (ex.:
  `service_order_tb.customer_id`) são apenas por ID, para os módulos poderem virar serviços
  separados com bancos próprios.
- Unicidades de negócio incluem `tenant_id` (CPF, SKU, código de barras, número da OS…): cada
  luthieria tem os seus.
- Enums ficam como `varchar` + `CHECK` com os valores aceitos.
- `version` nas tabelas de negócio é o lock otimista do Hibernate.
