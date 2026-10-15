"""
DataShield SQL Modifier

Uses sqlglot to parse SQL queries and inject tenant filters.
Handles: SELECT, UPDATE, DELETE, INSERT (with CTEs, Window Functions, etc.)
"""

import sqlglot
from sqlglot import exp
from typing import Optional


SUPPORTED_DIALECTS = [
    "mysql", "postgres", "oracle", "tsql", "sqlite",
    "redshift", "snowflake", "bigquery", "spark", "databricks",
]


class SqlModifier:
    def __init__(self, dialect: str = "mysql", excluded_tables: Optional[list] = None):
        if dialect not in SUPPORTED_DIALECTS:
            raise ValueError(f"Unsupported dialect: {dialect}. Use one of: {SUPPORTED_DIALECTS}")
        self.dialect = dialect
        self.excluded_tables = set()
        if excluded_tables:
            for table in excluded_tables:
                self.excluded_tables.add(table.strip().lower())

    # ═══════════════════════════════════════════════════════════════
    # Public API
    # ═══════════════════════════════════════════════════════════════
    def add_filter(self, sql: str, column: str, value: str) -> str:
        try:
            tree = sqlglot.parse_one(sql, dialect=self.dialect)
        except Exception as e:
            raise ValueError(f"Failed to parse SQL: {e}")

        if isinstance(tree, exp.Select):
            self._modify_select(tree, column, value)
        elif isinstance(tree, exp.Union):
            self._modify_union(tree, column, value)
        elif isinstance(tree, exp.Update):
            self._modify_update(tree, column, value)
        elif isinstance(tree, exp.Delete):
            self._modify_delete(tree, column, value)
        elif isinstance(tree, exp.Insert):
            self._modify_insert(tree, column, value)
        elif isinstance(tree, exp.With):
            if tree.this:
                self._modify_tree(tree.this, column, value)
            for cte in tree.expressions:
                if isinstance(cte, exp.CTE):
                    self._modify_tree(cte.this, column, value)
        else:
            raise ValueError(f"Unsupported statement type: {type(tree).__name__}")

        try:
            return tree.sql(dialect=self.dialect, pretty=False)
        except Exception as e:
            raise ValueError(f"Failed to generate SQL: {e}")

    # ═══════════════════════════════════════════════════════════════
    # SELECT handler
    # ═══════════════════════════════════════════════════════════════
    def _modify_select(self, node: exp.Select, column: str, value: str):
        # Process attached CTEs
        with_clause = node.args.get("with")
        if with_clause:
            for cte in with_clause.expressions:
                if isinstance(cte, exp.CTE):
                    self._modify_tree(cte.this, column, value)

        if self._is_trivial_select(node):
            return

        if self._touches_excluded_table(node):
            return

        if self._has_filter(node, column):
            return

        # Add filter to WHERE
        condition = self._build_condition(column, value)
        self._add_or_wrap_where(node, condition)

        # Process subqueries
        self._process_subqueries(node, column, value)

    def _modify_union(self, node: exp.Union, column: str, value: str):
        if node.left:
            self._modify_tree(node.left, column, value)
        if node.right:
            self._modify_tree(node.right, column, value)

    def _modify_tree(self, node: exp.Expression, column: str, value: str):
        if isinstance(node, exp.Union):
            self._modify_union(node, column, value)
        elif isinstance(node, exp.Select):
            self._modify_select(node, column, value)
        elif isinstance(node, exp.Update):
            self._modify_update(node, column, value)
        elif isinstance(node, exp.Delete):
            self._modify_delete(node, column, value)
        elif isinstance(node, exp.Insert):
            self._modify_insert(node, column, value)
        elif isinstance(node, exp.With):
            if node.this:
                self._modify_tree(node.this, column, value)
            for cte in node.expressions:
                if isinstance(cte, exp.CTE):
                    self._modify_tree(cte.this, column, value)

    # ═══════════════════════════════════════════════════════════════
    # UPDATE handler
    # ═══════════════════════════════════════════════════════════════
    def _modify_update(self, node: exp.Update, column: str, value: str):
        if self._touches_excluded_table(node):
            return
        if self._has_filter(node, column):
            return

        condition = self._build_condition(column, value)
        self._add_or_wrap_where(node, condition)

        where = node.args.get("where")
        if where:
            self._process_subqueries_in_expr(where.this, column, value)

    # ═══════════════════════════════════════════════════════════════
    # DELETE handler
    # ═══════════════════════════════════════════════════════════════
    def _modify_delete(self, node: exp.Delete, column: str, value: str):
        if self._touches_excluded_table(node):
            return
        if self._has_filter(node, column):
            return

        condition = self._build_condition(column, value)
        self._add_or_wrap_where(node, condition)

        where = node.args.get("where")
        if where:
            self._process_subqueries_in_expr(where.this, column, value)

    # ═══════════════════════════════════════════════════════════════
    # INSERT handler  (fixed for sqlglot's Schema structure)
    # ═══════════════════════════════════════════════════════════════
    def _modify_insert(self, node: exp.Insert, column: str, value: str):
        if self._touches_excluded_table(node):
            return

        # ─── INSERT INTO ... SELECT ───
        source = node.args.get("expression")
        if isinstance(source, (exp.Select, exp.Union)):
            self._modify_tree(source, column, value)
            return

        # ─── INSERT INTO ... VALUES ───
        # In sqlglot, columns are stored in node.this as exp.Schema
        target = node.this
        if not isinstance(target, exp.Schema):
            # No explicit column list — cannot safely inject
            return

        columns = target.expressions
        if not columns:
            return

        # Skip if column already present
        for col in columns:
            if col.name.lower() == column.lower():
                return

        # Add the column
        columns.append(exp.to_identifier(column))

        # Add the value to each row
        if isinstance(source, exp.Values):
            for row in source.expressions:
                if isinstance(row, exp.Tuple):
                    row.expressions.append(exp.Literal.string(value))

    # ═══════════════════════════════════════════════════════════════
    # Helpers
    # ═══════════════════════════════════════════════════════════════
    def _build_condition(self, column: str, value: str) -> exp.Expression:
        return exp.EQ(
            this=exp.column(column),
            expression=exp.Literal.string(value),
        )

    def _add_or_wrap_where(self, node: exp.Expression, condition: exp.Expression):
        """Add condition to WHERE (or create WHERE). Wrap existing WHERE in parens."""
        where = node.args.get("where")
        if where is None:
            node.set("where", exp.Where(this=condition))
        else:
            original = where.this
            if not isinstance(original, exp.Paren):
                original = exp.Paren(this=original)
            node.set("where", exp.Where(this=exp.And(this=original, expression=condition)))

    def _has_filter(self, node: exp.Expression, column: str) -> bool:
        where = node.args.get("where")
        if where is None:
            return False
        for col in where.find_all(exp.Column):
            if col.name.lower() == column.lower():
                return True
        return False

    def _is_trivial_select(self, select: exp.Select) -> bool:
        expressions = select.expressions
        if len(expressions) == 1:
            first = expressions[0]
            if isinstance(first, exp.Literal) and not first.is_string:
                return True
            if isinstance(first, exp.Null):
                return True
        return False

    def _touches_excluded_table(self, node: exp.Expression) -> bool:
        if not self.excluded_tables:
            return False
        for table in node.find_all(exp.Table):
            if table.name.lower() in self.excluded_tables:
                return True
        return False

    def _process_subqueries(self, select: exp.Select, column: str, value: str):
        for key in ("where", "having"):
            clause = select.args.get(key)
            if clause:
                self._process_subqueries_in_expr(clause.this, column, value)

        for expression in select.expressions:
            self._process_subqueries_in_expr(expression, column, value)

        from_clause = select.args.get("from")
        if from_clause:
            for subquery in from_clause.find_all(exp.Subquery):
                if subquery.this:
                    self._modify_tree(subquery.this, column, value)

    def _process_subqueries_in_expr(self, expr: exp.Expression, column: str, value: str):
        if expr is None:
            return
        for subquery in expr.find_all(exp.Subquery):
            if subquery.this:
                self._modify_tree(subquery.this, column, value)