#!/usr/bin/env bash
# Fluxo completo da API srv-luthieria em curl (mesma ordem da coleção do Postman).
# Requer: curl e jq. Uso: bash postman/fluxo-curl.sh   (API rodando em $BASE)
# Cada bloco pode ser copiado e rodado sozinho, desde que as variáveis anteriores existam.
set -uo pipefail

BASE=${BASE:-http://localhost:8080}
SENHA='SenhaForte@2026'
N=$(date +%s)
EMAIL_DONO="dono-$N@teste.com"
EMAIL_LUTHIER="luthier-$N@teste.com"

# CPF válido aleatório (dígitos verificadores corretos)
gera_cpf() {
  local d=($((RANDOM % 9 + 1))) i k s r
  for i in 1 2 3 4 5 6 7 8; do d+=($((RANDOM % 10))); done
  for k in 9 10; do
    s=0; for ((i = 0; i < k; i++)); do s=$((s + d[i] * (k + 1 - i))); done
    r=$(((s * 10) % 11)); [ $r -eq 10 ] && r=0; d+=($r)
  done
  printf '%s%s%s.%s%s%s.%s%s%s-%s%s' "${d[@]}"
}
CPF_LUTHIER=$(gera_cpf)

passo() { printf '\n\033[1m== %s\033[0m\n' "$1"; }

# ---------------------------------------------------------------- 0. Saúde
passo "0. Health"
curl -s "$BASE/actuator/health" | jq '{status, banco: .components.db.details.database}'

# ---------------------------------------------------------------- 1. Autenticação
passo "1.1 Cadastrar luthieria + dono"
RESP=$(curl -s -X POST "$BASE/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{
    "workshop": {"name": "Oficina Curl"},
    "owner": {"name": "Dono da Oficina", "email": "'"$EMAIL_DONO"'", "password": "'"$SENHA"'"}
  }')
echo "$RESP" | jq '{tokenType, expiresIn}'
TOKEN=$(echo "$RESP" | jq -r .accessToken)
REFRESH=$(echo "$RESP" | jq -r .refreshToken)

passo "1.2 Login (e-mail)"
RESP=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"identifier": "'"$EMAIL_DONO"'", "password": "'"$SENHA"'"}')
TOKEN=$(echo "$RESP" | jq -r .accessToken)
REFRESH=$(echo "$RESP" | jq -r .refreshToken)
echo "token: ${TOKEN:0:25}..."

passo "1.3 Quem sou eu"
curl -s "$BASE/users/me" -H "Authorization: Bearer $TOKEN" | jq '{name, email, profile, tenantId}'

passo "1.4 Renovar token (refresh)"
RESP=$(curl -s -X POST "$BASE/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken": "'"$REFRESH"'"}')
TOKEN=$(echo "$RESP" | jq -r .accessToken)
REFRESH=$(echo "$RESP" | jq -r .refreshToken)
echo "novo token: ${TOKEN:0:25}..."

# ---------------------------------------------------------------- 2. Usuários
passo "2.1 Criar luthier (com CPF $CPF_LUTHIER)"
curl -s -X POST "$BASE/users" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "name": "Rafael Luthier",
    "email": "'"$EMAIL_LUTHIER"'",
    "password": "'"$SENHA"'",
    "cpf": "'"$CPF_LUTHIER"'",
    "phone": "(11) 91234-5678",
    "profile": "LUTHIER"
  }' | jq '{id, name, profile}'

passo "2.2 Login do luthier (CPF)"
curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"identifier": "'"$CPF_LUTHIER"'", "password": "'"$SENHA"'"}' | jq '{tokenType, expiresIn}'

passo "2.3 Listar usuários"
curl -s "$BASE/users" -H "Authorization: Bearer $TOKEN" | jq '[.[] | {name, profile}]'

# ---------------------------------------------------------------- 3. Clientes e instrumentos
passo "3.1 Criar cliente"
CUSTOMER_ID=$(curl -s -X POST "$BASE/customers" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "name": "Lucas Silveira",
    "phone": "(11) 98765-4321",
    "email": "lucas@exemplo.com",
    "cpf": "529.982.247-25",
    "notes": "Prefere cordas 0.10"
  }' | jq -r .id)
