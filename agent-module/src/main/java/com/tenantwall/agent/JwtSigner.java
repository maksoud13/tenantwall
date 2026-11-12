package com.tenantwall.agent;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * HMAC-SHA256 JWT signer for agent→parser authentication.
 *
 * <p>
 * Token payload includes:
 * <ul>
 * <li>{@code iss} — constant {@code tenantwall-agent}</li>
 * <li>{@code iat} — issued at (Unix seconds)</li>
 * <li>{@code exp} — expiry (Unix seconds)</li>
 * <li>{@code nonce} — 16 random bytes (base64url), single-use</li>
 * <li>{@code body_sha256} — SHA256 hex of the request body</li>
 * </ul>
 *
 * <p>
 * <b>No external dependencies</b> — uses only JDK crypto.
 * Serialization of the payload is hand-built (simple JSON, no Jackson).
 */
public final class JwtSigner {

    private static final String ISSUER = "tenantwall-agent";
    private static final String HMAC_ALG = "HmacSHA256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final SecureRandom RANDOM = new SecureRandom();

    private JwtSigner() {
        // utility class
    }

    /**
     * Signs the given request body. The body's SHA256 is embedded in the token,
     * so any modification to the body after signing will fail verification.
     *
     * @param body the raw JSON body that will be sent to the parser
     * @return a JWT string (header.payload.signature)
     * @throws IllegalStateException if parser secret is not configured
     */
    public static String sign(String body) {
        String secret = TenantwallConfig.getParserSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "parser.secret not configured — cannot sign JWT");
        }

        int ttl = TenantwallConfig.getParserTokenTtlSeconds();
        long now = System.currentTimeMillis() / 1000L;
        long exp = now + ttl;

        String nonce = randomNonce();
        String bodyHash = sha256Hex(body);

        String payload = String.format(
                "{\"iss\":\"%s\",\"iat\":%d,\"exp\":%d,\"nonce\":\"%s\",\"body_sha256\":\"%s\"}",
                ISSUER, now, exp, nonce, bodyHash);

        String headerB64 = b64url(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String payloadB64 = b64url(payload.getBytes(StandardCharsets.UTF_8));
        String signingInput = headerB64 + "." + payloadB64;

        byte[] signature = hmacSha256(
                secret.getBytes(StandardCharsets.UTF_8),
                signingInput.getBytes(StandardCharsets.UTF_8));

        return signingInput + "." + b64url(signature);
    }

    // ═══════════════════════════════════════════════════════════════
    // Package-private helpers (testable)
    // ═══════════════════════════════════════════════════════════════

    static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(key, HMAC_ALG));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    static String b64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    static String randomNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return b64url(bytes);
    }

    static String getIssuer() {
        return ISSUER;
    }
}