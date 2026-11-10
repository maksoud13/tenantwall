#!/usr/bin/env bash
# توليد 5 properties files — واحد لكل strategy
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE="$ROOT/tenantwall.properties"
PODS_DIR="$ROOT/pods"

[[ -f "$BASE" ]] || { echo "❌ base missing: $BASE"; exit 1; }
mkdir -p "$PODS_DIR"

# احذف أي قسم "Tenant Resolution" قديم من الـ base عشان مانكررش
strip_tenant_section() {
    awk '
      /^# ═══ Tenant Resolution/ { skip=1; next }
      skip && /^# ═══/ && !/Tenant Resolution/ { skip=0 }
      skip && /^tenantwall\.tenant\./ { next }
      !skip { print }
    ' "$1"
}

emit() {
    local name="$1"; local port="$2"; local block="$3"
    local out="$PODS_DIR/$name.properties"
    strip_tenant_section "$BASE" > "$out"
    cat >> "$out" <<EOF

# ═══ POD: $name (port $port) ═══
tenantwall.tenant.strategy=$name
$block
EOF
    echo "✅ $out"
}

emit header  8081 "tenantwall.tenant.headerName=X-Tenant-Id"
emit fixed   8082 "tenantwall.tenant.fixedValue=TENANT_001"
emit session 8083 "tenantwall.tenant.sessionKey=TENANT_ID"
emit cookie  8084 "tenantwall.tenant.cookieName=tenant_id"
emit jwt     8085 "tenantwall.tenant.jwtClaim=tenant_id
tenantwall.tenant.jwtHeader=Authorization
tenantwall.tenant.jwtPrefix=Bearer "

echo
echo "🎉 5 pod properties generated in $PODS_DIR"