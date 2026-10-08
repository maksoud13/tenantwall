# TenantWall

Stop data leaks in multi-tenant applications. Zero code changes.

[![Live Demo](https://img.shields.io/badge/Live%20Demo-Try%20Now-blue)](https://maksoud13.github.io/datashield-demo/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## The Problem

Multi-tenant SaaS applications share one database. Every table has a `tenant_id` column. Every query must filter by it. Developers forget. One missing filter = data leak between customers.

## The Solution

TenantWall is a Java Agent + Python Service that injects tenant filters into SQL queries automatically at the JDBC level.

```sql
-- You write:
SELECT * FROM orders WHERE total > 100;

-- TenantWall changes it to:
SELECT * FROM orders WHERE (total > 100) AND tenant_id = 'TENANT_A';
No code changes. No new services. No extra latency.

Features
Simple SELECT, WHERE, JOIN

Subqueries (IN, EXISTS, NOT EXISTS)

UNION / INTERSECT / EXCEPT

CTEs (WITH clauses)

Window Functions

OR Leak Protection

Caffeine Cache (93%+ hit rate)

10+ SQL dialects

Excluded tables configuration

Zero code changes

Architecture
text
App → Java Agent → [Simple?] → JSqlParser (Local)
                 → [Complex?] → Python Service (sqlglot)
                 → [Cached?] → Caffeine
       ↓
    Database
Live Demo
Try it: https://maksoud13.github.io/datashield-demo/

Project Structure
text
tenantwall/
├── java-agent/        # Java Agent (ByteBuddy + JSqlParser)
├── python-parser/     # Python Service (FastAPI + sqlglot)
├── demo/              # Frontend Demo
└── docs/              # Documentation
Quick Start
Java Agent
bash
cd java-agent
mvn clean install
Python Parser
bash
cd python-parser
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
Roadmap
☑ Java Agent with ByteBuddy
☑ SELECT query support (all types)
☑ OR leak protection
☑ Caffeine cache
☑ Python Parser (CTEs, Window Functions)