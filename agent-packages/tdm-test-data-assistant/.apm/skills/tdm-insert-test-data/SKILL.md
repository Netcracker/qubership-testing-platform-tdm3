---
name: tdm-insert-test-data
description: Use when the user wants to add new rows of test data to TDM3, or wants to create a new test data table by inserting its first rows. Calls the TDM3 REST API directly.
---

# Insert test data into TDM3

Call `POST /api/tdm/rest/insert-records`. It creates the table when no table with this title exists yet, and
inserts into it otherwise — there's no separate "create table" step.

## Required inputs

Run [tdm-select-context](../tdm-select-context/SKILL.md) first if `PROJECT_NAME`, `ENV_NAME_DEFAULT`, or
`SYSTEM_NAME_DEFAULT` aren't resolved yet — read them from
[the config file](../../../README.md#configuration) rather than asking the user directly. Beyond that:

- `title-table` — the table to insert into (or create).
- One or more rows, each a map of column name to value.

## Request

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/rest/insert-records' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<project>",
    "envName": "<env>",
    "systemName": "<system>",
    "title-table": "<table title>",
    "insert-records": [
      { "CUSTOMER_ID": "CUST_001", "CUSTOMER_TYPE": "PREPAID", "SIM_STATUS": "ACTIVE" }
    ]
  }'
```

## Response

Unlike every other operation in this controller, the response is a single object, not an array:

```json
{
  "type": "SUCCESS",
  "content": "A new test data table has been created. Test data was inserted.",
  "contentObject": null,
  "link": "http://localhost:8080/project/<projectId>/<tableName>"
}
```

`content` reads differently depending on whether the table already existed; both cases are `SUCCESS`. Check `type`
for `ERROR` the same way as the other operations.

## Common pitfalls

- Expecting an array: `insert-records` inserts every row of the request in one call and returns one
  `ResponseMessage`, not a list with one entry per row (contrast `get-record`, `occupy-records`, and the rest of
  this controller, which return a list with one entry per row request).
- Omitting `envName`/`systemName`: they're optional here, but a table created without them has no environment or
  system association in TDM3's catalog. [tdm-refresh-test-data-table](../tdm-refresh-test-data-table/SKILL.md)
  fails against such a table with an internal error instead of a clear message, because it has no system to refresh
  against. Pass the resolved `envName`/`systemName` on every insert, even though the API allows leaving them out.
