---
name: tdm-find-test-data
description: Use when the user asks to find, search, or look up existing test data in TDM3 by column criteria (for example, "find a Customer of type X with an active SIM card"). Calls the TDM3 REST API directly and returns matching rows without reserving them.
---

# Find test data in TDM3

Call `POST /api/tdm/rest/get-record` (one response column) or `POST /api/tdm/rest/get-records` (several response
columns) on the TDM3 server. Both search only the available rows of the target table — a row already occupied by
another test never matches — and neither endpoint reserves the row it finds.

## Required inputs

Run [tdm-select-context](../tdm-select-context/SKILL.md) first if `PROJECT_NAME`, `ENV_NAME_DEFAULT`, or
`SYSTEM_NAME_DEFAULT` aren't resolved yet — this skill reads `TDM3_BASE_URL`, `projectName`, `envName`, and
`systemName` from [the config file](../../../README.md#configuration) rather than resolving them itself. Beyond
that, collect:

- `title-table` — the table title to search.
- Zero or more column filters: for each, a column name, a comparison (`Contains`, `startWith`, `Equals`, `From`, or
  `To`, case-insensitive), the value to compare against, and whether the comparison is case-sensitive. An empty
  `search-row-parameters-set` is valid — verified live — and matches the first available row with no condition, for
  a user who wants "any" row rather than one matching specific criteria.
- The column (or columns) whose value the user wants back.

`envName` and `systemName` are optional at the TDM3 API level (it falls back to the first table with this title in
the project when either is missing), but this package always sends the resolved defaults rather than omitting
them, so a search never silently drifts to the wrong environment.

## Request

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/rest/get-record' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<project>",
    "envName": "<env>",
    "systemName": "<system>",
    "title-table": "<table title>",
    "get-row-requests": [
      {
        "search-row-parameters-set": [
          { "name-column": "CUSTOMER_TYPE", "search-criterion": "Equals", "search-value": "PREPAID", "caseSensitive": false },
          { "name-column": "SIM_STATUS", "search-criterion": "Equals", "search-value": "ACTIVE", "caseSensitive": false }
        ],
        "name-column-response": "CUSTOMER_ID"
      }
    ]
  }'
```

Every filter in `search-row-parameters-set` must match (they are ANDed); `"search-row-parameters-set": []` matches
the first available row unconditionally. Use `/get-records` with
`"response-column-names": ["COL_A", "COL_B"]` instead of `"name-column-response"` when the caller needs more than one
column back; the rest of the request body is the same. `get-row-requests` accepts several entries, so one call can run
several independent searches against the same table.

## Response

The response is always HTTP 200, with one entry per `get-row-requests` item:

```json
[{ "type": "SUCCESS", "content": "PREPAID_00123", "contentObject": null, "link": "" }]
```

`type` is `SUCCESS` or `ERROR` — check it per item; `ERROR` means no available row matched that item's filters (for
example, `content` reads `"Table with title \"X\" was not found!"`), not a transport failure. `content` carries the
result as a string either way. `/get-records` additionally fills `contentObject` with the same columns as a JSON
object (`content` still holds its JSON-stringified form too), which is the more convenient one to read for several
columns. `link` is a URL to the table in the TDM3 UI when TDM3 has one to give, otherwise an empty string — don't
rely on it being set. Present the results to the user as a table (one row per search, showing the returned column
value(s)) so they can pick which record to carry into a follow-up reservation.

## Common pitfalls

- Reading `response.message`: the field is `content` (a string) and, for `/get-records`, `contentObject` (the same
  data as an object). There is no `message` field.
- Passing `title-table` as `titleTable`: the field is JSON key `title-table` (hyphenated), not camelCase.
- Expecting occupied rows to show up: this endpoint never searches them. To search occupied rows too, use TDM3's
  UI-facing `POST /api/tdm/table` endpoint with `"occupied": true` instead.
- A trailing timestamp on `envName` (for example, `" 2024-05-01T10:15:30"`) is ignored by TDM3, so it doesn't need to
  be stripped before sending the request.
