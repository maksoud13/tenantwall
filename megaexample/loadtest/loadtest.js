// ═══════════════════════════════════════════════════════════════
//  TenantWall — megaexample Load Test
//  Workload: 95% own-tenant + 5% cross-tenant attempts
// ═══════════════════════════════════════════════════════════════
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '2m',  target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    'success_rate': ['rate>0.99'],
    'http_req_duration{type:oltp_lookup}': ['p(99)<100'],
    'http_req_duration{type:oltp_list}':   ['p(99)<200'],
    'http_req_duration{type:olap_cte}':    ['p(99)<500'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const NUM_TENANTS = 20;
const PRODUCTS_PER_TENANT = 40000;

// Custom metrics
const ownHits = new Counter('own_tenant_ok');
const crossBlocked = new Counter('cross_tenant_blocked');
const isolationBroken = new Counter('ISOLATION_BROKEN');
const notFound = new Counter('not_found_404');
const successRate = new Rate('success_rate');

function randomTenant() {
  return Math.floor(Math.random() * NUM_TENANTS) + 1;
}

function productIdForTenant(tenantNum) {
  const base = (tenantNum - 1) * PRODUCTS_PER_TENANT;
  return base + Math.floor(Math.random() * PRODUCTS_PER_TENANT) + 1;
}

function tenantHeader(num) {
  return `TENANT_${String(num).padStart(3, '0')}`;
}

export default function () {
  const ownTenant = randomTenant();
  const headers = { 'X-Tenant-Id': tenantHeader(ownTenant) };

  // 5% cross-tenant attempts (should return 403 or 404, NOT 200)
  const isCrossTenant = Math.random() < 0.05;

  let targetProductId;
  if (isCrossTenant) {
    let otherTenant;
    do { otherTenant = randomTenant(); } while (otherTenant === ownTenant);
    targetProductId = productIdForTenant(otherTenant);
  } else {
    targetProductId = productIdForTenant(ownTenant);
  }

  const url = `${BASE_URL}/api/products/${targetProductId}`;
  const res = http.get(url, {
    headers,
    tags: { type: isCrossTenant ? 'cross_tenant' : 'oltp_lookup' },
  });

  if (isCrossTenant) {
    // Expect 403 (isolated) or 404 (not found)
    if (res.status === 403) {
      crossBlocked.add(1);
      successRate.add(1);
    } else if (res.status === 404) {
      notFound.add(1);
      successRate.add(1);
    } else if (res.status === 200) {
      isolationBroken.add(1);
      successRate.add(0);
      console.error(`🚨 ISOLATION BROKEN: ${headers['X-Tenant-Id']} read product ${targetProductId}`);
    } else {
      successRate.add(0);
    }
  } else {
    // Own product → expect 200
    if (res.status === 200) {
      ownHits.add(1);
      successRate.add(1);
    } else {
      successRate.add(0);
    }
  }

  // 10% list requests
  if (Math.random() < 0.10) {
    const listRes = http.get(`${BASE_URL}/api/products?page=0&size=20`, {
      headers,
      tags: { type: 'oltp_list' },
    });
    successRate.add(listRes.status === 200 ? 1 : 0);
  }

  // 5% CTE (Python)
  if (Math.random() < 0.05) {
    const cteRes = http.get(`${BASE_URL}/api/products/reports/stock-value`, {
      headers,
      tags: { type: 'olap_cte' },
    });
    successRate.add(cteRes.status === 200 ? 1 : 0);
  }
}

// Warmup mode (short, no full load)
export function warmup() {
  const tenant = randomTenant();
  const headers = { 'X-Tenant-Id': tenantHeader(tenant) };
  const id = productIdForTenant(tenant);
  http.get(`${BASE_URL}/api/products/${id}`, { headers });
  http.get(`${BASE_URL}/api/products?page=0&size=20`, { headers });
  http.get(`${BASE_URL}/api/products/reports/stock-value`, { headers });
}
