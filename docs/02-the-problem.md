# The Problem: Silent Leaks

> **In a multi-tenant SaaS, cross-tenant data leaks typically happen between application code and database execution.**

---

## The Silent Bug

**Developer writes:**

```java
List<Order> orders = orderRepo.findByStatus("PENDING");
```

**Hibernate generates:**

```sql
SELECT * FROM orders WHERE status = 'PENDING';

-- 💥 This returns orders from EVERY tenant.
```

The developer forgot `AND tenant_id = ?`. In code review this looks like a bug. In production it becomes:

> [!CAUTION]
> **🚨 Compliance Violation** — GDPR, HIPAA, SOC2 audits fail immediately.

> [!CAUTION]
> **💔 Contract Breach** — B2B SLAs guarantee tenant data isolation.

> [!CAUTION]
> **📉 Reputation Damage** — Trust lost permanently. Customers don't come back.

---

## Why Existing Solutions Fall Short

### 1. Native Row-Level Security (RLS / VPD)

- Requires deep DBA involvement
- DB-specific rules per table
- Schema rewrites for every new tenant column
- **Application code changes** to set session context

### 2. ORM Multi-Tenancy (Hibernate)

- Annotate every entity with `@TenantId`
- Custom `MultiTenantConnectionProvider`
- **Only covers ORM-tracked queries** — native SQL slips through

### 3. Sharding Middleware (ShardingSphere, Vitess)

- Heavy infrastructure (proxy layer)
- Rewrite rules at proxy layer
- **Not designed for row-level tenant isolation**

---

## The Common Thread

> [!WARNING]
> Every existing approach forces a **trade-off between safety, coverage, and complexity**. TenantWall eliminates that trade-off.

---

## Comparison

| Solution | Safety | Coverage | Complexity |
|---|---|---|---|
| Native RLS | ✅ High | ⚠️ DB-specific | ❌ High (DBA) |
| Hibernate MT | ⚠️ ORM only | ⚠️ Partial | ⚠️ Medium |
| Sharding | ❌ Wrong layer | ❌ Sharding only | ❌ High |
| **TenantWall** | **✅ High** | **✅ Universal** | **✅ Zero code** |

---

## Real-World Impact

A single forgotten `WHERE` clause can expose:

- **Customer PII** — names, addresses, payment methods
- **Financial records** — invoices, transactions, balances
- **Business intelligence** — revenue data, growth metrics
- **Support tickets** — private conversations between tenants

**In a shared-database SaaS, this is one line of code away — every single day.**

---

## The Cost of Getting It Wrong

| Incident Type | Typical Cost |
|---|---|
| Data breach disclosure | $50K–$500K (legal, notification) |
| Regulatory fine (GDPR) | Up to 4% of annual revenue |
| Customer churn | 10–30% after a disclosed breach |
| Reputation damage | Incalculable |

**One bug. One deployment. One invoice from a lawyer.**

---

## The Shift in Thinking

Traditional approach: **"Let the developer remember to add `WHERE tenant_id`."**

TenantWall approach: **"The infrastructure guarantees it."**

> [!TIP]
> Data isolation is **infrastructure's responsibility**, not the developer's. The JDBC layer is the universal chokepoint — every query passes through it.

---

**Next:** [The Solution →](./03-the-solution.md)