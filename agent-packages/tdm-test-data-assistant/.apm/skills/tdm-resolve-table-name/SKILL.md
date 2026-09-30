---
name: tdm-resolve-table-name
description: Use when the user wants the underlying H2 database table name behind a TDM3 table title — for example, to query it directly through the H2 console. Calls the TDM3 REST API directly.
---

# Resolve a TDM3 table's database name

Call `POST /api/tdm/rest/resolve-table`. It returns the physical database table name (the `TDM_<hash>` name TDM3
stores rows under) for a given table title.

## Required inputs

Run [tdm-select-context](../tdm-select-context/SKILL.md) first if `PROJECT_NAME`, `ENV_NAME_DEFAULT`, or
`SYSTEM_NAME_DEFAULT` aren't resolved yet. **All four fields are required here** — unlike every other operation in
this controller, `envName` and `systemName` can't be left out to match "the first table with this title."

- `title-table` — the table to resolve.

## Request

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/rest/resolve-table' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<project>",
    "envName": "<env>",
    "systemName": "<system>",
    "title-table": "<table title>"
  }'
```

## Response

On success, a single object (not a list, like [tdm-insert-test-data](../tdm-insert-test-data/SKILL.md)):

```json
{ "type": "SUCCESS", "content": "TDM_547cdede996b44f4be273c444d4f7287", "contentObject": null, "link": "..." }
```

**This endpoint doesn't fail the same way the rest of the controller does — verified, both below:**

- A missing field (for example, no `envName`) returns `HTTP 400` with a **plain-text** body, not JSON:
  `Environment name is missed`. Parsing it as `{"type": ..., "content": ...}` throws.
- An `envName` or `systemName` that doesn't exist returns `HTTP 500` with Spring Boot's default error JSON
  (`timestamp`, `status`, `error`, `trace`, `message`, `path`) — a raw stack trace, not a `ResponseMessage`. The
  `message` field here is an internal error code (for example `TDM-5005`), not a description; read `error` for
  `"Internal Server Error"` and treat the whole response as "environment or system not found," since the trace is
  not meant for the end user.

Check the HTTP status before trying to read `type` — a 200 has the normal shape, a 400 or 500 does not.

## Common pitfalls

- Reusing the "check `type`, then `content`" pattern from the rest of this controller without a status-code check
  first: this is the one operation where a failure isn't wrapped in `ResponseMessage`.
- Omitting `envName`/`systemName` out of habit, since they're optional almost everywhere else in this controller.
