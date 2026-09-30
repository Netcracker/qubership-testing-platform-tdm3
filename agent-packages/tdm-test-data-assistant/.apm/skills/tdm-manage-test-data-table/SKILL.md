---
name: tdm-manage-test-data-table
description: Use to drop a TDM3 table entirely, delete all its rows while keeping the table, or rename its title. Calls the TDM3 REST API directly. Dropping and truncating are permanent — confirm with the user first.
---

# Manage a TDM3 table's lifecycle

Table-level operations, addressed by database table name (from
[tdm-list-tables](../tdm-list-tables/SKILL.md)). For deleting specific rows instead of a whole table, see
[tdm-occupy-test-data-rows-by-id](../tdm-occupy-test-data-rows-by-id/SKILL.md)'s delete, or
[tdm-cleanup-test-data-table](../tdm-cleanup-test-data-table/SKILL.md) for the `atp-action-controller` equivalent
of the truncate below.

## Drop a table (confirm with the user first)

```bash
curl -X DELETE '<TDM3_BASE_URL>/api/tdm/table?tableName=<tableName>'
```

Verified live: HTTP 200, empty body. Drops the database table, removes its catalog entry, and stops any scheduled
refresh — permanent, and there's no confirmation step in the API itself, so get one from the user before calling
this.

## Delete every row, keeping the table (confirm with the user first)

```bash
curl -X DELETE '<TDM3_BASE_URL>/api/tdm/truncate/table?tableName=<tableName>&projectId=<PROJECT_ID>&systemId=<SYSTEM_ID_DEFAULT>'
```

Verified live:

```json
{ "type": "SUCCESS", "content": "Data has been cleaned in table with tableName = \"TDM_...\".", "contentObject": null, "link": "" }
```

`systemId` is optional; `projectId` is required. Returns HTTP 400 when `projectId` (or `systemId`, if given) has no
table with this name — different addressing and a different response shape from
[tdm-cleanup-test-data-table](../tdm-cleanup-test-data-table/SKILL.md)'s `truncate-table`, which takes a
`title-table` and returns a list.

## Rename a table's title

```bash
curl -X PUT '<TDM3_BASE_URL>/api/tdm/change/title' \
  -H 'Content-Type: application/json' \
  -d '{ "tableName": "<tableName>", "tableTitle": "<new title>" }'
```

Returns a bare `true`/`false` (whether the catalog entry was found and renamed) — not a `ResponseMessage`. Also
renames the table in its occupation statistics when the rename succeeds.

## Common pitfalls

- Confusing this skill's truncate (query params, `projectId` required, `ResponseMessage` response) with
  [tdm-cleanup-test-data-table](../tdm-cleanup-test-data-table/SKILL.md)'s (JSON body, `title-table` addressing,
  list response) — they hit different controllers with different contracts, not just different paths.
- Dropping or truncating without confirming: neither can be undone.
- Reading `/change/title`'s response as JSON with a `type` field: it's a plain boolean.
