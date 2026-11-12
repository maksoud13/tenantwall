# Roadmap

> **Philosophy:** Adoption > Features. Security > Novelty. Users > Assumptions.
>
> **The one metric that matters:** *Time from `mvn install` to first successfully isolated request.* Target: **< 10 minutes**.

---

## The North Star

**"The zero-code tenant isolation layer for Spring Boot JDBC."**

Success means a solo developer can:

1. Add one Maven dependency
2. Add one `-javaagent` flag
3. Add one property file
4. Have working multi-tenancy in **under 10 minutes** — with zero code changes, zero SQL leaks, and < 5% latency overhead.
Every item on this roadmap serves that goal.

---

## ✅ Shipped

### v1.0 — Core Engine (2026-Q3)

The foundation. Every claim below is backed by an integration test.

| Category | Feature |
| ---------- | --------- |
| **Interception** | Java agent (ByteBuddy) with 3-layer coverage |
| **Hibernate** | `StatementPreparerImpl.prepareQueryStatement` interceptor |
| **JDBC** | `Statement.executeQuery` + `Connection.prepareStatement` fallbacks |
| **Complex SQL** | CTE / window function support (Python `sqlglot` sidecar) |
| **Fallback** | Fail-open to local JSqlParser when Python is unavailable |
| **Cache** | Caffeine query cache (10K entries, 30 min TTL) |
| **Config** | Properties / env vars / system props / agent args |
| **Verified** | MySQL 8.0, SQL Server 2022, Oracle XE 21c |
| **Tests** | **88/88 integration tests passing** |

**What was hard:** Discovering that HikariCP's Javassist proxy breaks ByteBuddy's `@Advice.AllArguments(readOnly=false)`. The fix: intercept at the Hibernate SPI layer, not the JDBC driver layer. Full story in [Engineering Journey](./08-engineering-journey.md).

---

### v1.1 — Zero-Code + 5 Strategies (2026-Q4)

The productization step. This is where TenantWall becomes a **product**, not just a library.

| Category | Feature |
| ---------- | --------- |
| **Zero-code** | Spring Boot Starter (auto-config, no user filter class) |
| **Strategies** | 5 tenant resolvers: `header`, `fixed`, `session`, `cookie`, `jwt` |
| **Validation** | Fail-fast on bad strategy, missing sub-config |
| **Safety** | Conflict warning when >1 strategy key is set |
| **Testing** | `test-strategies.sh` — **14/14 passing** |
| **Load test** | Sequential 5-strategy load test, **0 leaks in 3529 requests** |

**What was hard:** Designing a strategy system that is (a) config-driven, (b) validates at startup, (c) auto-wires without user code, and (d) never silently misconfigures.

**What this enables:** A developer who has never heard of TenantWall can go from `mvn dependency` to isolated requests in 2 minutes. No filter code. No annotations. No boilerplate.

---

## 🛑 Tier 0 — Stop Doing (Protect Focus)

Before adding anything, **remove** distractions.

| # | Action | Reason |
| --- | -------- | -------- |
| 1 | **Pause Backup/Restore module** | 20 features × 2 months solo dev — that's a separate product. Revisit at 50+ users. |
| 2 | **Freeze Catalog Phase 2+** | Phase 1 (Shared DB + Admin DB) is enough. Wait for multi-DS user demand. |
| 3 | **Stop adding features to the roadmap** | 40 backlog items with no users is a distraction. Ship visibility first. |
| 4 | **Stop building in private** | Every week without Maven Central is a week of lost adoption. |

**Rule:** No new feature starts until Tier 1 is complete.

---

## 🔴 Tier 1 — Security + Publishing (Weeks 1–2)

> **Goal:** Ship an artifact that is mathematically safe to use, publicly available, and doesn't crash production.

### Week 1 — Security & Reliability

#### Security Hardening (Days 1–2)

