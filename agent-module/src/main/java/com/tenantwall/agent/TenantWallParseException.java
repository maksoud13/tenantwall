package com.tenantwall.agent;

/**
 * Thrown when TenantWall cannot safely rewrite a SQL query.
 *
 * <p>This exception is raised in {@code FAIL_CLOSED} mode (the default since
 * v1.3.0). The query is <b>not executed</b> — the application sees a 500-class
 * error instead of an unfiltered result set.
 *
 * <p>Applications may catch this exception to fall back to a safer default
 * (e.g. reject the request, show a degraded-mode page). The catch must be
 * explicit: no silent fallback is provided.
 */
public class TenantWallParseException extends RuntimeException {
    public TenantWallParseException(String message) {
        super(message);
    }

    public TenantWallParseException(String message, Throwable cause) {
        super(message, cause);
    }
}