# TenantWall Documentation

> **Zero-code multi-tenant data isolation for any JDBC database.**

This is the complete technical documentation for TenantWall. Every claim is either **verified by the shipped test suite** or **explicitly marked as supported/planned**. Nothing is speculative.

---

## Table of Contents

| # | Section | Description |
|---|---|---|
| 1 | [Executive Summary](./01-executive-summary.md) | What TenantWall is and why it matters |
| 2 | [The Problem](./02-the-problem.md) | Silent leaks in multi-tenant SaaS |
| 3 | [The Solution](./03-the-solution.md) | How zero-code interception works |
| 4 | [Architecture](./04-architecture.md) | Component diagram and query lifecycle |
| 5 | [Support Matrix](./05-support-matrix.md) | Verified, supported, and planned databases |
| 6 | [Security Model](./06-security-model.md) | What's guaranteed and what's out of scope |
| 7 | [Test Results](./07-test-results.md) | 88/88 tests across 3 databases |
| 8 | [Engineering Journey](./08-engineering-journey.md) | What didn't work and why |
| 9 | [Getting Started](./09-getting-started.md) | 5-minute integration guide |
| 10 | [Configuration](./10-configuration.md) | All config keys and sources |
| 11 | [Roadmap](./11-roadmap.md) | Shipped, in progress, planned |
| 12 | [FAQ](./12-faq.md) | Common questions |
| 13 | [License](./13-license.md) | MIT |

---

## Reading Order

**New to TenantWall?** Read in order: 1 → 2 → 3 → 4.

**Evaluating for production?** Start with [Security Model](./06-security-model.md) and [Test Results](./07-test-results.md).

**Ready to integrate?** Jump to [Getting Started](./09-getting-started.md).

**Curious about the engineering?** [Engineering Journey](./08-engineering-journey.md) documents the failed attempts and lessons learned.

---

## Version

- **Version:** 1.0.0
- **Last updated:** October 2026
- **Status:** 88/88 tests passing across 3 databases