---
name: tdm-manage-available-data-by-column-stats
description: Use when the user wants available-row counts broken down by a column's value across a system's tables (for example, available rows per region or per customer type), or to configure that breakdown and its email schedule. Calls the TDM3 REST API directly.
user-invocable: false
---

# TDM3 available-data-by-column statistics

A per-system, per-environment statistic: available rows grouped by the value of one chosen column, across every
table of the system that has it. Distinct from
[tdm-view-test-data-statistics](../tdm-view-test-data-statistics/SKILL.md), which counts by table, not by column
value.

## Required inputs

Run [tdm-select-context](../tdm-select-context/SKILL.md) first for `ENV_ID_DEFAULT` and `SYSTEM_ID_DEFAULT` — every
endpoint here takes `systemId` + `environmentId`, not a project ID.

## Configure which column to group by

```bash
curl "<TDM3_BASE_URL>/api/tdm/statistics/available/column/configuration?systemId=<SYSTEM_ID_DEFAULT>&environmentId=<ENV_ID_DEFAULT>"
```

Returns `columnKeys` (every column name common to the system's tables, to choose from) and `description` /
`activeColumnKey` (the same value twice — the column currently grouped on).

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/statistics/available/column/configuration' \
  -H 'Content-Type: application/json' \
  -d '{
    "systemId": "<SYSTEM_ID_DEFAULT>",
    "environmentId": "<ENV_ID_DEFAULT>",
    "description": "REGION",
    "tablesColumns": [{ "tableName": "<database table name>", "tableTitle": "<title>", "values": ["EU", "US"] }]
  }'
```

`tablesColumns` restricts which column values count as "available" per table; a table left out of it counts every
value.

## Read the current breakdown

```bash
curl "<TDM3_BASE_URL>/api/tdm/statistics/data/occupied/available?systemId=<SYSTEM_ID_DEFAULT>&environmentId=<ENV_ID_DEFAULT>"
```

Fails when no configuration is saved for this system and environment yet — save one first with the endpoint above.

## Email schedule for this statistic

```bash
curl "<TDM3_BASE_URL>/api/tdm/statistics/schedule/available?systemId=<SYSTEM_ID_DEFAULT>&environmentId=<ENV_ID_DEFAULT>"
```

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/statistics/schedule/available' \
  -H 'Content-Type: application/json' \
  -d '{
    "systemId": "<SYSTEM_ID_DEFAULT>",
    "environmentId": "<ENV_ID_DEFAULT>",
    "description": "REGION",
    "schedule": "0 0 8 * * ?",
    "scheduled": true,
    "threshold": 10,
    "recipients": "qa@example.com"
  }'
```

```bash
curl -X DELETE "<TDM3_BASE_URL>/api/tdm/statistics/schedule/available?systemId=<SYSTEM_ID_DEFAULT>&environmentId=<ENV_ID_DEFAULT>"
```

Delete takes query parameters, not a body — the one delete in this skill shaped that way (contrast
[tdm-manage-statistics-schedule](../tdm-manage-statistics-schedule/SKILL.md)'s deletes, which take a JSON body with
`projectId`).

## Common pitfalls

- Reading the breakdown before a configuration is saved: fails outright rather than returning an empty result.
- Sending a body to the DELETE here: it reads `systemId`/`environmentId` from the query string.
- Addressing by `projectId`: every endpoint in this skill takes `systemId` + `environmentId` instead.