| # | Task | Why |
| --- | ------ | ----- |
| 5 | **JWT agent ↔ Python parser** (HMAC-SHA256, 60s TTL, nonce) | Parser on `:8000` exposes raw SQL — critical leak |
| 6 | **Bind parser to `127.0.0.1`** | Same reason. Never bind `0.0.0.0` |
| 7 | **Error sanitization** | Parser errors currently leak SQL fragments |
| 8 | **Rate limiting** on parser | DoS protection |
| 9 | **Secret rotation** support | Future-proofing for multi-env deploys |

**Deliverables:**

- `JwtSigner.java` (agent side)
- `@require_jwt` decorator (Python side)
- Config: `tenantwall.parser.secret`, `tenantwall.parser.token.ttlSeconds`
- Integration test: unauthorized request → 401

---

#### Reliability (Days 3–5)

| # | Task | Why |
| --- | ------ | ----- |
| 10 | **Migration bypass** (Flyway/Liquibase/hbm2ddl) | If agent tries to filter `CREATE TABLE`, init breaks |
| 11 | **Concurrency test** (1,000 parallel requests) | ThreadLocal leaks are the #1 production failure mode |
| 12 | **ThreadLocal leak test** | Prove `TenantContext.clear()` runs in all paths |

**The concurrency test (the most important test in the roadmap):**

```java
@Test
void concurrentTenantsDoNotLeak() throws Exception {
    var pool = Executors.newFixedThreadPool(50);
    var futures = new ArrayList<Future<Void>>();
    for (int i = 0; i < 1000; i++) {
        final String tenant = (i % 2 == 0) ? "TENANT_A" : "TENANT_B";
        futures.add(pool.submit(() -> {
            TenantContext.setTenant(tenant);
            try {
                var results = repo.findAll();
                for (var row : results) {
                    assertEquals(tenant, row.getTenantId(),
                        "LEAK: " + tenant + " saw " + row.getTenantId());
                }
            } finally {
                TenantContext.clear();
            }
            return null;
        }));
    }
    for (var f : futures) f.get();
    pool.shutdown();
}
```

**Success:** 0 assertions failed, 0 leaks.

---

### Week 2 — Publishing

| # | Task | Why |
| --- | ------ | ----- |
| 13 | **SLF4J migration** (replace `System.err`) | Every enterprise eval rejects on this |
| 14 | **Maven Central setup** (`io.github.tenantwall` groupId) | 90% of devs won't `git clone` |
| 15 | **Publish `agent-module`** | The core artifact |
| 16 | **Publish `tenantwall-spring-boot-starter`** | The zero-code entry point |
| 17 | **GPG signing + javadoc + sources** | Maven Central requirements |
| 18 | **Verification:** fresh project `mvn dependency` works | The only test that matters |

#### Maven Central checklist

```xml
<groupId>io.github.tenantwall</groupId>
<artifactId>agent-module</artifactId>
<version>1.1.0</version>
 
<!-- Required for Maven Central -->
<licenses>...</licenses>
<developers>...</developers>
<scm>...</scm>
 
<!-- Signing -->
<maven-gpg-plugin>...</maven-gpg-plugin>
<maven-source-plugin>...</maven-source-plugin>
<maven-javadoc-plugin>...</maven-javadoc-plugin>
<nexus-staging-maven-plugin>...</nexus-staging-maven-plugin>
```

**Success criteria:**

