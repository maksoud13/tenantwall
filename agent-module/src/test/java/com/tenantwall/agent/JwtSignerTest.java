package com.tenantwall.agent;

import org.junit.jupiter.api.*;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSignerTest {

    private static final String SECRET = "x1234567890abcdef1234567890abcdef";  // 33 chars

    @BeforeEach
    void setUp() {
        TenantwallConfig.setParserSecret(SECRET);
        TenantwallConfig.setParserTokenTtlSeconds(60);
        TenantwallConfig.setParserEnforceAuth(true);
    }

    @AfterEach
    void tearDown() {
        TenantwallConfig.setParserSecret(null);
        TenantwallConfig.setParserTokenTtlSeconds(60);
        TenantwallConfig.setParserEnforceAuth(false);
    }

    // ═══════════════════════════════════════════════════════════════
    // Structure
    // ═══════════════════════════════════════════════════════════════

    @Test
    void sign_returnsThreePartToken() {
        String token = JwtSigner.sign("{\"sql\":\"SELECT 1\"}");
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void sign_headerIsValidJwt() {
        String token = JwtSigner.sign("body");
        String headerB64 = token.split("\\.")[0];
        String headerJson = new String(
                Base64.getUrlDecoder().decode(headerB64));
        assertThat(headerJson).contains("\"alg\":\"HS256\"");
        assertThat(headerJson).contains("\"typ\":\"JWT\"");
    }

    @Test
    void sign_payloadContainsRequiredClaims() {
        String body = "{\"sql\":\"SELECT 1\"}";
        String token = JwtSigner.sign(body);
        String payloadB64 = token.split("\\.")[1];
        String payload = new String(Base64.getUrlDecoder().decode(payloadB64));

        assertThat(payload).contains("\"iss\":\"tenantwall-agent\"");
        assertThat(payload).contains("\"iat\":");
        assertThat(payload).contains("\"exp\":");
        assertThat(payload).contains("\"nonce\":");
        assertThat(payload).contains("\"body_sha256\":");
    }

    @Test
    void sign_bodyHashMatchesSha256() {
        String body = "{\"sql\":\"SELECT 1\"}";
        String token = JwtSigner.sign(body);
        String payloadB64 = token.split("\\.")[1];
        String payload = new String(Base64.getUrlDecoder().decode(payloadB64));

        String expectedHash = JwtSigner.sha256Hex(body);
        assertThat(payload).contains(expectedHash);
    }

    // ═══════════════════════════════════════════════════════════════
    // Nonce — must be unique per token
    // ═══════════════════════════════════════════════════════════════

    @Test
    void sign_differentTokensHaveDifferentNonces() {
        String body = "same-body";
        String token1 = JwtSigner.sign(body);
        String token2 = JwtSigner.sign(body);

        String payload1 = decodePayload(token1);
        String payload2 = decodePayload(token2);

        String nonce1 = extractJsonValue(payload1, "nonce");
        String nonce2 = extractJsonValue(payload2, "nonce");

        assertThat(nonce1).isNotEqualTo(nonce2);
    }

    // ═══════════════════════════════════════════════════════════════
    // TTL
    // ═══════════════════════════════════════════════════════════════

    @Test
    void sign_expEqualsIatPlusTtl() {
        TenantwallConfig.setParserTokenTtlSeconds(120);
        String token = JwtSigner.sign("body");
        String payload = decodePayload(token);

        long iat = Long.parseLong(extractJsonValue(payload, "iat"));
        long exp = Long.parseLong(extractJsonValue(payload, "exp"));

        assertThat(exp - iat).isEqualTo(120);
    }

    @Test
    void sign_defaultTtlIs60Seconds() {
        TenantwallConfig.setParserTokenTtlSeconds(60);
        String token = JwtSigner.sign("body");
        String payload = decodePayload(token);

        long iat = Long.parseLong(extractJsonValue(payload, "iat"));
        long exp = Long.parseLong(extractJsonValue(payload, "exp"));

        assertThat(exp - iat).isEqualTo(60);
    }

    // ═══════════════════════════════════════════════════════════════
    // Missing secret — must throw
    // ═══════════════════════════════════════════════════════════════

    @Test
    void sign_nullSecret_throws() {
        TenantwallConfig.setParserSecret(null);

        assertThatThrownBy(() -> JwtSigner.sign("body"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parser.secret not configured");
    }

    @Test
    void sign_blankSecret_throws() {
        TenantwallConfig.setParserSecret("   ");

        assertThatThrownBy(() -> JwtSigner.sign("body"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parser.secret not configured");
    }

    // ═══════════════════════════════════════════════════════════════
    // Signature — deterministic per secret
    // ═══════════════════════════════════════════════════════════════

    @Test
    void sign_samePayloadAndSecretDifferentSignature() {
        // Signature differs because nonce differs
        String body = "same";
        String token1 = JwtSigner.sign(body);
        String token2 = JwtSigner.sign(body);

        String sig1 = token1.split("\\.")[2];
        String sig2 = token2.split("\\.")[2];

        assertThat(sig1).isNotEqualTo(sig2);
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    @Test
    void sha256Hex_knownValue() {
        // SHA256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        assertThat(JwtSigner.sha256Hex("hello"))
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void sha256Hex_emptyString() {
        // SHA256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertThat(JwtSigner.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void b64url_noPadding() {
        String b64 = JwtSigner.b64url(new byte[]{0x00, 0x00});
        assertThat(b64).doesNotContain("=");
    }

    @Test
    void randomNonce_isUnique() {
        String n1 = JwtSigner.randomNonce();
        String n2 = JwtSigner.randomNonce();
        assertThat(n1).isNotEqualTo(n2);
        // 16 bytes → 22 base64url chars (no padding)
        assertThat(n1).hasSize(22);
    }

    private static String decodePayload(String token) {
        String b64 = token.split("\\.")[1];
        return new String(Base64.getUrlDecoder().decode(b64));
    }

    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) {
            // numeric value
            search = "\"" + key + "\":";
            start = json.indexOf(search) + search.length();
            int end = json.indexOf(',', start);
            if (end == -1) end = json.indexOf('}', start);
            return json.substring(start, end).trim();
        }
        start += search.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}