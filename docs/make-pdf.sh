#!/bin/bash
export MSYS_NO_PATHCONV=1

cd "$(dirname "${BASH_SOURCE[0]}")"

echo "═══════════════════════════════════════════════════════════"
echo "  TenantWall Documentation → PDF"
echo "═══════════════════════════════════════════════════════════"
echo ""

HTML="tenantwall-documentation.html"
PDF="tenantwall.pdf"

if [ ! -f "$HTML" ]; then
    echo "❌ Error: $HTML not found"
    exit 1
fi
echo "✓ HTML: $HTML"

# Find Chrome
BROWSER=""
for p in \
    "/c/Program Files/Google/Chrome/Application/chrome.exe" \
    "/c/Program Files (x86)/Google/Chrome/Application/chrome.exe" \
    "/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" \
    "/c/Program Files/Microsoft/Edge/Application/msedge.exe"
do
    if [ -f "$p" ]; then
        BROWSER="$p"
        break
    fi
done

if [ -z "$BROWSER" ]; then
    echo "❌ No Chrome/Edge found"
    exit 1
fi
echo "✓ Browser: $BROWSER"

# Convert path
WIN_PATH=$(cygpath -w "$(pwd)/$HTML" 2>/dev/null || echo "$(pwd)/$HTML")
WIN_PATH="${WIN_PATH//\\//}"
URI="file:///$WIN_PATH"

PDF_WIN=$(cygpath -w "$(pwd)/$PDF" 2>/dev/null || echo "$(pwd)/$PDF")

echo "✓ URI: $URI"
echo "✓ Output: $PDF"
echo ""

rm -f "$PDF"

echo "⏳ Converting..."
"$BROWSER" --headless --disable-gpu --no-pdf-header-footer \
    --print-to-pdf="$PDF_WIN" --print-to-pdf-no-header \
    --virtual-time-budget=10000 "$URI" 2>&1 | grep -v "DevTools" || true

if [ -f "$PDF" ]; then
    SIZE=$(stat -c%s "$PDF" 2>/dev/null || stat -f%z "$PDF")
    echo ""
    echo "✅ SUCCESS"
    echo "  File: $PDF"
    echo "  Size: $(awk "BEGIN{printf \"%.1f\", $SIZE/1024}") KB"
else
    echo ""
    echo "❌ FAILED — PDF not created"
    exit 1
fi
