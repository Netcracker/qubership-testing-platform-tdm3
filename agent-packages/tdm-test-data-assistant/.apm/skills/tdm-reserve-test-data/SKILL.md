---
name: tdm-reserve-test-data
description: Use when the user wants to reserve (occupy) test data in TDM3 for a test execution, so other tests don't pick up the same row, or wants to release a reservation made earlier. Calls the TDM3 REST API directly.
user-invocable: false
---

# Reserve or release test data in TDM3

TDM3's reservation endpoints search and occupy a row in one call — there's no separate "find, then reserve" round
trip. Call `POST /api/tdm/rest/occupy-records` (one response column) or `POST /api/tdm/rest/occupy-records-full-row`
(several response columns) to reserve; call `POST /api/tdm/rest/release-records` or `.../release-records/bulk` to
release.

Use these endpoints when the user describes the rows by selection conditions and their `ROW_ID` values aren't known
yet, and do not look the IDs up first. When the `ROW_ID` values are already known, because the user supplied them or an
earlier response returned them, reserve the rows by ID with
[tdm-occupy-test-data-rows-by-id](../tdm-occupy-test-data-rows-by-id/SKILL.md).

## Required inputs

Same context resolution as [tdm-find-test-data](../tdm-find-test-data/SKILL.md): run
[tdm-select-context](../tdm-select-context/SKILL.md) first if `PROJECT_NAME`, `ENV_NAME_DEFAULT`, or
`SYSTEM_NAME_DEFAULT` aren't resolved yet, and read them from
[the config file](../../../README.md#configuration) rather than asking the user directly. Beyond that:

- `title-table` — the table to reserve or release rows in.
  Decide which table it is as described in [tdm-list-tables](../tdm-list-tables/SKILL.md#which-table-the-user-means).
- The same column filters used to find the row (see tdm-find-test-data): column name, comparison, value,
  case-sensitivity. For reserving, an empty `search-row-parameters-set` is valid — verified live — and occupies
  the first available row unconditionally, for a user who just wants "any" row. Don't do this for releasing (see
  Common pitfalls): an empty filter there is a way to release the wrong row, not a documented shortcut.
- The column (or columns) whose value identifies the reserved row to the caller.
- For reserving, `occupiedBy`: the name the reservation is recorded under. Both reserve endpoints require it as a query
  parameter, and a request without it fails with HTTP 400. Take it from the operating-system account and percent-encode
  it, as [tdm-occupy-test-data-rows-by-id](../tdm-occupy-test-data-rows-by-id/SKILL.md#occupy) describes.

## Reserve

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/rest/occupy-records?occupiedBy=<occupiedBy>' \
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

TDM3 occupies the first available row matching the filters, records it as occupied by `occupiedBy`, counts it in the
occupation statistics, and returns
`[{ "type": "SUCCESS", "content": "PREPAID_00123", "contentObject": null, "link": "..." }]` — `content` carries the
result (`occupy-records-full-row` additionally fills `contentObject` with several columns as an object, the same way
`/get-records` does). `type: "ERROR"` with `content: "No test data available for requested criteria!"` means no
available row matched — the pool is exhausted and new test data needs to be generated, not that the request failed
in transit (HTTP is 200 either way). Report that distinction to the user in plain language rather than surfacing the
raw JSON.

## Reserve every row matching a filter

There's no bulk "occupy all matches" operation — `occupy-records` always occupies the first available row of each
individual `occupy-row-requests` entry. To reserve every row matching one filter, **repeat the same
`search-row-parameters-set` once per row wanted, as separate entries in the same `occupy-row-requests` array**:

```json
{
  "occupy-row-requests": [
    { "search-row-parameters-set": [{ "name-column": "Partner", "search-criterion": "Equals", "search-value": "HITECH", "caseSensitive": false }], "name-column-response": "SIM" },
    { "search-row-parameters-set": [{ "name-column": "Partner", "search-criterion": "Equals", "search-value": "HITECH", "caseSensitive": false }], "name-column-response": "SIM" },
    { "search-row-parameters-set": [{ "name-column": "Partner", "search-criterion": "Equals", "search-value": "HITECH", "caseSensitive": false }], "name-column-response": "SIM" }
  ]
}
```

Verified live: the entries are evaluated in order against the table's live state, not a shared snapshot taken
before the call, so the second entry never re-occupies the row the first one just took — three identical entries
against three matching rows returned three distinct SIMs, one per entry. This follows directly from "each
`occupy-row-requests` entry occupies the first available row matching it": once entry 1 occupies a row, that row
is no longer available for entry 2 to match.

This means the caller has to know (or over-estimate) how many rows match before sending the request — there's no
"however many there are" option. Use the lookup only to count the matching rows, not to collect their `ROW_ID`
values for reserving by ID. Find the count first with
[tdm-browse-test-data-table](../tdm-browse-test-data-table/SKILL.md)'s paged read (its `records` field) or
[tdm-find-test-data](../tdm-find-test-data/SKILL.md)'s distinct-values/row tools, then send that many entries.
Sending one entry too many is harmless: the extra entry comes back `type: "ERROR"`,
`content: "No test data available for requested criteria!"`, the same pool-exhausted result documented above, and
doesn't affect the entries that did match.

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
- Reading a request that got no answer as "no data": when `curl` shows `HTTP 000` (exit code 7), TDM3 isn't running
  at `TDM3_BASE_URL`. Tell the user the server is unreachable and that nothing was reserved. Don't try another address.
- Retrying after an unknown column: a filter or response column the table doesn't have makes the search fail with a
  raw HTTP 500 (`TDM-2005`, `bad SQL grammar` in the trace) instead of a `ResponseMessage`, verified live. Column names
  match exactly, including case: `status` doesn't match `Status`. Nothing is reserved. Name the rejected column, show
  the table's column names (the header of [tdm-browse-test-data-table](../tdm-browse-test-data-table/SKILL.md)'s paged
  read), and let the user choose; don't resend with a corrected name of your own.
