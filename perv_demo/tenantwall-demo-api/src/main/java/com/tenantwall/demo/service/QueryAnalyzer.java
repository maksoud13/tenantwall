package com.tenantwall.demo.service;

import java.util.regex.Pattern;

public class QueryAnalyzer {

    private static final Pattern[] COMPLEX_PATTERNS = {
        Pattern.compile("\\bWITH\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bOVER\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bPARTITION\\s+BY\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bROW_NUMBER\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bRANK\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bNVL\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bDECODE\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bCONNECT\\s+BY\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bROWNUM\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bSTART\\s+WITH\\b", Pattern.CASE_INSENSITIVE),
    };

    public static boolean isComplex(String sql) {
        if (sql == null || sql.isEmpty()) return false;
        for (Pattern p : COMPLEX_PATTERNS) {
            if (p.matcher(sql).find()) return true;
        }
        return false;
    }
}