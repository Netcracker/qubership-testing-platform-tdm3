---
name: tdm-manage-cleanup-config
description: Use to view, save, or run-now a TDM3 table's automatic cleanup rule (age-based or SQL-based, on a cron schedule), or to check when a cron schedule next fires. Calls the TDM3 REST API directly. run-cleanup-table (tdm-cleanup-test-data-table) runs the already-saved rule; this skill is where that rule comes from.
---

# Manage a TDM3 table's cleanup configuration

`data-cleanup-controller` owns the cleanup rule that
[tdm-cleanup-test-data-table](../tdm-cleanup-test-data-table/SKILL.md)'s `run-cleanup-table` executes. Configure it
here first if the user hits `"Cleanup hasn't been configured for table..."` there.

## Get a saved configuration

```bash
curl '<TDM3_BASE_URL>/api/tdm/cleanup/config/<cleanupConfigId>'
```

`cleanupConfigId` comes from [tdm-list-tables](../tdm-list-tables/SKILL.md)'s `/tables/catalog` response
(`cleanupConfigId` field, `null` when none is set). Returns a `CleanupSettings` with `tableName` left unset (per
the endpoint's own description) and `environmentsList` holding the environments of every table currently sharing
this configuration. **An ID with no saved configuration returns a raw `500`, not a graceful error or a `404`** —
verified live. Check `cleanupConfigId` isn't `null` before calling this.

## Save a configuration

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/cleanup/config' \
  -H 'Content-Type: application/json' \
  -d '{
    "tableName": "<database table name>",
    "environmentsList": [],
    "testDataCleanupConfig": {
      "type": "DATE",
      "searchDate": "3w4d",
      "schedule": "0 0 3 * * ?",
      "enabled": true,
      "shared": false,
      "queryTimeout": 1800
    }
  }'
```

`testDataCleanupConfig.type` is `SQL` (with `searchSql`), `DATE` (with `searchDate`, an age like `"3w4d"` compared
against `CREATED_WHEN`), or `CLASS` — **`CLASS` is accepted but always rejected in this build: no cleaner class is
registered, so it fails with `Current search Class is not allowed here <class>` regardless of what's named.** Use
`SQL` or `DATE`. Set `shared: true` (or list environment IDs in `environmentsList`) to apply the same rule to every
table with this title and system across those environments, not just this one. `queryTimeout` must be between 1
and `EXTERNAL_QUERY_MAX_TIMEOUT` (3600 by default) — out of range returns HTTP 400.

## Run a configuration immediately, without saving it

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/cleanup/run' \
  -H 'Content-Type: application/json' \
  -d '{ "tableName": "<database table name>", "environmentsList": [], "testDataCleanupConfig": { "type": "DATE", "searchDate": "3w4d" } }'
```

Same body as saving, but nothing is persisted — useful to preview what a rule would remove. Returns
`List<CleanupResults>`, one per affected table: `{tableName, recordsTotal, recordsRemoved}`, or `error` instead of
the counts when that table's cleanup failed. This deletes rows for real; confirm with the user first, the same as
[tdm-cleanup-test-data-table](../tdm-cleanup-test-data-table/SKILL.md).

## Check a cron schedule's next run

```bash
curl "<TDM3_BASE_URL>/api/tdm/cleanup/next/run?cronExpression=0%200%203%20*%20*%20%3F"
```

Returns a bare JSON string, verified live: `"Thu Oct 01 12:00:00 SAMT 2026"`. Compare against
[tdm-manage-statistics-schedule](../tdm-manage-statistics-schedule/SKILL.md)'s own `next/run`, which wraps the same
kind of value in `{"nextRun": "..."}` instead — don't assume the two shapes match.

## Migration: fill in cleanup type on old configurations

```bash
curl '<TDM3_BASE_URL>/api/tdm/cleanup/fill/cleanup/type'
```

Verified live: works on H2 (`200`, empty body) — sets `type` from whichever of `searchSql`/`searchDate`/`searchClass`
is set, for configurations saved before `type` existed. Safe to run, but only relevant to data migrated from an
older TDM3, so don't call it as part of ordinary work (see
[tdm-run-legacy-migrations](../tdm-run-legacy-migrations/SKILL.md) for the equivalent migrations in other
controllers, two of which are *not* safe).

## Common pitfalls

- Fetching a `cleanupConfigId` that's `null`: raw `500`, not a clean error.
- Setting `type: "CLASS"`: always rejected, whatever the class name.
- Calling `/run` expecting a dry run: it deletes rows for real.
- Comparing this controller's `next/run` shape (bare string) to statistics' (`{nextRun: ...}`) — they differ.
