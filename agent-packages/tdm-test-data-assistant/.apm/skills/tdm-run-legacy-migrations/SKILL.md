---
name: tdm-run-legacy-migrations
description: Use only when the user explicitly names one of TDM3's one-time data migration endpoints and asks to run it. Never call these as part of routine test data work — two of the five are confirmed broken on TDM3's H2 database.
---

# TDM3 legacy migration endpoints

Five parameterless `GET` endpoints, each audited in the code only as `"Old update."` — one-time migrations for
data created before a column or a statistics table existed, left over from TDM3's earlier PostgreSQL-based
predecessor (see the repository's `tdm3-legacy-leftovers` note). None of them take input or return a body on
success. **Don't call any of these as part of ordinary test data work** — they exist for a maintainer to run once,
by name, not for an agent to reach for.

## Confirmed broken on H2 — do not call

Both verified live, returning HTTP 500 with `org.h2.jdbc.JdbcSQLSyntaxErrorException`: the underlying code issues
`LOCK TABLE ... IN SHARE ROW EXCLUSIVE MODE`, PostgreSQL syntax H2 doesn't support.

- `GET /api/tdm/fix/occupied/by/column` — was meant to add a missing `OCCUPIED_BY` column.
- `GET /api/tdm/alter/created/when` — was meant to add a missing `CREATED_WHEN` column.

If the user needs either migration's effect, that needs a code fix (or a manual H2 `ALTER TABLE`), not this
endpoint.

## Confirmed working on H2

Verified live: each returns HTTP 200 with an empty body.

```bash
curl '<TDM3_BASE_URL>/api/tdm/fill/envId'
curl '<TDM3_BASE_URL>/api/tdm/alter/occupy/statistic'
curl '<TDM3_BASE_URL>/api/tdm/resolve/discrepancy/testDataFlagsTableAndTestDataTableCatalog'
```

- `fill/envId` — sets the environment ID in catalog entries that have a system but no environment recorded, and
  deletes tables of a project whose loading fails with "not found."
- `alter/occupy/statistic` — adds an occupation-statistics record for every occupied row that predates the
  statistics table.
- `resolve/discrepancy/testDataFlagsTableAndTestDataTableCatalog` — adds default validation flags for catalog
  tables missing one, and deletes flags for tables no longer in the catalog.

Even these three: confirm with the user before running one — each rewrites data across every table it touches, and
"it returned 200" doesn't mean it changed anything useful for the user's actual problem.

## Common pitfalls

- Calling `fix/occupied/by/column` or `alter/created/when` expecting a graceful failure: both take down the request
  with a raw stack trace, and neither is fixable by changing how the request is sent.
- Reaching for any of these five to solve an ordinary "table is missing a column" problem: they're narrow one-time
  migrations for specific legacy states, not general-purpose schema repair.
