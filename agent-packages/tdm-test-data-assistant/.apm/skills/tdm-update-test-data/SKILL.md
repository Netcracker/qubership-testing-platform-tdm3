---
name: tdm-update-test-data
description: Use when the user wants to change column values on existing TDM3 rows, or append a note to a column without losing its current value. Calls the TDM3 REST API directly.
---

# Update test data in TDM3

Call `POST /api/tdm/rest/update-records` to replace column values on every matching row, or
`POST /api/tdm/rest/add-info-to-row` to append a value on a new line instead of replacing. Both act on every row
that matches the filters, not just the first one — unlike `get-record` and `occupy-records`.

## Required inputs

Run [tdm-select-context](../tdm-select-context/SKILL.md) first if `PROJECT_NAME`, `ENV_NAME_DEFAULT`, or
`SYSTEM_NAME_DEFAULT` aren't resolved yet — read them from
[the config file](../../../README.md#configuration) rather than asking the user directly. Beyond that:

- `title-table` — the table to update.
  Decide which table it is as described in [tdm-list-tables](../tdm-list-tables/SKILL.md#which-table-the-user-means).
- The column filters that select the rows to change (see [tdm-find-test-data](../tdm-find-test-data/SKILL.md) for
  the filter shape).
- The column-to-value map to write or append.

## Replace column values

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/rest/update-records' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<project>",
    "envName": "<env>",
    "systemName": "<system>",
    "title-table": "<table title>",
    "update-row-requests": [
      {
        "search-row-parameters-set": [
          { "name-column": "CUSTOMER_ID", "search-criterion": "Equals", "search-value": "CUST_001", "caseSensitive": true }
        ],
        "record-with-data-for-update": { "CUSTOMER_TYPE": "POSTPAID" }
      }
    ]
  }'
```

Every column in `record-with-data-for-update` replaces the matching row's current value.

## Append to a column

Same request shape, posted to `POST /api/tdm/rest/add-info-to-row` with `"add-info-to-row-requests"` in place of
`"update-row-requests"`. Each value in `record-with-data-for-update` is appended on a new line to the column's
current value — verified: appending `"second"` to a column already holding `"first"` leaves it as `"first\r\nsecond"`.
A column with no current value just gets the new one, with no leading empty line.

## Response

```json
[{ "type": "SUCCESS", "content": "\"1\" rows were successfully updated", "contentObject": null, "link": "" }]
```

One entry per request in `update-row-requests` / `add-info-to-row-requests`, `content` holding the count of rows
that matched and were changed (as a quoted number, verified — not a bug to work around, just how the string reads).
`type: "ERROR"` means the row request itself failed (for example, the table doesn't exist); it does not distinguish
"no rows matched" from "some rows matched" — a request whose filters match nothing still reports success with a
count of `"0"`.

## Common pitfalls

- Expecting `update-records` to touch only the first match: it changes every row the filters match. Add a
  uniquely-identifying filter (a primary key, or the value `occupy-records` returned) when only one row should
  change.
- Treating a `"0"`-row success as an error: check the count in `content`, not just `type`, when the caller needs to
  know whether anything actually matched.
