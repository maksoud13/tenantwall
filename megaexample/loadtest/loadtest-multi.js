// ═══════════════════════════════════════════════════════════════
//  TenantWall — multi-strategy load test
//  STRATEGY env var: header | fixed | session | cookie | jwt
// ═══════════════════════════════════════════════════════════════
import http from 'k6/http';
import encoding from 'k6/encoding';
import { Counter, Rate, Trend } from 'k6/metrics';

export const options = {
  stages: [
    { duration: '15s', target: 20 },
    { duration: '60s', target: 20 },
    { duration: '15s', target: 0  },
  ],
  thresholds: {
    'success_rate':     ['rate>0.99'],
    'isolation_broken': ['count<1'],
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)'],
};

const BASE_URL     = __ENV.BASE_URL || 'http://localhost:8080';
const STRATEGY     = __ENV.STRATEGY || 'header';
const NUM_TENANTS  = 20;
const PROD_PER_TEN = 40000;

const isolationBroken = new Counter('isolation_broken');
const crossBlocked    = new Counter('cross_tenant_blocked');
const ownOk           = new Counter('own_tenant_ok');
const successRate     = new Rate('success_rate');

const latLookup = new Trend('lat_lookup_ms', true);
const latList   = new Trend('lat_list_ms',   true);
const latSku    = new Trend('lat_sku_ms',    true);
const latCross  = new Trend('lat_cross_ms',  true);
const latCte    = new Trend('lat_cte_ms',    true);

// ─── helpers ───
function tenantHeader(n) { return `TENANT_${String(n).padStart(3, '0')}`; }
function randomTenant()  { return Math.floor(Math.random() * NUM_TENANTS) + 1; }
function otherTenant(n)  { let o; do { o = randomTenant(); } while (o === n); return o; }
function productIdFor(n) { return (n - 1) * PROD_PER_TEN + Math.floor(Math.random() * PROD_PER_TEN) + 1; }

function b64url(s) {
  return encoding.b64encode(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}
function makeJwt(tenant) {
  const h = b64url('{"alg":"none","typ":"JWT"}');
  const p = b64url(JSON.stringify({ tenant_id: tenant, sub: 'loadtest' }));
  return `${h}.${p}.sig`;
}

function headersFor(tenantNum, ctx) {
  const t = tenantHeader(tenantNum);
  switch (STRATEGY) {
    case 'header':  return { 'X-Tenant-Id': t };
    case 'fixed':   return {};
    case 'session': {
      const sid = ctx.sessions[t];
      return sid ? { 'Cookie': `JSESSIONID=${sid}` } : {};
    }
    case 'cookie':  return { 'Cookie': `tenant_id=${t}` };
    case 'jwt':     return { 'Authorization': `Bearer ${makeJwt(t)}` };
    default:        return {};
  }
}

// ─── setup: create sessions once if needed ───
export function setup() {
  const ctx = { sessions: {} };
  if (STRATEGY === 'session') {
    for (let i = 1; i <= NUM_TENANTS; i++) {
      const t   = tenantHeader(i);
      const res = http.post(`${BASE_URL}/api/admin/session/${t}`);
      const sc  = String(res.headers['Set-Cookie'] || '');
      const m   = sc.match(/JSESSIONID=([^;]+)/);
      if (m) ctx.sessions[t] = m[1];
    }
    console.log(`setup: created ${Object.keys(ctx.sessions).length} sessions`);
  }
  return ctx;
}

export default function (ctx) {
  const ownNum  = STRATEGY === 'fixed' ? 1 : randomTenant();
  const headers = headersFor(ownNum, ctx);
  const r = Math.random();

  // 45% point lookup
  if (r < 0.45) {
    const id  = productIdFor(ownNum);
    const res = http.get(`${BASE_URL}/api/products/${id}`, { headers, tags: { type: 'lookup' } });
    successRate.add(res.status === 200 ? 1 : 0);
    if (res.status === 200) ownOk.add(1);
    latLookup.add(res.timings.duration);
    return;
  }

  // 15% paginated list
  if (r < 0.60) {
    const page = Math.floor(Math.random() * 100);
    const res  = http.get(`${BASE_URL}/api/products?page=${page}&size=20`, { headers, tags: { type: 'list' } });
    successRate.add(res.status === 200 ? 1 : 0);
    latList.add(res.timings.duration);
    return;
  }

  // 10% SKU lookup
  if (r < 0.70) {
    const n   = Math.floor(Math.random() * PROD_PER_TEN);
    const sku = `P-${tenantHeader(ownNum)}-${String(n).padStart(6, '0')}`;
    const res = http.get(`${BASE_URL}/api/products/by-sku/${sku}`, { headers, tags: { type: 'sku' } });
    successRate.add(res.status === 200 ? 1 : 0);
    latSku.add(res.timings.duration);
    return;
  }

  // 15% cross-tenant attack
  if (r < 0.85) {
    const otherNum = otherTenant(ownNum);
    const id       = productIdFor(otherNum);
    const res      = http.get(`${BASE_URL}/api/products/${id}`, { headers, tags: { type: 'cross' } });
    if (res.status === 403 || res.status === 404) {
      crossBlocked.add(1);
      successRate.add(1);
    } else if (res.status === 200) {
      isolationBroken.add(1);
      successRate.add(0);
      console.error(`LEAK: ${tenantHeader(ownNum)} read ${id} (tenant ${otherNum})`);
    } else {
      successRate.add(0);
    }
    latCross.add(res.timings.duration);
    return;
  }

  // 15% CTE report
  const res = http.get(`${BASE_URL}/api/products/reports/stock-value`, {
    headers, tags: { type: 'cte' }, timeout: '15s',
  });
  successRate.add(res.status === 200 ? 1 : 0);
  latCte.add(res.timings.duration);
}