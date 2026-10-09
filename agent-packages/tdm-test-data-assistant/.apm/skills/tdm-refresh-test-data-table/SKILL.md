---
name: tdm-refresh-test-data-table
description: Use when the user wants to re-run a TDM3 table's saved import query on demand, outside its schedule, to pick up current data from the source system. Calls the TDM3 REST API directly.
user-invocable: false
---

# Refresh a TDM3 test data table

Call `POST /api/tdm/rest/refresh-tables`. It re-runs the import query saved for the table (the one set up through
TDM3's Excel/SQL import, not through this package's [tdm-insert-test-data](../tdm-insert-test-data/SKILL.md)) and
replaces its rows with the query's current result.

## Required inputs

Run [tdm-select-context](../tdm-select-context/SKILL.md) first if `PROJECT_NAME`, `ENV_NAME_DEFAULT`, or
`SYSTEM_NAME_DEFAULT` aren't resolved yet — read them from
[the config file](../../../README.md#configuration) rather than asking the user directly. Beyond that:

- `title-table` — the table to refresh.
  Decide which table it is as described in [tdm-list-tables](../tdm-list-tables/SKILL.md#which-table-the-user-means).

`envName` and `systemName` are optional here: when either is missing, TDM3 refreshes every table with this title in
the project, across every environment and system. Send the resolved defaults unless the user explicitly asked to
refresh the title everywhere.

## Request

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/rest/refresh-tables' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<project>",
    "envName": "<env>",
    "systemName": "<system>",
    "title-table": "<table title>"
  }'
```

## Response

```json
[{ "type": "SUCCESS", "content": "...", "contentObject": null, "link": "..." }]
```

One entry per table refreshed (more than one when `envName`/`systemName` were omitted and several tables share the
title). `type: "ERROR"` on a table with no saved import query — verified: refreshing a table that was created by
[tdm-insert-test-data](../tdm-insert-test-data/SKILL.md) without `envName`/`systemName` returns `content` reading
`Failed to refresh table with title:<title>. Root cause: Cannot invoke "java.util.UUID.toString()" because
"systemId" is null` — a raw internal error message, not a clear "no import query configured". Recognize this
specific message and tell the user in plain language that the table has no refreshable import query, rather than
surfacing the Java exception text.

## Common pitfalls

- Calling this on a table that was only ever populated by `insert-records` or `occupy-records`: those don't attach
  an import query, so there's nothing to refresh. The NPE-shaped error above is the symptom.
- Refreshing across every environment and system by accident: omitting `envName`/`systemName` is a broader
  operation than it looks — confirm that's what the user wants before sending the request without them.
