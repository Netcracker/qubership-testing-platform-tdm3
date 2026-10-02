---
name: tdm-browse-test-data-table
description: Use to page through, sort, or inspect a TDM3 table's rows (including already-occupied ones), or to list a single column's distinct values, when tdm-find-test-data's single-row lookup isn't enough. Calls the TDM3 REST API directly.
---

# Browse a TDM3 table

Prefer [tdm-find-test-data](../tdm-find-test-data/SKILL.md) for "find one row matching these criteria" — it returns
clean `{column: value}` data. Reach for this skill only when the task needs something that controller can't do:
paging through many rows, sorting, reading **occupied** rows, or a single row/column lookup by database table name
rather than by title.

## Required inputs

This controller addresses tables by **database table name** (`TDM_<hash>`, from
[tdm-list-tables](../tdm-list-tables/SKILL.md)), not by title-table + project/env/system like
`atp-action-controller`. Run [tdm-list-tables](../tdm-list-tables/SKILL.md) first to resolve the title the user
gave to a `tableName`.

## Page through rows, with sorting and filters

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/table' \
  -H 'Content-Type: application/json' \
  -d '{
    "tableName": "TDM_f3f06a83f35e4f058afefba71d71cec3",
    "offset": 0,
    "limit": 10,
    "occupied": false,
    "filters": [{ "name-column": "CUSTOMER_TYPE", "search-criterion": "Equals", "search-values": ["PREPAID"] }],
    "dataTableOrder": { "columnName": "CUSTOMER_ID", "orderType": "ASC" }
  }'
```

`filters` uses `search-values` (plural — an array whose first element is read, the rest ignored), not
`search-value` like the `atp-action-controller` filters. Set `"occupied": true` to read occupied rows instead of
available ones — `tdm-find-test-data` can never do this.

**The response is a UI grid shape, verified live, and it does not match the OpenAPI schema's own description**
(which says `header`/`body` sit next to `records`/`name`; they're actually nested one level deeper, under `data`):

```json
{
  "data": {
    "header": { "rows": [{ "columns": [
      { "type": "checkbox", "value": false },
      { "value": "CUSTOMER_ID", "sort": true, "filter": true, "filterType": "list", "contentModel": { "id": "CUSTOMER_ID" } },
      { "value": "CUSTOMER_TYPE", "sort": true, "filter": true, "filterType": "list", "contentModel": { "id": "CUSTOMER_TYPE" } }
    ] }] },
    "body": { "rows": [{
      "id": "179e6bdc-18d5-4cb4-b816-945302fb6e27",
      "columns": [
        { "type": "checkbox", "value": false },
        { "value": "this.simpleCellContent", "type": "content", "contentModel": { "value": "CUST_A" } },
        { "value": "this.simpleCellContent", "type": "content", "contentModel": { "value": "PREPAID" } }
      ]
    }] }
  },
  "records": 2,
  "name": "TDM_f3f06a83f35e4f058afefba71d71cec3",
  "query": null,
  "updateByQuery": null
}
```

To read this: `data.body.rows[i].id` is the row's `ROW_ID` (what [tdm-occupy-test-data-rows-by-id](../tdm-occupy-test-data-rows-by-id/SKILL.md)
needs). Each row's `columns` array lines up positionally with `data.header.rows[0].columns` — index 0 is always
the checkbox column, so column *N* of a data row (N ≥ 1) is the value of `data.header.rows[0].columns[N].value`
(the column name), and the cell's value is at `columns[N].contentModel.value`. This is a UI-grid format, not a
data API — do the column line-up once, then work with the resulting `{column: value}` map like any other skill's
result.

## Find one row by a single column, by table title (not database name)

```bash
curl "<TDM3_BASE_URL>/api/tdm/table/row?projectId=<PROJECT_ID>&systemId=<SYSTEM_ID_DEFAULT>&tableTitle=<title>&columnName=CUSTOMER_ID&searchValue=CUST_A&occupied=false"
```

**Always send the current `systemId`.** Resolve the system with [tdm-select-context](../tdm-select-context/SKILL.md)
first, as for every other request, and pass `SYSTEM_ID_DEFAULT`. Omit the parameter only when the user explicitly asks
for it in this request, for example "search without a system"; the omission applies to that request only.

Verified live — returns a flat map, not the grid shape, and includes bookkeeping columns
`get-record`/`get-records` don't expose:

```json
{
  "ROW_ID": "179e6bdc-18d5-4cb4-b816-945302fb6e27",
  "SELECTED": false,
  "OCCUPIED_BY": "",
  "OCCUPIED_DATE": "2026-09-30 18:04:56",
  "CREATED_WHEN": "2026-09-30 18:04:27",
  "CUSTOMER_ID": "CUST_A",
  "CUSTOMER_TYPE": "PREPAID"
}
```

Case-sensitive exact match only (no `Contains`/`startWith`/`From`/`To`) — for anything less exact, use
`tdm-find-test-data` or the paged read above. Unlike the rest of this controller, this one endpoint takes
`projectId` + `tableTitle` rather than `tableName`.

## List a column's distinct values

```bash
curl "<TDM3_BASE_URL>/api/tdm/table/column/distinct/values?tableName=<tableName>&columnName=CUSTOMER_TYPE&occupied=false"
```

`{"items": ["POSTPAID", "PREPAID"]}` — verified live. `GET /api/tdm/data/available/recalculate` answers the same
question across every table of a system that has the given column, in one call
(`systemId`, `environmentId`, `columnName` — no `tableName`), returning one `{tableName, tableTitle, values}` entry
per table.

## Common pitfalls

- Trusting the OpenAPI description's flatter shape for the paged-read response: verified above, `header`/`body`
  are under `data`, not siblings of `records`.
- Using `search-value` (singular) in `filters`: this endpoint's filter object uses `search-values` (an array).
- Addressing this controller's endpoints by `title-table` the way `atp-action-controller` does: most of them need
  the database `tableName` instead. `/table/row` is the one exception in this skill, taking `projectId` +
  `tableTitle`.
- Omitting `/table/row`'s `systemId` on your own initiative: the lookup then takes the first table with that title in
  any system, which may not be the current one.
- Reaching for `/table/row` at all when `atp-action-controller`'s `get-record` fits: it does for most single-column,
  single-filter, available-rows lookups — default to that skill first (see its own opening line) rather than this
  one out of habit.
