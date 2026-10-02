---
name: tdm-configure-column-links
description: Use when the user wants a TDM3 table column's values shown as clickable links in the UI, or wants to preview what a link would look like before saving that setup. Calls the TDM3 REST API directly.
---

# Turn a TDM3 column into links

Two endpoints: preview the link TDM3 would build for a column, then save that setup so the TDM3 UI renders the
column's values as links.

## Required inputs

Run [tdm-select-context](../tdm-select-context/SKILL.md) first for `PROJECT_ID` and, where used, `SYSTEM_ID_DEFAULT`.

- `tableName` — the database table name (from [tdm-list-tables](../tdm-list-tables/SKILL.md)).
- `columnName` — the column to link.
- Either `pickUpFullLinkFromTableCell: true` (each row's own cell value is already a full URL) or an `endpoint` to
  join with the target system's own HTTP connection URL.

## Preview

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/link/preview?projectId=<PROJECT_ID>&systemId=<SYSTEM_ID_DEFAULT>&columnName=<column>&tableName=<tableName>&pickUpFullLinkFromTableCell=false' \
  -H 'Content-Type: text/plain' \
  -d '/customers/{value}'
```

Returns the built link as a JSON-encoded string (for example `"http://host/customers/CUST_001"`), read from the
column's first row's value when `pickUpFullLinkFromTableCell` is set, or from the system's connection `url` joined
with `endpoint` otherwise. Nothing is saved by this call.

## Save the link setup

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/link/setup?isAll=false&projectId=<PROJECT_ID>&systemId=<SYSTEM_ID_DEFAULT>&tableName=<tableName>&columnName=<column>&validateUnoccupiedResources=false&pickUpFullLinkFromTableCell=false' \
  -H 'Content-Type: text/plain' \
  -d '/customers/{value}'
```

Set `isAll: true` to apply the same link setup to every table sharing this title across every environment of the
project, instead of just this one table. Returns no body on success.

## Common pitfalls

- Sending `endpoint` as a JSON body: both endpoints take it as a raw request body string (`text/plain`), not a
  JSON field.
- Setting `isAll: true` without confirming the user wants every environment's copy of the table changed, not just
  this one.
