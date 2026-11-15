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
 * <p>Routes each statement type to its dedicated handler:
 * <ul>
 * <li>{@link SelectHandler} → SELECT queries (with JOIN, UNION, subqueries)</li>
 * <li>{@link UpdateHandler} → UPDATE statements</li>
 * <li>{@link DeleteHandler} → DELETE statements</li>
 * <li>{@link InsertHandler} → INSERT statements</li>
 * </ul>
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
     * @return the modified SQL, or the original if unsupported / unparseable
     */
    public static String addFilter(String sql, String column, String value) {
        if (sql == null || column == null || column.isEmpty()
                || value == null || value.isEmpty()) {
            return sql;
        }

        try {
            Statement stmt = PARSER.parse(new StringReader(sql));

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

            // Unsupported (CREATE, DROP, ALTER, TRUNCATE, ...) — DDL is expected post-Task-4
            log.debug("{} Unsupported statement type: {}",
                    LogTags.DDL, stmt.getClass().getSimpleName());
            return sql;

        } catch (JSQLParserException e) {
            // Parse failure — designed fallback to original SQL. Visible warning because
            // unfiltered SQL is a security concern (fail-open).
            log.warn("{} Parse error (returning original SQL): {}",
                    LogTags.SQL, e.getMessage());
            return sql;
        } catch (Exception e) {
            // Unexpected — bug or classpath issue
            log.warn("{} Unexpected error (returning original SQL)", LogTags.SQL, e);
            return sql;
        }
    }

    /**
     * Legacy alias. Kept for backward compatibility.
     */
    public static String addTenantId(String sql, String tenantId) {
        return addFilter(sql, "tenant_id", tenantId);
    }
}