- `mvn dependency:get -Dartifact=io.github.tenantwall:tenantwall-spring-boot-starter:1.1.0` succeeds in a clean environment
- Both artifacts appear on [search.maven.org](https://search.maven.org)

---

### Week 2 (bonus) — Quick Wins

| # | Task | Time |
| --- | ------ | ------ |
| 19 | `.devcontainer` — Java 21 + Maven + Docker | 4h |
| 20 | Codespaces button in README | 1h |
| 21 | `benchmark/` folder — k6 script (baseline vs agent) | 4h |
| 22 | `docker-compose up` for Python sidecar | 4h |
| 23 | README: "When NOT to use" section | 2h |

**Why "When NOT to use" builds trust:** Counter-intuitively, stating "Don't use this if you have 2 tenants (use Hibernate native)" signals maturity. It shows you're solving a real problem, not overselling.

---

### Tier 1 — Definition of Done

- [ ] Unauthorized parser request → 401
- [ ] 1,000 concurrent requests → 0 leaks
- [ ] Flyway migration runs with `onMissing=fail` set
- [ ] `mvn dependency` works in a fresh project
- [ ] Both artifacts on Maven Central
- [ ] Codespaces → 2 min to `test-strategies.sh`
- [ ] README has "When NOT to use"
**After Tier 1:** The tool is secure, published, and testable. It's ready to be discovered.

---

## 🟡 Tier 2 — Developer Experience (Weeks 3–4)

> **Goal:** Remove every friction point between "hearing about TenantWall" and "seeing it work."

| # | Task | Impact |
| --- | ------ | -------- |
| 24 | **Codespaces polish** — one-click full demo | Removes local setup friction |
| 25 | **YouTube demo** (3 min) — before/after SQL, live test | Required for HN post |
| 26 | **Docs restructure** — getting started, config reference, FAQ | Self-serve onboarding |
| 27 | **PostgreSQL Testcontainers test** | Start of largest-market DB |
| 28 | **PostgreSQL docs page** | "Verified" status |

**Why Postgres now, not later:** PostgreSQL is the #1 database for SaaS startups. Without it, TenantWall is locked out of the fastest-growing segment. Adding it in Tier 2 (not Tier 4) makes it a "verified" DB before visibility campaigns.

---

## 🟢 Tier 3 — Visibility (Weeks 5–6)

> **Goal:** Get the first 100 developers to know TenantWall exists.

| Day | Channel | Notes |
| ----- | --------- | ------- |
| Tue 9am ET | **Show HN** | Lead with SQL diff + YouTube link |
| +1 day | **r/java** | Lead with code, not architecture |
| +2 days | **r/springboot** | Same |
| +3 days | **awesome-java PR** | Permanent SEO |
| +3 days | **GitHub Discussions** | Questions live here, not Issues |
| +4 days | **5 Stack Overflow answers** | Spring Boot multi-tenancy questions |
| +5 days | **DM Dan Vega** | 3-line pitch, no long email |

**HN post template:**

> **Show HN: TenantWall — Zero-code multi-tenant isolation for Spring Boot**
>
> I got tired of adding `AND tenant_id = ?` to every repository method. So I built a Java agent that rewrites SQL at the JDBC layer.
>
> **Before:** `SELECT * FROM orders WHERE status = 'PENDING'` (returns every tenant's data)
> **After:** `SELECT * FROM orders WHERE status = 'PENDING' AND tenant_id = 'ACME'` (returns only ACME's data)
>
> Your application code doesn't change. 88 integration tests across MySQL, SQL Server, Oracle. 5 tenant strategies. Works with any ORM.
>
> Demo: [YouTube 3 min] | Docs: [link] | GitHub: [link]

**Success criteria:**

- 500+ GitHub stars
- 100+ clones in first week
- 10+ real issues (not "cool project" comments)
- 1 external contributor

---

## 🔵 Tier 4 — Credibility + PostgreSQL (Month 2)

> **Goal:** Shift from "interesting project" to "tool I'd use in production."

| # | Task | Impact |
| --- | ------ | -------- |
| 29 | **Baeldung article** — "Multi-Tenant Data Isolation in Spring Boot with TenantWall" | Highest ROI — permanent Google ranking |
| 30 | **"vs Hibernate `@Filter`" comparison page** | Answers 70% of objections |
| 31 | **Cross-post** to Dev.to + Hashnode | 3× reach, 10 min effort |
| 32 | **PostgreSQL official support** | Largest market gap |
| 33 | **MyBatis interceptor adapter** | Second-biggest Java ORM audience |
| 34 | **Bug fixes from early adopters** | Credibility from responsiveness |

**Why Baeldung before ProductHunt:** A Baeldung article ranks on Google permanently. A ProductHunt launch is a 24-hour spike. Baeldung drives sustained traffic for years.

**Success criteria:**

- Baeldung article published
- PostgreSQL documented + tested (test suite green)
- 5+ production users (private confirmation)
- 10+ MyBatis users in Discussions

---

## 🟣 Tier 5 — Growth (Month 3+)

> **Goal:** Transition from "solo dev project" to "community-driven standard."

| # | Task |
| --- | ------ |
| 35 | **ProductHunt launch** (after PostgreSQL) |
| 36 | **"Good First Issue" labels** (2–3 well-scoped issues) |
| 37 | **Technical deep-dive article** — "ByteBuddy + AST: 3 Hardest Bugs" |
| 38 | **DataSource Catalog Phase 1** (if user demand exists) |
| 39 | **Performance:** indexes + Page→Slice + `@Cacheable` |
| 40 | **PDF case study** with real load-test numbers |

**ProductHunt prep (when Postgres is done):**

- MySQL + SQL Server + Oracle + PostgreSQL = 4 databases
- 102/102 tests + load-test verified
- Baeldung published
- 500+ GitHub stars
- 3+ production users
Only launch when the story is **"verified across 4 databases with real users"** — not before.

---

## ⏸️ Tier 6 — Deferred (v2.0+ — 6+ months)

These features are valid ideas but belong in a different era of the product.

| Feature | Why deferred | Revival condition |
| --------- | -------------- | ------------------- |
| **Backup/Restore module** | 20 features, 2 months, separate product area | 50+ users asking |
| **DataSource Catalog Phase 2+** | Per-factory context requires multi-DS users to design correctly | 10+ users with 2+ DataSources |
| **Async context propagation** | `TransmittableThreadLocal` adds dependency complexity | 5+ async users |
| **HTML admin UI** | Scope creep — belongs in a different product | 20+ users want it |
| **Oracle RAC + SQL Server AG** | Enterprise-only; no current enterprise users | First enterprise deal |
| **GraalVM native image** | Reflection config is complex; not a priority for early users | 5+ users requesting native binaries |
| **jOOQ adapter** | MyBatis is bigger audience | After MyBatis |
| **Schema-per-tenant mode** | Changes the isolation model entirely | Enterprise customers only |

**Rule:** No deferred feature starts until Tier 3 success criteria are met.

---

## 📊 Success Metrics (6 months)

| Metric | Conservative | Target | Stretch |
| -------- | -------------- | -------- | --------- |
| Maven Central downloads | 100 | 1,000 | 10,000 |
| GitHub stars | 200 | 500 | 2,000 |
| Production users | 3 | 10 | 50 |
| External contributors | 0 | 3 | 10 |
| Blog/Baeldung traffic | 1K | 10K | 100K |
| GitHub issues (real) | 5 | 20 | 100 |

**How to measure:**

- **Maven Central:** mvnrepository.com stats
- **Stars/clones:** GitHub Insights
- **Production users:** private confirmations + case studies
- **Traffic:** Google Search Console + Baeldung referral logs

---

## 🎯 Milestone Timeline

```text
Week 1     🔴 Security hardening (JWT, concurrency, migration)
Week 2     🔴 Maven Central + SLF4J + Codespaces
Week 3-4   🟡 DevEx (YouTube, docs, Postgres)
Week 5-6   🟢 Visibility (HN, Reddit, awesome-java, SO)
Month 2    🔵 Credibility (Baeldung, MyBatis, Postgres official)
Month 3    🟣 Growth (ProductHunt, contributors, catalog Phase 1)
Month 6+   ⏸️ Deferred (backup, catalog Phase 2, native image)
```

---

## Roadmap Principles

### 1. Adoption > Features

*Ship what users can try, not what we think is cool.*

Every feature must have a real user requesting it. Backup module = 20 features, 0 users → deferred. Maven Central = 0 features, unblocks all users → Tier 1.

### 2. Security > Novelty

*One data leak destroys the product's reputation.*

JWT parser auth comes before any visibility campaign. Concurrency tests come before publication. Security is not a "phase" — it's a prerequisite.

### 3. Developers > Enterprise

*If a solo dev can't try it in 2 minutes, it doesn't exist.*

Maven Central, Codespaces, `docker-compose up` — all before enterprise features. The solo dev is the entry point; enterprise comes later.

### 4. Focus > Breadth

*Do one thing so well the world can't ignore it.*

JDBC isolation, done perfectly. Not "full DB management." Not "backup tool." Not "migration platform." One thing.

### 5. Community > Solo

*Solo dev builds a tool. Community builds an industry.*

Good First Issues, public roadmap, contributor highlights. The bus factor of 1 is the biggest risk — mitigate it with community.

### 6. Verified > Claimed

*Test everything before shipping.*

If we can't test it, we don't claim it works. The Support Matrix exists because we don't oversell. Every row is either tested or explicitly marked.

---

## Feature Request Process

Have an idea? Open a [GitHub issue](https://github.com/maksoud13/tenantwall/issues) with:

| Field | Content |
| ------- | --------- |
| **Use case** | What are you trying to do? |
| **Current workaround** | How are you solving it today? |
| **Proposed solution** | What would you like TenantWall to do? |
| **Why this, not X** | What's wrong with the alternative? |

**We prioritize by:**

1. Impact on preventing data leaks — highest priority
2. Universal applicability — not framework-specific
3. Implementation complexity — lower is better
4. Number of users requesting — real demand, not hypothetical
**We decline:**

- Features that break the "zero code changes" promise
- Features that require annotating entities
- Features that lock users into a specific ORM or database
- Features that add scope beyond tenant isolation

---

## Versioning

TenantWall follows [Semantic Versioning](https://semver.org):

| Change | Version bump |
| -------- | -------------- |
| Breaking changes | Major (v2.0) |
| New features, backward compatible | Minor (v1.2) |
| Bug fixes only | Patch (v1.1.1) |

**Current version:** 1.1.0

**Backward compatibility promise:** Config keys, public APIs, and behavior remain stable across all v1.x releases. No breaking changes in minor versions.

---

## What We Will NOT Build

| ❌ | Why |
| ---- | ----- |
| UI dashboard (before 100 users) | Scope creep — belongs in a different product |
| Backup/restore (before 50 users) | Separate product category |
| Non-JVM language support | Out of scope |
| Managed SaaS (before self-hosted adoption) | Reverse priority |
| Closed source | Community > revenue |
| Feature-per-request without validation | Loses focus |

---

## Open Challenges (Community Welcome)

These are unsolved problems where contributions are valuable:

| Challenge | Difficulty | Notes |
| ----------- | ------------ | ------- |
| Hibernate 5.x support | Medium | API differs from 6.x |
| Stored procedure internals | Hard | Requires SP body parsing |
| Schema-per-tenant mode | Hard | Changes isolation model |
| GraalVM native image | Advanced | Reflection config |
| jOOQ / QueryDSL adapters | Medium | Follow Hibernate pattern |
| R2DBC reactive support | Hard | Different interception layer |

See [Engineering Journey → Open Challenges](./08-engineering-journey.md) for details.

---

## How to Contribute

We welcome contributions in these areas:

| Area | Difficulty | Time to PR |
| ------ | ------------ | ------------ |
| New database integration (SQLite, Snowflake) | Easy | 1–2 days |
| Test suite for existing databases | Easy | 1 day |
| Documentation improvements | Easy | 1 hour |
| MyBatis adapter | Medium | 1 week |
| jOOQ adapter | Medium | 1 week |
| GraalVM native image | Advanced | 2–4 weeks |

**Contribution process:**

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Add tests (follow `examples/` pattern)
4. Open a PR with test results and a clear description

---

## The Roadmap in One Sentence

> **Prove it's safe (Tier 1), make it easy (Tier 2), make it visible (Tier 3), make it credible (Tier 4), grow it with community (Tier 5) — and defer everything else until users ask for it.**

---

**Next:** [FAQ →](./12-faq.md)
