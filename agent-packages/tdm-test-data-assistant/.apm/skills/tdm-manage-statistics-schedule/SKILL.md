---
name: tdm-manage-statistics-schedule
description: Use when the user wants to set up, check, or cancel TDM3's periodic email reports — a low-available-rows warning per project, or a per-user occupation report. Calls the TDM3 REST API directly.
user-invocable: false
---

# Manage TDM3's statistics email schedules

Two independent schedules per project, both Quartz-cron-driven. Neither sends anything on its own — saving a
schedule with `enabled`/`scheduled: false` keeps the configuration without sending email.

## Required inputs

Run [tdm-select-context](../tdm-select-context/SKILL.md) first for `PROJECT_ID`.

## General-statistics schedule (low-available-rows warning)

```bash
curl "<TDM3_BASE_URL>/api/tdm/statistics/schedule?projectId=<PROJECT_ID>"
```

Returns a disabled schedule with empty fields when the project has none — not a 404. Fields: `cronExpression`,
`enabled`, `recipients` (comma-separated emails), `threshold` (send when a table's available count drops below
this — see [tdm-view-test-data-statistics](../tdm-view-test-data-statistics/SKILL.md)'s `/threshold` for the
system default).

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/statistics/schedule' \
  -H 'Content-Type: application/json' \
  -d '{ "projectId": "<PROJECT_ID>", "cronExpression": "0 0 8 * * ?", "enabled": true, "recipients": "qa@example.com", "threshold": 10 }'
```

```bash
curl -X PUT '<TDM3_BASE_URL>/api/tdm/statistics/delete/schedule' \
  -H 'Content-Type: application/json' \
  -d '{ "projectId": "<PROJECT_ID>" }'
```

Delete reads only `projectId` from the body — the rest is ignored, so sending a full `TestDataTableMonitoring`
object works too, but isn't required. It's a `PUT`, not a `DELETE`.

## Users-occupation schedule

Same three operations, on `/api/tdm/statistics/schedule/users` (GET/POST) and
`/api/tdm/statistics/delete/schedule/users` (PUT). Fields: `cronExpression`, `scheduled` (enables it — note the
different field name from the general schedule's `enabled`), `recipients`, `daysCount` (how many past days the
report covers), `htmlReport`/`csvReport` (include an HTML table in the email body / attach a CSV file).

## Check a cron schedule's next run

```bash
curl "<TDM3_BASE_URL>/api/tdm/statistics/next/run?cronExpression=0%200%208%20*%20*%20%3F"
```

Verified live: `{"nextRun": "Thu Oct 01 12:00:00 SAMT 2026"}` — wrapped in an object, unlike
[tdm-manage-cleanup-config](../tdm-manage-cleanup-config/SKILL.md)'s `next/run`, which returns the same kind of
value as a bare string. Don't assume the two shapes match.

## Common pitfalls

- Deleting with `DELETE`: both delete operations are `PUT`.
- Using `enabled` for the users-occupation schedule: that schedule's flag is named `scheduled`, not `enabled` —
  the general schedule uses `enabled`. Mixing them up silently sets the wrong field (Jackson ignores unknown
  properties) rather than failing.
- Treating an empty-fields response from GET as "not found": both GET endpoints return a disabled placeholder
  schedule for a project with none, not an error.
