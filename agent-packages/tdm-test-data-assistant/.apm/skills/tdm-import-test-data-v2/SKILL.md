---
name: tdm-import-test-data-v2
description: Use when the user wants to import TDM3 test data with a SQL query and prefers (or the calling agent requires) a JSON request body over query parameters. Calls the TDM3 REST API directly.
user-invocable: false
---

# Import rows with a SQL query (body-based)

`test-data-controller-v2` has one operation: `POST /api/tdm/v2/import/sql` does exactly what
[tdm-import-test-data](../tdm-import-test-data/SKILL.md)'s `POST /api/tdm/import/sql` does, with the same parameters
carried in a JSON body instead of the query string. Prefer this version whenever building a query string is more
awkward than a JSON body — for instance, a multi-line SQL query.

## Request

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/v2/import/sql' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectId": "<PROJECT_ID>",
    "environmentsIds": ["<ENV_ID_DEFAULT>"],
    "systemName": "<system name>",
    "tableTitle": "<table title>",
    "query": "SELECT * FROM customers",
    "queryTimeout": 1800
  }'
```

Same fields, same semantics, same `List<ImportTestDataStatistic>` response (one entry per environment, a missing
environment or system reported in that entry's `error` rather than thrown) as
[tdm-import-test-data](../tdm-import-test-data/SKILL.md)'s SQL import — see that skill for the response shape and
pitfalls. The only difference is where the parameters go.

## Common pitfalls

- Mixing the two forms: this endpoint doesn't accept query parameters, and `/api/tdm/import/sql` (v1) doesn't
  accept this JSON body.