echo "CUSTOMER_ID=$CUSTOMER_ID"

passo "3.2 Buscar clientes"
curl -s "$BASE/customers?search=Lucas&page=0&size=20" -H "Authorization: Bearer $TOKEN" \
  | jq '[.content[] | {id, name, phone, maskedCpf}]'

passo "3.3 Detalhar cliente"
curl -s "$BASE/customers/$CUSTOMER_ID" -H "Authorization: Bearer $TOKEN" | jq '{name, phone, maskedCpf}'

passo "3.4 Cadastrar instrumento"
INSTRUMENT_ID=$(curl -s -X POST "$BASE/customers/$CUSTOMER_ID/instruments" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "type": "guitarra",
    "brand": "Fender",
    "model": "Stratocaster",
    "serialNumber": "MX123456",
    "year": 2018,
    "finishColor": "Sunburst"
  }' | jq -r .id)
echo "INSTRUMENT_ID=$INSTRUMENT_ID"

passo "3.5 Instrumentos do cliente"
curl -s "$BASE/customers/$CUSTOMER_ID/instruments" -H "Authorization: Bearer $TOKEN" \
  | jq '[.[] | {id, type, brand, model}]'

# ---------------------------------------------------------------- 4. Estoque
passo "4.1 Criar produto"
PRODUCT_ID=$(curl -s -X POST "$BASE/products" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "name": "Encordoamento 0.10",
    "category": "cordas",
    "brand": "DAddario",
    "sku": "EXL110-'"$N"'",
    "unit": "jogo",
    "costPrice": 25,
    "salePrice": 45,
    "minQuantity": 2,
    "initialQuantity": 10
  }' | jq -r .id)
echo "PRODUCT_ID=$PRODUCT_ID"

passo "4.2 Entrada de estoque"
curl -s -X POST "$BASE/products/$PRODUCT_ID/movements" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"type": "entrada", "quantity": 5, "reason": "Compra fornecedor"}' | jq '{type, quantity, reason}'

passo "4.3 Movimentações do produto"
curl -s "$BASE/products/$PRODUCT_ID/movements" -H "Authorization: Bearer $TOKEN" \
  | jq '[.content[] | {type, quantity, reason}]'

passo "4.4 Produtos com estoque baixo"
curl -s "$BASE/products?lowStock=true" -H "Authorization: Bearer $TOKEN" | jq '.content | length'

# ---------------------------------------------------------------- 5. Catálogo de serviços
passo "5.1 Criar serviço"
CATALOG_ID=$(curl -s -X POST "$BASE/service-catalog" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "title": "Regulagem completa",
    "description": "Altura de cordas, tensor, oitavas",
    "laborPrice": 180.00,
    "estimatedDays": 3
  }' | jq -r .id)
echo "CATALOG_ID=$CATALOG_ID"

passo "5.2 Listar serviços"
curl -s "$BASE/service-catalog" -H "Authorization: Bearer $TOKEN" | jq '[.[] | {title, laborPrice}]'

# ---------------------------------------------------------------- 6. Ordem de serviço
passo "6.1 Abrir OS"
ORDER_ID=$(curl -s -X POST "$BASE/service-orders" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "customerId": "'"$CUSTOMER_ID"'",
    "instrumentId": "'"$INSTRUMENT_ID"'",
    "priority": "alta",
    "reportedIssue": "Trastejando na 5a casa",
    "estimatedDeliveryDate": "2030-01-10",
    "services": [{"catalogId": "'"$CATALOG_ID"'"}]
  }' | jq -r .id)
echo "ORDER_ID=$ORDER_ID"

passo "6.2 Detalhar OS"
curl -s "$BASE/service-orders/$ORDER_ID" -H "Authorization: Bearer $TOKEN" \
  | jq '{orderNumber, status, services: [.services[].title], totalAmount}'

passo "6.3 Status → diagnóstico"
curl -s -X POST "$BASE/service-orders/$ORDER_ID/status" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status": "diagnostico"}' | jq '{status}'

