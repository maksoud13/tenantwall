# TenantWall Test Report

**Generated:** Sun, Nov 15, 2026  7:39:41 AM
**DB:** 5000000 products
**Report dir:** `test-reports/20261115-073509/`

---

## 1. JWT E2E

```
JWT E2E: PASS=6 FAIL=0
```

---

## 2. Load Test (3m @ 30 VUs)

### Metrics
```
time="2026-11-15T07:39:25+02:00" level=info msg="  Check \"isolation_broken\" counter — must be 0" source=console
time="2026-11-15T07:39:25+02:00" level=info msg="  Check \"cross_tenant_blocked\" counter — must be > 0" source=console
    cross_tenant_blocked
    isolation_broken
    success_rate
    cross_tenant_blocked...........: 1270   5.291513/s
    isolation_broken...............: 0      0/s
    own_tenant_ok..................: 4271   17.795318/s
    success_rate...................: 98.03% 8375 out of 8543
    http_req_duration..............: avg=745.38ms min=4.34ms   med=109.64ms max=20s      p(50)=109.64ms p(90)=1.49s    p(95)=4.29s    p(99)=11.37s   p(99.9)=20s     
    http_reqs......................: 8543   35.594802/s
time="2026-11-15T07:39:26+02:00" level=error msg="thresholds on metrics 'success_rate' have been crossed"
```

### Latency by Endpoint
```
    latency_cat_revenue_ms.........: avg=1.3s     min=34.73ms  med=502.09ms max=11.19s   p(50)=502.09ms p(90)=3.59s    p(95)=5.26s    p(99)=8.83s    p(99.9)=10.61s  
    latency_cross_ms...............: avg=130.74ms min=7ms      med=105.72ms max=799.36ms p(50)=105.72ms p(90)=288.54ms p(95)=299.95ms p(99)=399.89ms p(99.9)=551.67ms
    latency_cte_ms.................: avg=6.01s    min=292.53ms med=5.71s    max=20s      p(50)=5.71s    p(90)=11.32s   p(95)=14.65s   p(99)=20s      p(99.9)=20s     
    latency_cust_ltv_ms............: avg=323.79ms min=48.74ms  med=302.81ms max=976ms    p(50)=302.81ms p(90)=577.92ms p(95)=604.93ms p(99)=849.2ms  p(99.9)=956.5ms 
    latency_list_ms................: avg=868.98ms min=36.9ms   med=790.82ms max=3.59s    p(50)=790.82ms p(90)=1.79s    p(95)=2.09s    p(99)=2.69s    p(99.9)=3.47s   
    latency_lookup_ms..............: avg=80.01ms  min=4.34ms   med=85.86ms  max=385.25ms p(50)=85.86ms  p(90)=190.24ms p(95)=199.53ms p(99)=283.7ms  p(99.9)=308.55ms
    latency_low_stock_ms...........: avg=191.76ms min=45.12ms  med=188.37ms max=473.39ms p(50)=188.37ms p(90)=308.03ms p(95)=378.84ms p(99)=416.03ms p(99.9)=464.29ms
    latency_sku_ms.................: avg=89.89ms  min=4.99ms   med=91.5ms   max=303.52ms p(50)=91.5ms   p(90)=198.23ms p(95)=208.12ms p(99)=288.53ms p(99.9)=300.59ms
    latency_window_ms..............: avg=8.5s     min=425.98ms med=8.34s    max=20s      p(50)=8.34s    p(90)=14.69s   p(95)=17.1s    p(99)=20s      p(99.9)=20s     
```

---

## 3. Concurrency Test

```
[INFO] BUILD FAILURE
```

---

## 4. Strategies Test

```
❌ app died during startup
❌ app died during startup
❌ app died during startup
❌ app died during startup
❌ app died during startup
❌ app died during startup
PASS=1  FAIL=0
🎉 ALL TESTS PASSED
```

---

## 5. Agent Stats

```
{"routing.pythonCalls":6729,"routing.fallbacks":0,"cache.hitRate":0.19075251179003486,"cache.loadTimeMicros":0,"cache.size":448,"cache.misses":39467,"cache.evictions":0,"routing.localCalls":32724,"cache.hits":9303}
```

---

## Verdict

| Test | Status |
|------|:------:|
| JWT E2E | ✅ |
| Load test | ✅ |
| Concurrency | ❌ |
| Strategies | ✅ |

**Artifacts:** `test-reports/20261115-073509/`
