---
name: tdm-occupy-test-data-rows-by-id
description: Use to occupy, release, or delete specific TDM3 rows the caller has already identified by ROW_ID (for example, from tdm-browse-test-data-table), rather than by search criteria. Calls the TDM3 REST API directly.
---

# Occupy, release, or delete TDM3 rows by ID

Use this skill to occupy rows only when the `ROW_ID` values are already known, for example the user supplied them or
an earlier response returned them. When the user describes the rows by selection conditions, use
[tdm-reserve-test-data](../tdm-reserve-test-data/SKILL.md) instead: it finds and reserves in one call, so never run a
search just to obtain `ROW_ID` values for this skill. Releasing and deleting rows by ID also belong here, and
deleting is the one operation `atp-action-controller` has no equivalent for, so a lookup in
[tdm-browse-test-data-table](../tdm-browse-test-data-table/SKILL.md) to find the rows to delete is expected.

## Required inputs

- `tableName` — the database table name (from [tdm-list-tables](../tdm-list-tables/SKILL.md)), not the title.
- One or more `ROW_ID` values (UUIDs).
- For occupying, `occupiedBy`: the name the reservation is recorded under.

## Occupy

```bash
curl -X PUT '<TDM3_BASE_URL>/api/tdm/occupy?tableName=<tableName>&occupiedBy=<occupiedBy>' \
  -H 'Content-Type: application/json' \
  -d '["179e6bdc-18d5-4cb4-b816-945302fb6e27"]'
```

Set `occupiedBy` to the login name of the operating-system account the agent runs under, such as the value of
`USERNAME` on Windows or `USER` on Linux and macOS. If the system reports no such name, use the IP address of the
computer the agent runs on.

## Release

```bash
curl -X PUT '<TDM3_BASE_URL>/api/tdm/release?tableName=<tableName>' \
  -H 'Content-Type: application/json' \
  -d '["179e6bdc-18d5-4cb4-b816-945302fb6e27"]'
```

## Delete rows (permanent — confirm with the user first)

```bash
curl -X PUT '<TDM3_BASE_URL>/api/tdm/delete/rows?tableName=<tableName>' \
  -H 'Content-Type: application/json' \
  -d '["179e6bdc-18d5-4cb4-b816-945302fb6e27"]'
```

Unlike `atp-action-controller`, there's no search-and-delete here — only by ID. This removes the rows entirely,
not just their reservation; confirm with the user before calling it, the same as
[tdm-cleanup-test-data-table](../tdm-cleanup-test-data-table/SKILL.md).

## Response

All three return HTTP 200 with an empty body — verified live. There's no `ResponseMessage`, no per-row result, and
no error reported for an ID that doesn't exist in the table; a 200 confirms the request was accepted, not that
every ID matched a row.

## Common pitfalls

- Expecting a `ResponseMessage` or a per-row result: these endpoints are `void`. Check the HTTP status only.
- Passing `title-table` instead of `tableName`: this controller addresses tables by database name throughout,
  unlike `atp-action-controller`.
- Using `/delete/rows` when the intent was "free this reservation": that's `/release` — `/delete/rows` removes the
  row permanently.
