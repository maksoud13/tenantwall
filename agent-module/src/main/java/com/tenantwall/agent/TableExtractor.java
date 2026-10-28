package com.tenantwall.agent;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Extracts the table names from a Select's immediate FROM / JOIN clauses.
 * Does NOT recurse into subqueries — those are handled separately when
 * SelectHandler processes them.
 */
public final class TableExtractor {

    private TableExtractor() {
    }

    /**
     * Returns lowercased table names in the immediate FROM/JOINs.
     * Empty set means: either no FROM (e.g. "SELECT 1") or only subqueries
     * in the FROM (which we can't inspect without recursion).
     */
    public static Set<String> getImmediateTables(Select select) {
        Set<String> tables = new HashSet<>();
        collect(select, tables);
        return tables;
    }

    private static void collect(Select select, Set<String> out) {
        if (select instanceof PlainSelect ps) {
            addFromItem(ps.getFromItem(), out);
            if (ps.getJoins() != null) {
                for (Join join : ps.getJoins()) {
                    addFromItem(join.getRightItem(), out);
                }
            }
        } else if (select instanceof SetOperationList setOp) {
            for (Select branch : setOp.getSelects()) {
                collect(branch, out);
            }
        } else if (select instanceof ParenthesedSelect ps) {
            Select inner = ps.getSelect();
            if (inner != null)
                collect(inner, out);
        }
    }

    private static void addFromItem(FromItem item, Set<String> out) {
        if (item == null)
            return;
        if (item instanceof Table t) {
            String name = t.getName();
            if (name != null && !name.isEmpty()) {
                out.add(name.toLowerCase(Locale.ROOT));
            }
        }
        // ParenthesedSelect (subquery in FROM), LateralSubSelect, etc.
        // → intentionally NOT recursed here.
    }
}