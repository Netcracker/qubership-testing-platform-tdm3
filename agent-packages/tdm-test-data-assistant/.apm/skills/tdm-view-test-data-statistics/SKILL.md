---
name: tdm-view-test-data-statistics
description: Use when the user wants row counts (available, occupied, created, outdated) for TDM3 tables, or occupation history by user, rather than the rows themselves. Calls the TDM3 REST API directly.
user-invocable: false
---

# View TDM3 test data statistics

Read-only counts and history, scoped by project (and optionally system). None of these return row data — use
[tdm-browse-test-data-table](../tdm-browse-test-data-table/SKILL.md) or
[tdm-find-test-data](../tdm-find-test-data/SKILL.md) for that.

## Required inputs

Run [tdm-select-context](../tdm-select-context/SKILL.md) first for `PROJECT_ID` and, where the user wants to narrow
to one system, `SYSTEM_ID_DEFAULT`.

## Available and occupied row counts, per table

```bash
curl "<TDM3_BASE_URL>/api/tdm/statistics/data/available?projectId=<PROJECT_ID>"
# optionally: &systemId=<SYSTEM_ID_DEFAULT>
```

One `GeneralStatisticsItem` per table: `{context (title), environment, system, available, occupied, occupiedToday,
total}`. A row grouped across systems carries `details` (one entry per system) instead of the four counts —
check for `details` before reading `available`/`occupied` directly.

## Rows occupied per period, by table

```bash
curl "<TDM3_BASE_URL>/api/tdm/statistics/data/occupied?projectId=<PROJECT_ID>&dateFrom=2026-09-01&dateTo=2026-09-30"
```

Splits the period into days, weeks, months, or years depending on its length.
`GET /api/tdm/statistics/data/outdated` answers the same question plus a per-period outdated count — add
`&expirationDate=<days>`: a row occupied on or after `dateFrom` plus that many days counts as outdated.
`GET /api/tdm/statistics/data/created/when` answers it for row creation instead of occupation, with the same
`projectId`/`systemId`/`dateFrom`/`dateTo` parameters and no `expirationDate`.

## Rows occupied per user

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/statistics/data/occupied/users' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectId": "<PROJECT_ID>",
    "dateFrom": "2026-09-01",
    "dateTo": "2026-09-30",
    "offset": 0,
    "limit": 50,
    "filters": []
  }'
```

`filters` matches on table title, username, system name, or environment name (`From`/`To` don't apply here).
Returns `{"data": [...], "records": <total>}`, paged. `dateTo` must be later than `dateFrom`; otherwise the request
returns HTTP 400.

## The default report threshold

```bash
curl "<TDM3_BASE_URL>/api/tdm/statistics/threshold"
```

Verified live: returns a bare integer (`10`) — the default available-row threshold suggested for a new report
schedule (see [tdm-manage-statistics-schedule](../tdm-manage-statistics-schedule/SKILL.md)), not wrapped in JSON.

## Migration: add the OCCUPIED_DATE column

```bash
curl "<TDM3_BASE_URL>/api/tdm/statistics/fix/occupied/date/column"
```

Verified live: works on H2 (`200`), returns a JSON array of the table names it altered (`[]` when every table
already has the column). Unlike two of the migrations in
[tdm-run-legacy-migrations](../tdm-run-legacy-migrations/SKILL.md), this one is safe to run — but it's still a
one-time migration for legacy data, not an everyday operation.

## Common pitfalls

- Reading `available`/`occupied` off a row that actually carries `details`: that shape means the row sums several
  systems, and the four counts aren't set on it directly.
- Calling `/data/occupied/users` with `dateTo` not later than `dateFrom`: HTTP 400, not an empty result.
