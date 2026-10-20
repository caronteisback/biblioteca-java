# Query plans

How to check that the hot queries use the indexes, and what was found on the first schema (`V1`).

## How to check a plan

In SQL*Plus, SQLcl or any Oracle client, connected as the application user:

```sql
EXPLAIN PLAN FOR
  SELECT COUNT(*) FROM prestamo WHERE socio_id = :socio AND fecha_devolucion IS NULL;

SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, NULL, 'BASIC'));
```

`EXPLAIN PLAN` shows the plan the optimizer would choose without running the query. Use `'TYPICAL'` instead of `'BASIC'` to also see cost, rows and predicates. To see what actually ran, execute the query and then call `DBMS_XPLAN.DISPLAY_CURSOR`.

Two things matter for a meaningful result:

- **Gather statistics first** (`EXEC DBMS_STATS.GATHER_SCHEMA_STATS(USER)`). Without them the optimizer guesses.
- **Use a realistic volume.** On an almost empty table a full scan is the right plan, and the plan changes as data grows. The plans below were taken with 500 books, 1,500 copies, 1,000 members and about 16,000 loans (300 of them active), all fictional and deleted afterwards.

## Plans found on `V1`

| Query (used for) | Plan | Verdict |
|------------------|------|---------|
| Active loans of a member (limit of 5) | `INDEX RANGE SCAN IX_PRESTAMO_SOCIO` | The composite index `(socio_id, fecha_devolucion)` answers the count without touching the table |
| Is this copy on loan? (Spring Data derived query) | `INDEX RANGE SCAN IX_PRESTAMO_EJEMPLAR` + table access | Fine: it reads the few loans of one copy |
| Same check written with the unique index expression | `INDEX UNIQUE SCAN UX_PRESTAMO_EJEMPLAR_ACTIVO` | The best possible plan, one entry. Optional later optimization with a native query |
| Whole loan history of a member (score input) | `INDEX RANGE SCAN IX_PRESTAMO_SOCIO` + table access | Fine |
| Member by email, ignoring case | `INDEX UNIQUE SCAN UX_SOCIO_EMAIL` | The function-based index on `LOWER(email)` is used |
| Pending fine of a member (join through the loan) | `TABLE ACCESS FULL MULTA` + nested loop | Acceptable only because `multa` was empty. Re-check once fines exist |

## Findings

- **Oracle does not index foreign keys.** Without `IX_PRESTAMO_EJEMPLAR` and `IX_PRESTAMO_SOCIO`, joins would scan `prestamo` and deleting a parent row would lock the child table. The first draft of the ER diagram listed only two of these indexes; the ejemplar one was added with `V1`.
- **The unique index can also serve queries.** Oracle uses a function-based index only when the query repeats the same expression. The derived query `existsByEjemplarIdAndFechaDevolucionIsNull` does not, so it uses the foreign key index instead. The difference is negligible at this scale.
- **To re-check when data grows:** the pending-fine lookup. If it ever shows a full scan on a large `multa`, an index on `multa(estado)` is not the fix (very low selectivity); drive the query from `prestamo(socio_id)` instead, which is what the optimizer should do once it has real statistics.
