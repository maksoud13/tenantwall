#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
#  Concurrency Isolation Test (JUnit IT in megaexample)
#
#  ⚠️  NOTE: MSYS_NO_PATHCONV is NOT set here.
#  mvn bash wrapper needs Unix paths to be auto-converted to
#  Windows paths when invoking java.exe. Setting MSYS_NO_PATHCONV=1
#  breaks this (java sees "/c/Java/..." instead of "C:\Java\...").
# ═══════════════════════════════════════════════════════════════
set -u

cd "$(dirname "$0")"

# ─── Find mvn (don't override MAVEN_HOME - let mvn find itself) ───
MVN_BIN="$(command -v mvn 2>/dev/null || echo '/c/Java/apache-maven-3.9.5-bin/maven/bin/mvn')"

if [ ! -x "$MVN_BIN" ]; then
    echo "❌ Maven not found at: $MVN_BIN"
    echo "   Set correct path in this script or install Maven."
    exit 1
fi

# ─── Check agent jar exists ───
AGENT_JAR_UNIX="$(cygpath -u "$HOME")/.m2/repository/com/tenantwall/agent-module/1.0.0-SNAPSHOT/agent-module-1.0.0-SNAPSHOT.jar"
if [ ! -f "$AGENT_JAR_UNIX" ]; then
    echo "❌ Agent JAR not found: $AGENT_JAR_UNIX"
    echo "   Run: cd .. && mvn install -pl agent-module -DskipTests"
    exit 1
fi

echo "════════════════════════════════════════════════════════════"
echo "  Concurrency Isolation Test — 1000 parallel requests"
echo "  Maven: $MVN_BIN"
echo "════════════════════════════════════════════════════════════"
echo ""

# ─── Run the IT ───
"$MVN_BIN" verify -Dit.test=ConcurrencyIsolationIT -DskipITs=false 2>&1 | tee /tmp/concurrency-output.log
MVN_EXIT=${PIPESTATUS[0]}   # ← tee's exit code, not mvn's!

echo ""
echo "════════════════════════════════════════════════════════════"
echo "  Summary"
echo "════════════════════════════════════════════════════════════"

grep -E "Requests:|Threads:|Completed:|Rows read:|Leaks:|Errors:|Duration:|Tests run:|BUILD" /tmp/concurrency-output.log || {
    echo "  (no concurrency metrics found — see /tmp/concurrency-output.log)"
}

echo ""
if [ $MVN_EXIT -ne 0 ]; then
    echo "❌ Concurrency test FAILED (exit $MVN_EXIT)"
    exit 1
fi

echo "✅ Concurrency test PASSED"
exit 0