passo "6.4 Status → orçamento"
curl -s -X POST "$BASE/service-orders/$ORDER_ID/status" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status": "orcamento"}' | jq '{status}'

passo "6.5 Adicionar peça"
curl -s -X POST "$BASE/service-orders/$ORDER_ID/parts" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"productId": "'"$PRODUCT_ID"'", "quantity": 1}' | jq '{totalAmount}'

passo "6.6 Enviar orçamento"
TRACKING=$(curl -s -X POST "$BASE/service-orders/$ORDER_ID/quote/send" \
  -H "Authorization: Bearer $TOKEN" | jq -r .clientTrackingToken)
echo "TRACKING=$TRACKING"

passo "6.7 Portal: ver OS (sem login)"
curl -s "$BASE/portal/orders/$TRACKING" | jq '{customerFirstName, instrument, status, total: .quote.grandTotal}'

passo "6.8 Portal: aprovar orçamento (sem login)"
curl -s -X POST "$BASE/portal/orders/$TRACKING/quote/approve" | jq '{status}'

passo "6.9 Status → pronto"
curl -s -X POST "$BASE/service-orders/$ORDER_ID/status" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status": "pronto"}' | jq '{status}'

passo "6.10 Registrar pagamento"
curl -s -X POST "$BASE/service-orders/$ORDER_ID/payments" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"amount": 225.00, "method": "pix"}' | jq '{balance}'

passo "6.11 Status → entregue"
curl -s -X POST "$BASE/service-orders/$ORDER_ID/status" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status": "entregue"}' | jq '{status}'

passo "6.12 Listar OS (kanban)"
curl -s "$BASE/service-orders?full=true" -H "Authorization: Bearer $TOKEN" \
  | jq '[.content[] | {orderNumber, status}]'

# ---------------------------------------------------------------- 7. Área do cliente
passo "7.1 Reenviar código de acesso (balcão) — o anterior deixa de valer"
curl -s -X POST "$BASE/customers/$CUSTOMER_ID/access-code" \
  -H "Authorization: Bearer $TOKEN" | jq '{status, body}'

# O código não sai na API: vem pelo WhatsApp (em dev, no log: "[DEV] Código de acesso").
CODE=${CODE:-}
if [ -z "$CODE" ]; then
  read -r -p "Cole o código do log da API (XXXX-XXXX): " CODE
fi

passo "7.2 Login do cliente (só o código)"
CLIENT_TOKEN=$(curl -s -X POST "$BASE/auth/customer/login" \
  -H 'Content-Type: application/json' \
  -d '{"code": "'"$CODE"'"}' | jq -r .accessToken)
echo "CLIENT_TOKEN: ${CLIENT_TOKEN:0:25}..."

passo "7.3 Minhas OS (cliente)"
curl -s "$BASE/client/orders" -H "Authorization: Bearer $CLIENT_TOKEN" \
  | jq '[.[] | {orderNumber, instrument, status}]'

passo "7.4 Cliente tentando rota do balcão (esperado 403)"
curl -s -o /dev/null -w '%{http_code}\n' "$BASE/customers" -H "Authorization: Bearer $CLIENT_TOKEN"

# ---------------------------------------------------------------- 8. Relatórios, mensagens e auditoria
passo "8.1 Dashboard"
curl -s "$BASE/reports/dashboard" -H "Authorization: Bearer $TOKEN" | jq .

passo "8.2 Financeiro"
curl -s "$BASE/reports/financial" -H "Authorization: Bearer $TOKEN" | jq '{received, deliveredOrders, byMethod}'

passo "8.3 Mensagens (WhatsApp)"
curl -s "$BASE/notifications?channel=whatsapp" -H "Authorization: Bearer $TOKEN" \
  | jq '[.content[] | {subject, status, body}]'

passo "8.4 Auditoria"
curl -s "$BASE/audit-logs" -H "Authorization: Bearer $TOKEN" | jq '[.content[] | {action, actor}] | .[0:8]'

# ---------------------------------------------------------------- 9. Encerrar sessão
passo "9. Logout"
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE/auth/logout" \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken": "'"$REFRESH"'"}'
