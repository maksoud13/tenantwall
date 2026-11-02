// ═══════════════════════════════════════════════════════════════
//  TenantWall — megaexample Load Test
//  Workload: OLTP + OLAP + Cross-tenant + 3 Complex Reports
//  Compatible with k6 v0.40+ (no responseCallback)
// ═══════════════════════════════════════════════════════════════

import http from 'k6/http';
import { Counter, Rate, Trend } from 'k6/metrics';

export const options = {
  stages: [
    { duration: '30s', target: 30 },
    { duration: '3m',  target: 30 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    'success_rate': ['rate>0.99'],
    'isolation_broken': ['count<1'],
    'cross_tenant_blocked': ['count>0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const NUM_TENANTS = parseInt(__ENV.NUM_TENANTS || '20');
const PRODUCTS_PER_TENANT = parseInt(__ENV.PRODUCTS_PER_TENANT || '40000');

// ═══ Metrics ═══
const isolationBroken = new Counter('isolation_broken');
const crossTenantBlocked = new Counter('cross_tenant_blocked');
const ownTenantOk = new Counter('own_tenant_ok');
const notFound404 = new Counter('not_found_404');
const serverErrors5xx = new Counter('server_errors_5xx');
const successRate = new Rate('success_rate');

const latencyLookup = new Trend('latency_lookup_ms', true);
const latencyList = new Trend('latency_list_ms', true);
const latencySku = new Trend('latency_sku_ms', true);
const latencyCte = new Trend('latency_cte_ms', true);
const latencyWindow = new Trend('latency_window_ms', true);
const latencyCross = new Trend('latency_cross_ms', true);
const latencyCatRevenue = new Trend('latency_cat_revenue_ms', true);
const latencyCustLtv = new Trend('latency_cust_ltv_ms', true);
const latencyLowStock = new Trend('latency_low_stock_ms', true);

// ═══ Helpers ═══
function randomTenantNum() {
  return Math.floor(Math.random() * NUM_TENANTS) + 1;
}

function tenantHeader(num) {
  return `TENANT_${String(num).padStart(3, '0')}`;
}

function productIdForTenant(tenantNum) {
  const base = (tenantNum - 1) * PRODUCTS_PER_TENANT;
  return base + Math.floor(Math.random() * PRODUCTS_PER_TENANT) + 1;
}

function nonExistentId() {
  return NUM_TENANTS * PRODUCTS_PER_TENANT + Math.floor(Math.random() * 1000000) + 1;
}

function differentTenant(ownNum) {
  let other;
  do { other = randomTenantNum(); } while (other === ownNum);
  return other;
}

// ═══════════════════════════════════════════════════════════════
//  Main iteration
// ═══════════════════════════════════════════════════════════════
export default function () {
  const ownTenantNum = randomTenantNum();
  const ownTenant = tenantHeader(ownTenantNum);
  const headers = { 'X-Tenant-Id': ownTenant };

  const r = Math.random();

  // ─────────────────────────────────────────────────────────────
  //  40%: Point lookup
  // ─────────────────────────────────────────────────────────────
  if (r < 0.40) {
    const id = productIdForTenant(ownTenantNum);
    const res = http.get(`${BASE_URL}/api/products/${id}`, {
      headers,
      tags: { type: 'oltp_lookup' },
    });
    if (res.status === 200) { ownTenantOk.add(1); successRate.add(1); }
    else successRate.add(0);
    latencyLookup.add(res.timings.duration);
    return;
  }

  // ─────────────────────────────────────────────────────────────
  //  14%: Paginated list
  // ─────────────────────────────────────────────────────────────
  if (r < 0.54) {
    const page = Math.floor(Math.random() * 100);
    const res = http.get(`${BASE_URL}/api/products?page=${page}&size=20`, {
      headers,
      tags: { type: 'oltp_list' },
    });
    successRate.add(res.status === 200 ? 1 : 0);
    if (res.status >= 500) serverErrors5xx.add(1);
    latencyList.add(res.timings.duration);
    return;
  }

  // ─────────────────────────────────────────────────────────────
  //  10%: SKU lookup
  // ─────────────────────────────────────────────────────────────
  if (r < 0.64) {
    const n = Math.floor(Math.random() * PRODUCTS_PER_TENANT);
    const sku = `P-${ownTenant}-${String(n).padStart(6, '0')}`;
    const res = http.get(`${BASE_URL}/api/products/by-sku/${sku}`, {
      headers,
      tags: { type: 'oltp_sku' },
    });
    if (res.status === 200) { ownTenantOk.add(1); successRate.add(1); }
    else successRate.add(0);
    latencySku.add(res.timings.duration);
    return;
  }

  // ─────────────────────────────────────────────────────────────
  //  15%: Cross-tenant attack
  // ─────────────────────────────────────────────────────────────
  if (r < 0.79) {
    const otherNum = differentTenant(ownTenantNum);
    const otherId = productIdForTenant(otherNum);
    const res = http.get(`${BASE_URL}/api/products/${otherId}`, {
      headers,
      tags: { type: 'cross_tenant' },
    });
    if (res.status === 403 || res.status === 404) {
      crossTenantBlocked.add(1); successRate.add(1);
    } else if (res.status === 200) {
      isolationBroken.add(1); successRate.add(0);
      console.error(`[CRITICAL] LEAK: ${ownTenant} read ${otherId} from ${tenantHeader(otherNum)}`);
    } else successRate.add(0);
    latencyCross.add(res.timings.duration);
    return;
  }

  // ─────────────────────────────────────────────────────────────
  //  5%: CTE report
  // ─────────────────────────────────────────────────────────────
  if (r < 0.84) {
    const res = http.get(`${BASE_URL}/api/products/reports/stock-value`, {
      headers,
      tags: { type: 'olap_cte' },
      timeout: '15s',
    });
    successRate.add(res.status === 200 ? 1 : 0);
    if (res.status >= 500) serverErrors5xx.add(1);
    latencyCte.add(res.timings.duration);
    return;
  }

  // ─────────────────────────────────────────────────────────────
  //  5%: Window function
  // ─────────────────────────────────────────────────────────────
  if (r < 0.89) {
    const res = http.get(`${BASE_URL}/api/products/reports/top-categories`, {
      headers,
      tags: { type: 'olap_window' },
      timeout: '15s',
    });
    successRate.add(res.status === 200 ? 1 : 0);
    if (res.status >= 500) serverErrors5xx.add(1);
    latencyWindow.add(res.timings.duration);
    return;
  }

  // ─────────────────────────────────────────────────────────────
  //  4%: NEW — category-revenue
  // ─────────────────────────────────────────────────────────────
  if (r < 0.93) {
    const res = http.get(`${BASE_URL}/api/reports/category-revenue`, {
      headers,
      tags: { type: 'olap_cat_revenue' },
      timeout: '15s',
    });
    successRate.add(res.status === 200 ? 1 : 0);
    if (res.status >= 500) serverErrors5xx.add(1);
    latencyCatRevenue.add(res.timings.duration);
    return;
  }

  // ─────────────────────────────────────────────────────────────
  //  3%: NEW — customer-ltv
  // ─────────────────────────────────────────────────────────────
  if (r < 0.96) {
    const res = http.get(`${BASE_URL}/api/reports/customer-ltv`, {
      headers,
      tags: { type: 'olap_cust_ltv' },
      timeout: '15s',
    });
    successRate.add(res.status === 200 ? 1 : 0);
    if (res.status >= 500) serverErrors5xx.add(1);
    latencyCustLtv.add(res.timings.duration);
    return;
  }

  // ─────────────────────────────────────────────────────────────
  //  2%: NEW — low-stock
  // ─────────────────────────────────────────────────────────────
  if (r < 0.98) {
    const res = http.get(`${BASE_URL}/api/reports/low-stock`, {
      headers,
      tags: { type: 'olap_low_stock' },
      timeout: '15s',
    });
    successRate.add(res.status === 200 ? 1 : 0);
    if (res.status >= 500) serverErrors5xx.add(1);
    latencyLowStock.add(res.timings.duration);
    return;
  }

  // ─────────────────────────────────────────────────────────────
  //  2%: Not-found (edge case)
  // ─────────────────────────────────────────────────────────────
  const id = nonExistentId();
  const res = http.get(`${BASE_URL}/api/products/${id}`, {
    headers,
    tags: { type: 'not_found' },
  });
  if (res.status === 404) { notFound404.add(1); successRate.add(1); }
  else successRate.add(0);
}

// ═══ Lifecycle hooks ═══
export function setup() {
  console.log('');
  console.log('═══════════════════════════════════════════════════════');
  console.log('  TenantWall Load Test');
  console.log('═══════════════════════════════════════════════════════');
  console.log(`  Base URL:             ${BASE_URL}`);
  console.log(`  Tenants:              ${NUM_TENANTS}`);
  console.log(`  Products per tenant:  ${PRODUCTS_PER_TENANT}`);
  console.log('');
  console.log('  Workload mix:');
  console.log('    40%  point lookup           (OLTP)');
  console.log('    14%  paginated list         (OLTP)');
  console.log('    10%  SKU lookup             (OLTP)');
  console.log('    15%  cross-tenant           (SECURITY)');
  console.log('     5%  CTE report             (OLAP/Python)');
  console.log('     5%  window function        (OLAP/Python)');
  console.log('     4%  category-revenue       (OLAP/4 tables)');
  console.log('     3%  customer-ltv           (OLAP/6 tables)');
  console.log('     2%  low-stock              (OLAP/7 tables)');
  console.log('     2%  not-found              (edge case)');
  console.log('═══════════════════════════════════════════════════════');
  console.log('');
}

export function teardown(data) {
  console.log('');
  console.log('═══════════════════════════════════════════════════════');
  console.log('  Load test complete');
  console.log('═══════════════════════════════════════════════════════');
  console.log('  Check "isolation_broken" counter — must be 0');
  console.log('  Check "cross_tenant_blocked" counter — must be > 0');
  console.log('═══════════════════════════════════════════════════════');
  console.log('');
}
