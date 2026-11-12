#!/usr/bin/env bash
# Start the TenantWall parser. Defaults to 127.0.0.1 (localhost-only).
# For Docker/K8s: set TENANTWALL_PARSER_BIND_HOST=0.0.0.0 explicitly.
set -euo pipefail

HOST="${TENANTWALL_PARSER_BIND_HOST:-127.0.0.1}"
PORT="${TENANTWALL_PARSER_PORT:-8000}"

echo "═══════════════════════════════════════════════════════════"
echo "  TenantWall Parser"
echo "  Bind: ${HOST}:${PORT}"
if [ "${HOST}" = "0.0.0.0" ]; then
    echo "  ⚠️  WARNING: bound to 0.0.0.0 — exposed to the network."
    echo "     Ensure a firewall + reverse proxy with auth is in place."
fi
echo "═══════════════════════════════════════════════════════════"

exec uvicorn app.main:app --host "${HOST}" --port "${PORT}"