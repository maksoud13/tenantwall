package com.tenantwall.agent;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Analyzes SQL queries to decide which engine should process them.
 *
 * <p>Complex queries (with CTEs, Window Functions, Oracle-specific functions)
 * go to the Python service. Simple queries stay local for speed.
 *
 * <p>Also classifies DDL (CREATE/ALTER/DROP/...) so migrations are never
 * rewritten — a rewritten CREATE TABLE would be invalid SQL.
 */
public class QueryAnalyzer {

    // Patterns that indicate a query is too complex for JSqlParser
    private static final Pattern[] COMPLEX_PATTERNS = {
        // CTEs (WITH clauses)
        Pattern.compile("\\bWITH\\b", Pattern.CASE_INSENSITIVE),

        // Window functions
        Pattern.compile("\\bOVER\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bPARTITION\\s+BY\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bROW_NUMBER\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bRANK\\s*\\(", Pattern.CASE_INSENSITIVE),

        // Oracle-specific functions
        Pattern.compile("\\bNVL\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bDECODE\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bCONNECT\\s+BY\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bROWNUM\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bSTART\\s+WITH\\b", Pattern.CASE_INSENSITIVE),
    };

    // DDL keywords — first word after stripping comments
    private static final Set<String> DDL_KEYWORDS = Set.of(
            "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME"
    );

    /**
     * Returns true if the query needs the Python service.
     */
    public static boolean isComplex(String sql) {
        if (sql == null || sql.isEmpty()) {
            return false;
        }

        for (Pattern pattern : COMPLEX_PATTERNS) {
            if (pattern.matcher(sql).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the SQL is a DDL statement that must not be rewritten.
     *
     * <p>Uses string-based detection (no regex):
     * <ol>
     *   <li>Strip leading {@code --} line comments and {@code /* *&#47;} block comments</li>
     *   <li>Read the first word (letters only)</li>
     *   <li>Uppercase and compare to DDL keywords</li>
     * </ol>
     *
     * <p>Examples returning true:
     * <ul>
     *   <li>{@code CREATE TABLE products (...)}</li>
     *   <li>{@code ALTER TABLE products ADD COLUMN ...}</li>
     *   <li>{@code -- migration 001\nCREATE TABLE ...}</li>
     *   <li>{@code /* v2 *&#47; ALTER TABLE ...}</li>
     * </ul>
     *
     * <p>Examples returning false (no false positives):
     * <ul>
     *   <li>{@code SELECT created_at FROM products} (column name)</li>
     *   <li>{@code SELECT * FROM drop_rate} (table name)</li>
     * </ul>
     */
    public static boolean isDdl(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }
        String stripped = stripLeadingComments(sql);
        String firstWord = firstWord(stripped);
        return DDL_KEYWORDS.contains(firstWord);
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers (package-private for tests)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Strips leading whitespace + SQL comments (line + block) from the SQL.
     * Returns empty string if the SQL is only comments.
     */
    static String stripLeadingComments(String sql) {
        String s = sql;
        while (true) {
            s = s.stripLeading();
            if (s.isEmpty()) {
                return "";
            }
            if (s.startsWith("--")) {
                // Line comment — skip to newline
                int nl = s.indexOf('\n');
                if (nl < 0) {
                    return ""; // comment runs to end of input
                }
                s = s.substring(nl + 1);
            } else if (s.startsWith("/*")) {
                // Block comment — skip to closing */
                int end = s.indexOf("*/");
                if (end < 0) {
                    return ""; // unterminated block comment
                }
                s = s.substring(end + 2);
            } else {
                return s;
            }
        }
    }

    /**
     * Returns the first sequence of letters in the SQL, uppercased.
     * Skips leading non-letters (whitespace already stripped by caller).
     * Returns empty string if no letters found.
     */
    static String firstWord(String sql) {
        int i = 0;
        int len = sql.length();
        while (i < len && !Character.isLetter(sql.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < len && Character.isLetter(sql.charAt(i))) {
            i++;
        }
        if (i == start) {
            return "";
        }
        return sql.substring(start, i).toUpperCase();
    }
}