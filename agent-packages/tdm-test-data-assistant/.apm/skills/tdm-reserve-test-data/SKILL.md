---
name: tdm-reserve-test-data
description: Use when the user wants to reserve (occupy) test data in TDM3 for a test execution, so other tests don't pick up the same row, or wants to release a reservation made earlier. Calls the TDM3 REST API directly.
---

# Reserve or release test data in TDM3

TDM3's reservation endpoints search and occupy a row in one call — there's no separate "find, then reserve" round
trip. Call `POST /api/tdm/rest/occupy-records` (one response column) or `POST /api/tdm/rest/occupy-records-full-row`
(several response columns) to reserve; call `POST /api/tdm/rest/release-records` or `.../release-records/bulk` to
release.

## Required inputs

Same context resolution as [tdm-find-test-data](../tdm-find-test-data/SKILL.md): run
[tdm-select-context](../tdm-select-context/SKILL.md) first if `PROJECT_NAME`, `ENV_NAME_DEFAULT`, or
`SYSTEM_NAME_DEFAULT` aren't resolved yet, and read them from
[the config file](../../../README.md#configuration) rather than asking the user directly. Beyond that:

- `title-table` — the table to reserve or release rows in.
- The same column filters used to find the row (see tdm-find-test-data): column name, comparison, value,
  case-sensitivity. For reserving, an empty `search-row-parameters-set` is valid — verified live — and occupies
  the first available row unconditionally, for a user who just wants "any" row. Don't do this for releasing (see
  Common pitfalls): an empty filter there is a way to release the wrong row, not a documented shortcut.
- The column (or columns) whose value identifies the reserved row to the caller.

## Reserve

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/rest/occupy-records' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<project>",
    "envName": "<env>",
    "systemName": "<system>",
    "title-table": "<table title>",
    "occupy-row-requests": [
      {
        "search-row-parameters-set": [
          { "name-column": "CUSTOMER_TYPE", "search-criterion": "Equals", "search-value": "PREPAID", "caseSensitive": false }
        ],
        "name-column-response": "CUSTOMER_ID"
      }
    ]
  }'
```

Set `"search-row-parameters-set": []` to occupy the first available row with no condition — verified live, this is
how to satisfy "reserve any available row" without inventing a filter that happens to match everything.

TDM3 occupies the first available row matching the filters, as `ATP_User`, and returns
`[{ "type": "SUCCESS", "content": "PREPAID_00123", "contentObject": null, "link": "..." }]` — `content` carries the
result (`occupy-records-full-row` additionally fills `contentObject` with several columns as an object, the same way
`/get-records` does). `type: "ERROR"` with `content: "No test data available for requested criteria!"` means no
available row matched — the pool is exhausted and new test data needs to be generated, not that the request failed
in transit (HTTP is 200 either way). Report that distinction to the user in plain language rather than surfacing the
raw JSON.

## Release

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/rest/release-records' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<project>",
    "envName": "<env>",
    "systemName": "<system>",
    "title-table": "<table title>",
    "release-row-requests": [
      {
        "search-row-parameters-set": [
          { "name-column": "CUSTOMER_ID", "search-criterion": "Equals", "search-value": "PREPAID_00123", "caseSensitive": true }
        ],
        "name-column-response": "CUSTOMER_ID"
      }
    ]
  }'
```

`release-records` fails a row request when more than one occupied row matches its filters, so filter on a unique
column (a primary key or a value just returned by `occupy-records`). To release every occupied row of a table at
once, call `POST /api/tdm/rest/release-records/bulk` with just the addressing fields — no `release-row-requests`
needed.

## Common pitfalls

- Reading `response.message`: the field is `content` (a string) and, for `occupy-records-full-row`, `contentObject`
  (the same data as an object). There is no `message` field.
- Filtering `release-records` on a non-unique column: it throws rather than picking one of the matches. Use the
  identifier `occupy-records` returned.
- Releasing with an empty `search-row-parameters-set`: not verified, and `release-records` already throws on more
  than one match for a real filter — an empty one is likely to match every occupied row. Reserve for occupy only,
  never for release.
- Treating `type: "ERROR"` as a network error: it means the reservation pool has no available row left, which the
  user needs to know so they can trigger test data generation instead of retrying the same request.
