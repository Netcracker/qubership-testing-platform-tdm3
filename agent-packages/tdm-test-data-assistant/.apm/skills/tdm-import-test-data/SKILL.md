---
name: tdm-import-test-data
description: Use when the user wants to load test data into TDM3 from an Excel file or from a SQL query against a system's database, or update a table's rows from a fresh SQL query result. Calls the TDM3 REST API directly.
---

# Import or update a TDM3 table from an external source

Three ways to bring data into TDM3 from outside, distinct from
[tdm-insert-test-data](../tdm-insert-test-data/SKILL.md) (which inserts rows the caller already has in hand).

## Required inputs

Run [tdm-select-context](../tdm-select-context/SKILL.md) first for `PROJECT_ID` and, where used below,
`SYSTEM_ID_DEFAULT`. These endpoints take project/environment/system **IDs**, not the names
`atp-action-controller` uses.

## Import rows from an Excel file

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/import/excel' \
  -F 'projectId=<PROJECT_ID>' \
  -F 'environmentId=<ENV_ID_DEFAULT>' \
  -F 'systemId=<SYSTEM_ID_DEFAULT>' \
  -F 'tableTitle=<table title>' \
  -F 'runSqlScript=false' \
  -F 'file=@/path/to/file.xlsx'
```

Multipart form data, not JSON — the one endpoint in this package shaped that way. `environmentId` and `systemId`
are required here even though their query-parameter declaration allows omitting them: the controller's own
description says the import fails without them. Creates the table if none with this title exists in the system yet.
Set `runSqlScript: true` to also run the table's saved update-by-query script (see `/update/sql` below) against the
loaded rows afterward.

Verified live: response is a one-element `List<ImportTestDataStatistic>`, the same shape `/import/sql` returns for
several environments — `envName` is always `null` here, since this endpoint only ever targets the one
environment/system passed in:

```json
[{ "envName": null, "error": null, "processedRows": 14 }]
```

**On Windows, building the `-F file=@...` value from a Git Bash/MSYS shell: don't append a `;type=...` (or
`;filename=...`) modifier to a POSIX-style path (`/c/Users/...`) in the same argument.** Verified live and
reproduced twice: that exact combination makes `curl.exe` fail to read the file (`exit 26`, no request ever sent)
even though the file exists and is readable; either half alone — a POSIX path with no modifier, or a Windows path
(`C:\...`) with one — works. The server accepts the file without a `;type=...` override regardless, so the
simplest fix is to never add one: `-F "file=@<path>"`.

## Import rows with a SQL query

```bash
curl -G -X POST '<TDM3_BASE_URL>/api/tdm/import/sql' \
  --data-urlencode 'projectId=<PROJECT_ID>' \
  --data-urlencode 'environmentsIds=<ENV_ID_DEFAULT>' \
  --data-urlencode 'systemName=<system name>' \
  --data-urlencode 'tableTitle=<table title>' \
  --data-urlencode 'query=SELECT * FROM customers' \
  --data-urlencode 'queryTimeout=1800'
```

Takes plain query parameters on a `POST`, not a JSON body — `-G` makes `curl` put `--data-urlencode` values in the
query string of URL instead of the request body while `-X POST` keeps the method.

`environmentsIds` accepts several IDs (repeat the parameter) to import the same query into one table per
environment in a single call. `systemName` here is a name, not an ID — unlike the rest of this skill. TDM3 saves
`query` and `queryTimeout` with the table, which is what makes
[tdm-refresh-test-data-table](../tdm-refresh-test-data-table/SKILL.md) able to re-run it later.

Response is one `ImportTestDataStatistic` per environment:

```json
[{ "envName": "STAGE", "processedRows": 42, "error": null }]
```

A missing environment or system shows up as `error` on that entry, not as a thrown request failure — check every
entry, not just the first.

## Update a table's rows from a SQL query

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/update/sql' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectId": "<PROJECT_ID>",
    "environmentId": "<ENV_ID_DEFAULT>",
    "systemId": "<SYSTEM_ID_DEFAULT>",
    "tableName": "<database table name>",
    "query": "SELECT * FROM customers WHERE status = '\''active'\''",
    "queryTimeout": 1800
  }'
```

Addresses the table by database name (from [tdm-list-tables](../tdm-list-tables/SKILL.md)), not by title, and
takes a JSON body unlike `/import/sql`. Saves `query` and `queryTimeout` with the table the same way `/import/sql`
does. Returns a single `ImportTestDataStatistic` (no array — there's only one environment/system pair here).

## Common pitfalls

- Sending `/import/excel` as JSON: it's `multipart/form-data`, the only endpoint in this package that is.
- Combining a POSIX-style path with a `;type=...` modifier in `-F file=@...` from a Windows Git Bash/MSYS shell —
  see above; drop the modifier rather than debugging the path.
- Omitting `environmentId`/`systemId` on `/import/excel` because they're marked optional: the import fails without
  them regardless.
- Assuming `/import/sql`'s per-environment errors surface as an HTTP failure: they don't — read every entry's
  `error` field.
