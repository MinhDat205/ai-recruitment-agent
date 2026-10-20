-- Xuat schema public tu catalog Postgres thanh mot JSON (khong doc file migration).
WITH t AS (
    SELECT c.oid, c.relname
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind IN ('r', 'p')
      AND c.relname <> 'flyway_schema_history'
),
cols AS (
    SELECT t.relname,
           json_agg(json_build_object(
               'name', a.attname,
               'type', format_type(a.atttypid, a.atttypmod),
               'notNull', a.attnotnull
           ) ORDER BY a.attnum) AS columns
    FROM t
    JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum > 0 AND NOT a.attisdropped
    GROUP BY t.relname
),
cons AS (
    SELECT t.relname,
           json_agg(json_build_object(
               'name', co.conname,
               'kind', co.contype,
               'columns', (SELECT json_agg(a.attname ORDER BY k.ord)
                           FROM unnest(co.conkey) WITH ORDINALITY k(attnum, ord)
                           JOIN pg_attribute a ON a.attrelid = co.conrelid AND a.attnum = k.attnum),
               'refTable', CASE WHEN co.contype = 'f' THEN co.confrelid::regclass::text END,
               'refColumns', CASE WHEN co.contype = 'f' THEN
                             (SELECT json_agg(a.attname ORDER BY k.ord)
                              FROM unnest(co.confkey) WITH ORDINALITY k(attnum, ord)
                              JOIN pg_attribute a ON a.attrelid = co.confrelid AND a.attnum = k.attnum) END,
               'definition', pg_get_constraintdef(co.oid)
           ) ORDER BY co.contype, co.conname) AS constraints
    FROM t
    JOIN pg_constraint co ON co.conrelid = t.oid AND co.contype IN ('p', 'f', 'u', 'c')
    GROUP BY t.relname
),
uidx AS (
    -- Unique index khong gan voi constraint (gom ca partial unique index)
    SELECT t.relname,
           json_agg(json_build_object(
               'name', ic.relname,
               'columns', (SELECT json_agg(COALESCE(a.attname, '(expr)') ORDER BY k.ord)
                           FROM unnest(i.indkey::int2[]) WITH ORDINALITY k(attnum, ord)
                           LEFT JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum),
               'predicate', pg_get_expr(i.indpred, i.indrelid),
               'definition', pg_get_indexdef(i.indexrelid)
           ) ORDER BY ic.relname) AS unique_indexes
    FROM t
    JOIN pg_index i ON i.indrelid = t.oid AND i.indisunique
    JOIN pg_class ic ON ic.oid = i.indexrelid
    WHERE NOT EXISTS (SELECT 1 FROM pg_constraint co WHERE co.conindid = i.indexrelid)
    GROUP BY t.relname
)
SELECT json_build_object(
    'flywayVersion', (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1),
    'exportedAt', to_char(now() AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD HH24:MI'),
    'tables', json_agg(json_build_object(
        'name', t.relname,
        'columns', cols.columns,
        'constraints', COALESCE(cons.constraints, '[]'::json),
        'uniqueIndexes', COALESCE(uidx.unique_indexes, '[]'::json)
    ) ORDER BY t.relname)
)
FROM t
JOIN cols ON cols.relname = t.relname
LEFT JOIN cons ON cons.relname = t.relname
LEFT JOIN uidx ON uidx.relname = t.relname;
