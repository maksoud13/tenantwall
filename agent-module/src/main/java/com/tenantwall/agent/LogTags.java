package com.tenantwall.agent;

/**
 * Centralized log tag constants.
 *
 * <p>
 * Used as the first {@code {}} placeholder in SLF4J messages to provide
 * a stable, greppable tag without emojis:
 *
 * <pre>{@code
 * log.info("{} Interceptors installed", LogTags.AGENT);
 * // → INFO TenantWall - [AGENT] Interceptors installed
 * }</pre>
 *
 * <p>
 * Never include the log level in a tag (e.g., "[ERROR]"). The level is
 * already part of the SLF4J log record. Tags describe <em>subsystem</em>,
 * not severity.
 */
public final class LogTags {

    /** Startup, premain, ByteBuddy install. */
    public static final String AGENT = "[AGENT]";

    /** SQL rewrite at Hibernate layer (Layer 1). */
    public static final String HIBERNATE = "[HIBERNATE]";

    /** SQL rewrite at JDBC Statement layer (Layer 2). */
    public static final String JDBC = "[JDBC]";

    /** SQL rewrite at Connection.prepareStatement layer (Layer 3). */
    public static final String CONNECTION = "[CONNECTION]";

    /** SmartSqlModifier: routing, cache, counters. */
    public static final String MODIFIER = "[MODIFIER]";

    /** Python parser client. */
    public static final String PARSER = "[PARSER]";

    /** Config load, validation, warnings. */
    public static final String CONFIG = "[CONFIG]";

    /** SELECT handler. */
    public static final String SELECT = "[SELECT]";

    /** INSERT handler. */
    public static final String INSERT = "[INSERT]";

    /** UPDATE handler. */
    public static final String UPDATE = "[UPDATE]";

    /** DELETE handler. */
    public static final String DELETE = "[DELETE]";

    /** Query skipped (excluded table, existing filter). */
    public static final String SKIP = "[SKIP]";

    /** Filter forced (fail-closed override). */
    public static final String FORCE = "[FORCE]";

    /** DDL detection. */
    public static final String DDL = "[DDL]";

    /** Debug utility (SqlModifierDebugger). */
    public static final String DEBUGGER = "[DEBUGGER]";

    public static final Object SQL = "[SQL]";

    private LogTags() {
        // utility class
    }
}