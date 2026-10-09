---
name: tdm-table-utilities
description: Use to check whether a TDM3 table warns when it runs out of unoccupied rows, or to see what a query with ${...} table-value macros resolves to before running it elsewhere. Calls the TDM3 REST API directly.
user-invocable: false
---

# TDM3 table utilities

Two small, unrelated reads, grouped here because neither is large enough to earn its own skill.

## Get a table's unoccupied-row validation flag

```bash
curl '<TDM3_BASE_URL>/api/tdm/validation/unoccupied?tableName=<tableName>'
```

Verified live: `{"tableName": "TDM_...", "unoccupiedValidation": false}`. This flag is read-only through this
controller — there's no endpoint in this package to set it.

## Substitute table values into a query

```bash
curl -X PUT '<TDM3_BASE_URL>/api/tdm/evaluate/query?tableName=<tableName>' \
  -H 'Content-Type: text/plain' \
  -d 'SELECT * FROM orders WHERE customer_id IN (${CUSTOMER_ID})'
```

Replaces each `${columnName}` macro with the comma-joined values of that column across the table's rows, and
returns `{"query": "<resolved query>"}`. The query is a raw text body, not JSON.

**Verified live: an invalid or unresolvable query returns a raw H2 stack trace as HTTP 500, not a graceful error.**
Recognize a `500` with `org.h2.jdbc.JdbcSQLSyntaxErrorException` (or similar) in the body as "the query is
malformed," and tell the user in plain language rather than surfacing the trace.

## Common pitfalls

- Sending the query to `/evaluate/query` as `{"query": "..."}` JSON: it's a plain-text body.
- Assuming a `500` from `/evaluate/query` means TDM3 itself is broken: it usually means the query or the `${...}`
  macro doesn't resolve to valid SQL for this table.
