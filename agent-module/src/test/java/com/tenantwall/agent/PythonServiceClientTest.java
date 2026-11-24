package com.tenantwall.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class PythonServiceClientTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        TenantwallConfig.setParserUrl(null);
        TenantwallConfig.setParserEnforceAuth(false);
        TenantwallConfig.setParserSecret(null);
    }

    // ═══════════════════════════════════════════════════════════════
    // isEnabled
    // ═══════════════════════════════════════════════════════════════

    @Test
    void isEnabled_falseWhenParserUrlNotConfigured() {
        TenantwallConfig.setParserUrl(null);
        assertThat(PythonServiceClient.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_trueWhenParserUrlSet() {
        TenantwallConfig.setParserUrl(baseUrl);
        assertThat(PythonServiceClient.isEnabled()).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════
    // modify — success path
    // ═══════════════════════════════════════════════════════════════

    @Test
    void modify_success_returnsResult() throws Exception {
        server.createContext("/api/modify", exchange -> {
            String resp = "{\"success\":true,\"result\":\"SELECT * FROM products WHERE tenant_id = 'ACME'\"}";
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });
        TenantwallConfig.setParserUrl(baseUrl);

        String result = PythonServiceClient.modify(
                "SELECT * FROM products", "tenant_id", "ACME", "mysql");

        assertThat(result).contains("tenant_id = 'ACME'");
    }

    // ═══════════════════════════════════════════════════════════════
    // modify — failure paths
    // ═══════════════════════════════════════════════════════════════

    @Test
    void modify_http500_throwsException() throws Exception {
        server.createContext("/api/modify", exchange -> {
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
        });
        TenantwallConfig.setParserUrl(baseUrl);

        assertThatThrownBy(() -> PythonServiceClient.modify(
                "SELECT 1", "tenant_id", "ACME", "mysql"))
                .isInstanceOf(PythonServiceClient.PythonServiceException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void modify_http401_throwsAuthError() throws Exception {
        server.createContext("/api/modify", exchange -> {
            exchange.sendResponseHeaders(401, 0);
            exchange.close();
        });
        TenantwallConfig.setParserUrl(baseUrl);

        assertThatThrownBy(() -> PythonServiceClient.modify(
                "SELECT 1", "tenant_id", "ACME", "mysql"))
                .isInstanceOf(PythonServiceClient.PythonServiceException.class)
                .hasMessageContaining("401");
    }

    @Test
    void modify_successFalse_throwsWithError() throws Exception {
        server.createContext("/api/modify", exchange -> {
            String resp = "{\"success\":false,\"error\":\"parse failed\",\"reference\":\"ref-123\"}";
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });
        TenantwallConfig.setParserUrl(baseUrl);

        assertThatThrownBy(() -> PythonServiceClient.modify(
                "INVALID", "tenant_id", "ACME", "mysql"))
                .isInstanceOf(PythonServiceClient.PythonServiceException.class)
                .hasMessageContaining("parse failed")
                .hasMessageContaining("ref-123");
    }

    @Test
    void modify_noResultField_throws() throws Exception {
        server.createContext("/api/modify", exchange -> {
            String resp = "{\"success\":true}";
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });
        TenantwallConfig.setParserUrl(baseUrl);

        assertThatThrownBy(() -> PythonServiceClient.modify(
                "SELECT 1", "tenant_id", "ACME", "mysql"))
                .isInstanceOf(PythonServiceClient.PythonServiceException.class)
                .hasMessageContaining("No result");
    }

    @Test
    void modify_connectionRefused_throws() {
        TenantwallConfig.setParserUrl("http://127.0.0.1:1");

        assertThatThrownBy(() -> PythonServiceClient.modify(
                "SELECT 1", "tenant_id", "ACME", "mysql"))
                .isInstanceOf(PythonServiceClient.PythonServiceException.class)
                .hasMessageContaining("Failed to call");
    }

    @Test
    void modify_parserDisabled_throws() {
        TenantwallConfig.setParserUrl(null);

        assertThatThrownBy(() -> PythonServiceClient.modify(
                "SELECT 1", "tenant_id", "ACME", "mysql"))
                .isInstanceOf(PythonServiceClient.PythonServiceException.class);
    }

    // ═══════════════════════════════════════════════════════════════
    // modify — auth (JWT)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void modify_withAuthEnabled_sendsBearerToken() throws Exception {
        TenantwallConfig.setParserSecret("12345678901234567890123456789012");
        TenantwallConfig.setParserEnforceAuth(true);
        TenantwallConfig.setParserUrl(baseUrl);

        AtomicReference<String> authHeader = new AtomicReference<>();
        server.createContext("/api/modify", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String resp = "{\"success\":true,\"result\":\"SELECT 1\"}";
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });

        PythonServiceClient.modify("SELECT 1", "tenant_id", "ACME", "mysql");

        assertThat(authHeader.get()).startsWith("Bearer ");
    }

    // ═══════════════════════════════════════════════════════════════
    // JSON escaping (body inspection)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void modify_sqlWithDoubleQuote_escapesCorrectly() throws Exception {
        TenantwallConfig.setParserUrl(baseUrl);
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/api/modify", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String resp = "{\"success\":true,\"result\":\"ok\"}";
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });

        PythonServiceClient.modify(
                "SELECT * FROM t WHERE name = \"O'Brien\"",
                "tenant_id", "ACME", "mysql");

        // Escaped double-quote must appear as \" in the JSON body
        assertThat(body.get()).contains("\\\"O'Brien\\\"");
    }

    @Test
    void modify_sqlWithBackslash_escapesCorrectly() throws Exception {
        TenantwallConfig.setParserUrl(baseUrl);
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/api/modify", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String resp = "{\"success\":true,\"result\":\"ok\"}";
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });

        PythonServiceClient.modify(
                "SELECT * FROM t WHERE path = 'C:\\temp'",
                "tenant_id", "ACME", "mysql");

        // Backslash must appear as \\ in the JSON body
        assertThat(body.get()).contains("C:\\\\temp");
    }

    @Test
    void modify_sqlWithNewline_escapesCorrectly() throws Exception {
        TenantwallConfig.setParserUrl(baseUrl);
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/api/modify", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String resp = "{\"success\":true,\"result\":\"ok\"}";
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });

        PythonServiceClient.modify(
                "SELECT *\nFROM t",
                "tenant_id", "ACME", "mysql");

        // Newline must appear as \n in the JSON body
        assertThat(body.get()).contains("\\n");
    }
}