package com.tenantwall.agent;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;

/**
 * Main entry point for SQL modification.
 *
 * <p>
 * Routes each statement type to its dedicated handler:
 * <ul>
 * <li>{@link SelectHandler} → SELECT queries (with JOIN, UNION,
 * subqueries)</li>
 * <li>{@link UpdateHandler} → UPDATE statements</li>
 * <li>{@link DeleteHandler} → DELETE statements</li>
 * <li>{@link InsertHandler} → INSERT statements</li>
 * </ul>
 *
 * <h2>Failure semantics</h2>
 *
 * <p>
 * Parse failures raise {@link TenantWallParseException}. The caller
 * ({@link SmartSqlModifier}) applies {@code ParserFailureMode} to decide
 * whether to fail-closed, fall back, or (unsafely) fail-open.
 *
 * <p>
 * This class does NOT swallow parse errors. Swallowing here would bypass
 * the configured failure mode — a security regression.
 */
public class SqlModifier {

    private static final Logger log = LoggerFactory.getLogger("TenantWall");
    private static final CCJSqlParserManager PARSER = new CCJSqlParserManager();

    /**
     * Adds a tenant filter to any SQL statement.
     * Dispatches to the appropriate handler based on the statement type.
     *
     * @param sql    the original SQL statement
     * @param column the filter column name (e.g., "tenant_id")
     * @param value  the filter value (e.g., "TENANT_A")
     * @return the modified SQL
     * @throws TenantWallParseException if parsing or rewriting fails
     */
    public static String addFilter(String sql, String column, String value) {
        // ─── Defensive null/empty check ───
        // Callers (SmartSqlModifier) validate inputs already, but this class
        // may be invoked directly (tests, legacy code paths). Null inputs are
        // a configuration error — fail-closed rather than silently passing.
        if (sql == null) {
            return null; // No SQL — normal for JDBC metadata calls
        }
        if (column == null || column.isEmpty()) {
            throw new TenantWallParseException(
                    "TenantWall: filter column is not configured.");
        }
        if (value == null || value.isEmpty()) {
            throw new TenantWallParseException(
                    "TenantWall: tenant ID is missing for this request.");
        }

        Statement stmt;
        try {
            stmt = PARSER.parse(new StringReader(sql));
        } catch (JSQLParserException e) {
            // Parse failure — caller decides fail-closed vs fallback vs fail-open.
            // We must NOT swallow here; that would bypass the configured mode.
            throw new TenantWallParseException(
                    "TenantWall: failed to parse SQL: " + e.getMessage(), e);
        } catch (Exception e) {
            // Unexpected — surface as a distinct error so callers can distinguish
            // parser bugs from syntax errors.
            throw new TenantWallParseException(
                    "TenantWall: unexpected parser error: " + e.getMessage(), e);
        }

        if (stmt instanceof Select) {
            return SelectHandler.handle((Select) stmt, column, value);
        }
        if (stmt instanceof Update) {
            return UpdateHandler.handle((Update) stmt, column, value);
        }
        if (stmt instanceof Delete) {
            return DeleteHandler.handle((Delete) stmt, column, value);
        }
        if (stmt instanceof Insert) {
            return InsertHandler.handle((Insert) stmt, column, value);
        }

        // Unsupported statement type (CREATE, DROP, ALTER, TRUNCATE, ...).
        // DDL is intentionally untouched — no exception. Migrations rely on
        // exact schema SQL, and rewriting them would break Flyway/Liquibase.
        log.debug("{} Unsupported statement type: {}",
                LogTags.DDL, stmt.getClass().getSimpleName());
        return sql;
    }

    /**
     * Legacy alias. Kept for backward compatibility.
     */
    public static String addTenantId(String sql, String tenantId) {
        return addFilter(sql, "tenant_id", tenantId);
    }
}