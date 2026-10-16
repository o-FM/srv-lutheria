# Testes de carga

Script [k6](https://k6.io) que simula várias luthierias (multi-tenant) usando a API ao mesmo tempo:
consultas do balcão (dashboard, busca, kanban, estoque) e o ciclo completo da OS (abertura →
orçamento → aprovação pelo portal → pronto com baixa de estoque → pagamento → entrega).

```bash
# Ambiente: docker compose up (ou a app local) com rate limit alto para o IP do gerador
export APP_SECURITY_RATE_LIMIT_LOGIN_CAPACITY=100000
export APP_SECURITY_RATE_LIMIT_PORTAL_CAPACITY=100000

k6 run -e PROFILE=smoke  load-tests/k6-luthieria.js   # sanidade (1 VU)
k6 run                   load-tests/k6-luthieria.js   # carga: 20 VUs por 2 min
k6 run -e PROFILE=stress load-tests/k6-luthieria.js   # estresse: até 100 VUs

# Sem k6 instalado:
docker run --rm -i --network host -v "$PWD/load-tests:/scripts" grafana/k6 run /scripts/k6-luthieria.js
```

Critérios de aceite (thresholds — o k6 sai com erro se falharem):

| Métrica | Limite |
|---|---|
| `http_req_failed` | < 1% |
| `http_req_duration` p95 | < 500 ms |
| `order_lifecycle_duration` p95 (9 chamadas) | < 3 s |
| `checks` | > 99% |

Durante o teste acompanhe no Prometheus (`http://localhost:9090`):
`http_server_requests_seconds`, `hikaricp_connections_active` e
`luthieria_orders_transitions_total`; publicações pendentes do outbox ficam na tabela
`event_publication` (`completion_date is null`).
