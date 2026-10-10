package com.tenantwall.agent;

import java.util.regex.Pattern;

/**
 * Analyzes SQL queries to decide which engine should process them.
 * 
 * Complex queries (with CTEs, Window Functions, Oracle-specific functions)
 * go to the Python service. Simple queries stay local for speed.
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
}