# Why TenantWall Uses a Python (sqlglot) Sidecar for Complex SQL

> **Architecture Decision Record (ADR):** Why we chose an HTTP-based Python Sidecar (FastAPI + sqlglot) over in-process solutions (GraalPy, JEP) or a pure Java approach for complex SQL parsing.

---

## The Problem: Java Parsers Hit a Wall

TenantWall relies on `JSqlParser` for local, fast SQL rewriting. It handles simple `SELECT`, `INSERT`, `UPDATE`, `DELETE`, and basic `JOINs` perfectly.

However, enterprise applications often use **Complex SQL**:
- Common Table Expressions (CTEs / `WITH` clauses)
- Window Functions (`OVER`, `PARTITION BY`, `ROW_NUMBER`)
- Recursive subqueries
- Vendor-specific functions (Oracle `NVL`, `CONNECT BY`, etc.)

`JSqlParser` struggles with these edge cases. Building our own Java AST parser for 10+ dialects would take years. Therefore, we needed an engine with **maximum fidelity** for complex SQL dialects. We chose **`sqlglot` (Python)**.

But how do we integrate Python into a pure Java/Spring Boot agent? We evaluated four approaches.

---

## The Extended Performance Comparison

*Test setup: Simple `SELECT * FROM t WHERE id=1` over loopback/Docker-network HTTP, with keep-alive connection pool.*

| Approach | Cold Start | Per-Call Latency | Throughput / Core | Verdict |
|---|---|---|---|---|
| **Naive `python3 -c` per call** | 30 ms | 200–400 ms | 3–5 req/s | ❌ Unusable |
| **FastAPI + WebClient (Our Choice)** | ~1.5 s (container) / ~0 ms (warm) | **~2–8 ms** | **~100–300 req/s** | ✅ **Chosen** |
| Stdout sidecar | 300 ms | 1–5 ms | 200–800 req/s | ❌ No isolation, complex ops |
| GraalPy | 2–5 s per JVM | 3–15 ms | 60–300 req/s | ❌ 2–4× slower sqlglot, high memory |
| JEP (Java Embedded Python) | 200 ms | 1–5 ms | 200–800 req/s | ❌ ABI pain, hard deployment |

**The Verdict on HTTP Overhead:**
The HTTP hop costs **1–3 ms**. The actual `sqlglot` parse takes 1–5 ms for simple queries and 20–80 ms for complex ones. Therefore, the HTTP overhead is <5% on real queries. **Network latency is not the bottleneck; CPU is.**

---

## 🚀 The Critical Fix: Docker & FastAPI Workers

`sqlglot.transpile()` is a **CPU-bound** and synchronous operation. A single `uvicorn` worker running `async def` handlers will block the Python event loop while parsing.

If you run our Docker image with a single worker, you get exactly **1 request at a time**, regardless of how many CPU cores the server has.

**The Solution:** We must run multiple workers in Docker.

### Dockerfile Configuration

Our `Dockerfile` explicitly sets the number of workers to match the available CPU cores (e.g., 4):

```dockerfile
# Our Dockerfile runs uvicorn with multiple workers
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "4"]
```

Alternatively, for fine-grained control in Python, you can use `run_in_threadpool`:

```python
from fastapi.concurrency import run_in_threadpool

@app.post("/api/modify")
async def modify(req: ModifyRequest):
    return await run_in_threadpool(sqlglot.transpile, req.sql)
```

Why this matters: With `--workers N`, we get roughly N× throughput. Our default Docker image uses 4 workers, scaling the throughput to ~300 req/s per container.

---

## Where the Python Sidecar Clearly Wins

| Dimension | Why it beats in-process solutions (GraalPy/JEP) |
|---|---|
| sqlglot Fidelity | Full CPython, full feature set (optimizer, lineage, 10+ dialects). No "does this work on GraalPy?" testing. |
| Version Independence | Upgrade sqlglot with `pip install`, redeploy one container. No JVM rebuild, no GraalVM upgrade dance. |
| Shared Warmth | One FastAPI container serves N Spring Boot instances. GraalPy warms a 2–5 s context in every JVM. |
| Memory Efficiency | ~60–100 MB per Python container vs. +200–500 MB RSS to every JVM with GraalPy. |
| Blast Radius | A sqlglot crash/OOM kills one container, not your Spring Boot app. |
| Scaling | `docker compose scale sqlglot=4` — horizontal, independent of your JVM. |
| Testability | `curl` / `pytest` the Python service in isolation; no JNI debugging. |

---

## The Spring Boot Side — Non-Negotiables

Because we are using a microservice pattern (HTTP), a slow or dead FastAPI container must never hang a Spring Boot request thread. If we skip this, a FastAPI hiccup becomes a Spring Boot thread-pool exhaustion outage.

We enforce strict timeouts and circuit breakers.

```java
@Bean
public WebClient sqlglotClient(WebClient.Builder b) {
    var httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 500) // 500ms connect timeout
        .responseTimeout(Duration.ofSeconds(3))            // 3s max read timeout
        .doOnConnected(c -> c.addHandlerLast(new ReadTimeoutHandler(3)));

    return b.baseUrl("http://127.0.0.1:8000")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
}
```

> **Note:** In TenantWall's agent module, we also wrap these calls in a fallback mechanism. If the Python sidecar times out, the agent falls back to the local `JSqlParser`. This guarantees 100% uptime.

---

## Summary

The HTTP-based Python Sidecar is the right default for a Spring Boot + sqlglot integration.

- **Performance:** Within 5–15% of an in-process solution for real queries. (Network hop is noise compared to CPU parsing.)
- **Ops:** One extra standard container vs. GraalVM/JNI native-lib hell. Clear win.
- **Risk:** Entirely concentrated in timeouts + circuit breakers on the Spring Boot side, and worker concurrency in Docker. Both are well-understood, one-afternoon fixes.

**We ship it.**