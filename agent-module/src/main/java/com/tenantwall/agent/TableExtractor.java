package com.tenantwall.agent;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Extracts table names from a Select's immediate FROM / JOIN clauses.
 *
 * <p>
 * <b>Security-critical distinction:</b> This class returns only
 * <b>physical</b> table names. A name that matches a CTE alias defined in the
 * same SELECT scope is a virtual relation, not a physical table — it is
 * excluded from the result.
 *
 * <p>
 * This distinction prevents a CTE alias from being mistaken for an excluded
 * physical table. Without it, an attacker could define a CTE whose alias
 * matches
 * an excluded table name and cause the entire query — including the CTE body —
 * to bypass tenant filtering.
 *
 * <p>
 * Does NOT recurse into subqueries — those are processed separately by
 * {@link SelectHandler} when it recurses into the AST.
 */
public final class TableExtractor {

    private TableExtractor() {
    }

    /**
     * Returns lowercased <b>physical</b> table names in the immediate FROM/JOINs.
     *
     * <p>
     * CTE references (names matching a CTE alias in the same scope) are
     * excluded. If all FROM/JOIN items are CTEs or subqueries, an empty set
     * is returned — callers must treat "empty" as "unknown", not as "no tables".
     *
     * @param select the SELECT to inspect
     * @return set of lowercased physical table names (never null)
     */
    public static Set<String> getImmediateTables(Select select) {
        Set<String> cteAliases = collectCteAliases(select);
        Set<String> tables = new HashSet<>();
        collectPhysical(select, cteAliases, tables);
        return tables;
    }

    // ═══════════════════════════════════════════════════════════════
    // CTE alias collection
    // ═══════════════════════════════════════════════════════════════

    private static Set<String> collectCteAliases(Select select) {
        Set<String> aliases = new HashSet<>();
        collectCteAliasesInternal(select, aliases);
        return aliases;
    }

    private static void collectCteAliasesInternal(Select select, Set<String> out) {
        if (select instanceof PlainSelect ps) {
            addCteAliases(ps.getWithItemsList(), out);
        } else if (select instanceof SetOperationList setOp) {
            addCteAliases(setOp.getWithItemsList(), out);
            List<Select> branches = setOp.getSelects();
            if (branches != null) {
                for (Select branch : branches) {
                    collectCteAliasesInternal(branch, out);
                }
            }
        } else if (select instanceof ParenthesedSelect ps) {
            if (ps.getSelect() != null) {
                collectCteAliasesInternal(ps.getSelect(), out);
            }
        }
    }

    private static void addCteAliases(List<WithItem> withItems, Set<String> out) {
        if (withItems == null)
            return;
        for (WithItem item : withItems) {
            if (item.getAlias() != null && item.getAlias().getName() != null) {
                out.add(item.getAlias().getName().toLowerCase(Locale.ROOT));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Physical table collection
    // ═══════════════════════════════════════════════════════════════

    private static void collectPhysical(Select select, Set<String> cteAliases, Set<String> out) {
        if (select instanceof PlainSelect ps) {
            addPhysicalFromItem(ps.getFromItem(), cteAliases, out);
            if (ps.getJoins() != null) {
                for (Join join : ps.getJoins()) {
                    addPhysicalFromItem(join.getRightItem(), cteAliases, out);
                }
            }
        } else if (select instanceof SetOperationList setOp) {
            List<Select> branches = setOp.getSelects();
            if (branches != null) {
                for (Select branch : branches) {
                    collectPhysical(branch, cteAliases, out);
                }
            }
        } else if (select instanceof ParenthesedSelect ps) {
            Select inner = ps.getSelect();
            if (inner != null) {
                collectPhysical(inner, cteAliases, out);
            }
        }
    }

    private static void addPhysicalFromItem(FromItem item, Set<String> cteAliases, Set<String> out) {
        if (item == null)
            return;
        if (item instanceof Table t) {
            String name = t.getName();
            if (name != null && !name.isEmpty()) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (!cteAliases.contains(lower)) {
                    out.add(lower);
                }
            }
        } else if (item instanceof ParenthesedSelect ps) {
            // Recurse into subquery in FROM — tables inside must be visible
            // to callers for exclusion checks
            Select inner = ps.getSelect();
            if (inner != null) {
                collectPhysical(inner, cteAliases, out);
            }
        }
    }
}