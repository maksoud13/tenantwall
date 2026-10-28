package com.tenantwall.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client for the TenantWall Python Parser service.
 * 
 * Sends SQL queries to the Python service for complex parsing
 * (CTEs, Window Functions, etc.)
 */
public class PythonServiceClient {

    
    private static final String BASE_URL = TenantwallConfig.getParserUrl();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Sends SQL to the Python service for modification.
     *
     * @param sql     The original SQL query
     * @param column  Column to filter by (e.g., tenant_id)
     * @param value   Value to filter by
     * @param dialect SQL dialect (mysql, oracle, postgres, etc.)
     * @return The modified SQL
     * @throws PythonServiceException if the service is unreachable or fails
     */

    public static boolean isEnabled() {
        return TenantwallConfig.isParserEnabled();
    }

    public static String modify(String sql, String column, String value, String dialect)
            throws PythonServiceException {

        if (!isEnabled()) {
            throw new PythonServiceException(
                    "Python parser is not configured (-Dtenantwall.parser.url)");
        }

        try {
            String jsonBody = buildJsonRequest(sql, column, value, dialect);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/modify"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new PythonServiceException(
                        "Python service returned HTTP " + response.statusCode()
                                + ": " + response.body());
            }

            return extractResult(response.body());

        } catch (PythonServiceException e) {
            throw e;
        } catch (Exception e) {
            String detail = e.getMessage();
            if (detail == null) {
                detail = e.getClass().getSimpleName()
                        + (e.getCause() != null ? " (cause: " + e.getCause() + ")" : "");
            }
            throw new PythonServiceException(
                    "Failed to call Python service at " + BASE_URL + ": " + detail, e);
        }
    }

    public static boolean isAvailable() {
        if (!isEnabled())
            return false;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildJsonRequest(String sql, String column, String value, String dialect) {
        return String.format(
                "{\"sql\":%s,\"column\":%s,\"value\":%s,\"dialect\":%s}",
                escapeJson(sql),
                escapeJson(column),
                escapeJson(value),
                escapeJson(dialect));
    }

    private static String escapeJson(String s) {
        if (s == null)
            return "null";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return "\"" + sb + "\"";
    }

    /**
     * Extracts the "result" field from the JSON response.
     * Simple parser - no external JSON library needed.
     */
    private static String extractResult(String json) throws PythonServiceException {
        // Check success
        if (json.contains("\"success\":false")) {
            String error = extractJsonString(json, "error");
            throw new PythonServiceException("Python service error: " + error);
        }

        String result = extractJsonString(json, "result");
        if (result == null) {
            throw new PythonServiceException("No result in Python response: " + json);
        }
        return result;
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1)
            return null;

        start += search.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        if (start >= json.length())
            return null;

        // null value
        if (json.startsWith("null", start)) {
            return null;
        }

        // String value
        if (json.charAt(start) != '"') {
            return null;
        }
        start++;

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                switch (next) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    default:
                        sb.append(next);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Custom exception for Python service errors.
     */
    public static class PythonServiceException extends Exception {
        public PythonServiceException(String message) {
            super(message);
        }

        public PythonServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}