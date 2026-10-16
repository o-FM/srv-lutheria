// Teste de carga da API da Luthieria (k6: https://k6.io).
//
//   k6 run load-tests/k6-luthieria.js                       # perfil "load" padrão
//   k6 run -e PROFILE=smoke load-tests/k6-luthieria.js      # 1 usuário, sanidade
//   k6 run -e PROFILE=stress -e BASE_URL=https://staging... load-tests/k6-luthieria.js
//
// Cada VU simula uma luthieria diferente (multi-tenant real): cadastra a oficina no setup,
// depois executa o dia a dia do balcão e da bancada. O rate limit de /auth precisa estar alto
// no ambiente de teste (APP_SECURITY_RATE_LIMIT_LOGIN_CAPACITY), pois todos os VUs saem do
// mesmo IP.
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TENANTS = Number(__ENV.TENANTS || 20);

const profiles = {
  smoke: { vus: 1, iterations: 5 },
  load: {
    stages: [
      { duration: '30s', target: 20 },
      { duration: '2m', target: 20 },
      { duration: '30s', target: 0 },
    ],
  },
  stress: {
    stages: [
      { duration: '1m', target: 50 },
      { duration: '3m', target: 100 },
      { duration: '1m', target: 0 },
    ],
  },
};

export const options = {
  ...profiles[__ENV.PROFILE || 'load'],
  thresholds: {
    http_req_failed: ['rate<0.01'],          // < 1% de erros
    http_req_duration: ['p(95)<500'],        // p95 < 500 ms no geral
    'order_lifecycle_duration': ['p(95)<3000'],
    'checks': ['rate>0.99'],
  },
};

const orderLifecycle = new Trend('order_lifecycle_duration', true);

function json(token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  return { headers };
}

function post(path, body, token, tag) {
  return http.post(`${BASE_URL}${path}`, JSON.stringify(body || {}), {
    ...json(token), tags: { name: tag || path },
  });
}

function get(path, token, tag) {
  return http.get(`${BASE_URL}${path}`, { ...json(token), tags: { name: tag || path } });
}

// Uma luthieria por "slot", com cliente, instrumento, produto e serviço de catálogo prontos.
export function setup() {
  const tenants = [];
  for (let i = 0; i < TENANTS; i++) {
    const reg = post('/auth/register', {
      workshop: { name: `Oficina Carga ${i}` },
      owner: { name: 'Dono', email: `carga-${Date.now()}-${i}@teste.com`, password: 'SenhaForte@2026' },
    }, null, '/auth/register');
    check(reg, { 'cadastro de luthieria 201': (r) => r.status === 201 });
    const token = reg.json('accessToken');

    const customer = post('/customers', { name: 'Cliente Carga', phone: '11987654321' }, token).json('id');
    const instrument = post(`/customers/${customer}/instruments`,
      { type: 'guitarra', brand: 'Fender', model: 'Strat' }, token, '/customers/{id}/instruments').json('id');
    const product = post('/products', {
      name: 'Corda 0.10', category: 'cordas', sku: `C${i}`, unit: 'jogo',
      costPrice: 20, salePrice: 40, minQuantity: 1, initialQuantity: 100000,
    }, token).json('id');
    const catalog = post('/service-catalog', { title: 'Regulagem', laborPrice: 150 }, token).json('id');
    tenants.push({ token, customer, instrument, product, catalog });
  }
  return { tenants };
}

export default function (data) {
  const t = data.tenants[(__VU - 1) % data.tenants.length];

  group('balcão: consultas', () => {
    check(get('/reports/dashboard', t.token), { 'dashboard 200': (r) => r.status === 200 });
    check(get('/customers?search=Cliente', t.token, '/customers?search'), { 'busca 200': (r) => r.status === 200 });
    check(get('/service-orders?status=em_execucao', t.token, '/service-orders?status'), { 'kanban 200': (r) => r.status === 200 });
    check(get('/products?lowStock=true', t.token, '/products?lowStock'), { 'estoque 200': (r) => r.status === 200 });
  });

  group('ciclo completo da OS', () => {
    const start = Date.now();
    const order = post('/service-orders', {
      customerId: t.customer, instrumentId: t.instrument, reportedIssue: 'Teste de carga',
      services: [{ catalogId: t.catalog }],
    }, t.token);
    if (!check(order, { 'OS criada 201': (r) => r.status === 201 })) return;
    const id = order.json('id');

    post(`/service-orders/${id}/status`, { status: 'orcamento' }, t.token, '/service-orders/{id}/status');
    post(`/service-orders/${id}/parts`, { productId: t.product, quantity: 1 }, t.token, '/service-orders/{id}/parts');
    const quote = post(`/service-orders/${id}/quote/send`, null, t.token, '/service-orders/{id}/quote/send');
    const token = quote.json('clientTrackingToken');

    check(get(`/portal/orders/${token}`, null, '/portal/orders/{token}'), { 'portal 200': (r) => r.status === 200 });
    post(`/portal/orders/${token}/quote/approve`, null, null, '/portal/orders/{token}/quote/approve');
    post(`/service-orders/${id}/status`, { status: 'pronto' }, t.token, '/service-orders/{id}/status');
    post(`/service-orders/${id}/payments`, { amount: 190, method: 'pix' }, t.token, '/service-orders/{id}/payments');
    const delivered = post(`/service-orders/${id}/status`, { status: 'entregue' }, t.token, '/service-orders/{id}/status');
    check(delivered, { 'OS entregue': (r) => r.status === 200 && r.json('status') === 'entregue' });
    orderLifecycle.add(Date.now() - start);
  });

  sleep(1);
}
