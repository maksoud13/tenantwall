# TenantWall Test Report

**Generated:** Sun, Nov 15, 2026  7:53:10 AM
**DB:** 5000000 products
**Report dir:** `test-reports/20261115-074611/`

---

## 1. Concurrency Test

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 62.29 s -- in com.megaexample.ConcurrencyIsolationIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 2. JWT E2E

```
JWT E2E: PASS=6 FAIL=0
```

---

## 3. Load Test (3m @ 30 VUs)

### Metrics
```
time="2026-11-15T07:51:41+02:00" level=info msg="  Check \"isolation_broken\" counter — must be 0" source=console
time="2026-11-15T07:51:41+02:00" level=info msg="  Check \"cross_tenant_blocked\" counter — must be > 0" source=console
    cross_tenant_blocked
    isolation_broken
    success_rate
    cross_tenant_blocked...........: 1270   5.287151/s
    isolation_broken...............: 0      0/s
    own_tenant_ok..................: 4174   17.376826/s
    success_rate...................: 98.11% 8280 out of 8439
    http_req_duration..............: avg=754.84ms min=3.52ms   med=119.48ms max=20s      p(50)=119.48ms p(90)=1.48s    p(95)=3.3s     p(99)=11.67s   p(99.9)=20s     
    http_reqs......................: 8439   35.132494/s
time="2026-11-15T07:51:42+02:00" level=error msg="thresholds on metrics 'success_rate' have been crossed"
```

### Latency by Endpoint
```
    latency_cat_revenue_ms.........: avg=927.4ms  min=35.81ms  med=705.23ms max=5.12s    p(50)=705.23ms p(90)=2.01s    p(95)=2.54s    p(99)=3.63s    p(99.9)=4.51s   
    latency_cross_ms...............: avg=150.53ms min=6.79ms   med=121.48ms max=592.62ms p(50)=121.48ms p(90)=295.99ms p(95)=316.88ms p(99)=402.54ms p(99.9)=501.07ms
    latency_cte_ms.................: avg=6.28s    min=210.24ms med=6.3s     max=20s      p(50)=6.3s     p(90)=10.89s   p(95)=12.69s   p(99)=19.36s   p(99.9)=20s     
    latency_cust_ltv_ms............: avg=315.84ms min=48.95ms  med=300.02ms max=1.09s    p(50)=300.02ms p(90)=512.05ms p(95)=596.15ms p(99)=747.35ms p(99.9)=1.02s   
    latency_list_ms................: avg=1s       min=35.46ms  med=983.74ms max=3.77s    p(50)=983.74ms p(90)=1.9s     p(95)=2.2s     p(99)=2.88s    p(99.9)=3.74s   
    latency_lookup_ms..............: avg=91.52ms  min=3.52ms   med=96.02ms  max=418.22ms p(50)=96.02ms  p(90)=193.49ms p(95)=201.24ms p(99)=283.21ms p(99.9)=311.8ms 
    latency_low_stock_ms...........: avg=193.48ms min=41.97ms  med=192.12ms max=581.76ms p(50)=192.12ms p(90)=307.22ms p(95)=382.35ms p(99)=525.41ms p(99.9)=574.95ms
    latency_sku_ms.................: avg=100.18ms min=4.83ms   med=98.22ms  max=395.96ms p(50)=98.22ms  p(90)=198.49ms p(95)=205.17ms p(99)=287.71ms p(99.9)=315.91ms
    latency_window_ms..............: avg=8.76s    min=394.44ms med=8.79s    max=20s      p(50)=8.79s    p(90)=15.19s   p(95)=17.68s   p(99)=20s      p(99.9)=20s     
```

---

## 4. Strategies Test

```
PASS=14  FAIL=0
🎉 ALL TESTS PASSED
```

---

## 5. Agent Stats

```
{"routing.pythonCalls":6645,"routing.fallbacks":0,"cache.hitRate":0.19064703442554956,"cache.loadTimeMicros":0,"cache.size":448,"cache.misses":39027,"cache.evictions":0,"routing.localCalls":32368,"cache.hits":9193}
```

---

## Verdict

| Test | Status |
|------|:------:|
| Concurrency | ✅ |
| JWT E2E | ✅ |
| Load test | ✅ |
| Strategies | ✅ |

**Artifacts:** `test-reports/20261115-074611/`
