---
name: tdm-manage-dynamic-environment
description: Use when the user wants to create, update, or delete a TDM3 dynamic environment, system, or connection — the environments and systems tdm-select-context resolves against. Calls the TDM3 REST API directly.
---

# Create, update, or delete a TDM3 dynamic environment or system

All three operations share one endpoint, `/api/tdm/rest/create-env`, distinguished by HTTP method. This is the
write side of what [tdm-select-context](../tdm-select-context/SKILL.md) and
[tdm-list-tables](../tdm-list-tables/SKILL.md) read.

## Required inputs

- `projectName` — must be a name from `PROJECTS_INFO` (see [tdm-select-context](../tdm-select-context/SKILL.md)'s
  `PROJECT_NAME`).
- `envName` — the environment to create, update, or delete.
- For create/update: `systemName` and a `connection` object — `{name, type, parameters}`, where `type` is required
  (for example `"HTTP"` or `"DB"`) and `parameters` holds type-specific values such as `url`.

The request body can be sent flat (as below) or nested under an `"environment"` key — both are equivalent.

## `DB`-type connection parameters

For `connection.type: "DB"`, the minimal working set of `parameters` — verified live end to end, including a real
`POST /api/tdm/import/sql` run against the resulting system — is:

```json
{ "jdbc_url": "jdbc:postgresql://<host>:<port>/<database>", "db_login": "<user>", "db_password": "<password>", "db_type": "postgresql" }
```

`db_type` (`"postgresql"`, `"oracle"`, or `"h2"`) is **always required**, even when `jdbc_url` is set — TDM3 reads
it first, to pick the JDBC driver class, before it ever looks at `jdbc_url`; a system created without it fails any
operation that actually opens the connection (import, update-by-query, refresh) with a driver-not-found error.
TDM3's own UI additionally sends `db_host`, `db_port`, and `db_name` — those three are **not needed** when
`jdbc_url` is present: confirmed by reading `SqlRepositoryImpl` (they're only used to build a connection string for
the case `jdbc_url` is empty) and by a live `import/sql` run that succeeded without them. Include `jdbc_url` rather
than reconstructing it from host/port/database — it's the one the connection code actually uses.

## Create an environment and system, or add a system to an existing environment

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/rest/create-env' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<project>",
    "envName": "<env>",
    "systemName": "<system>",
    "connection": { "name": "HTTP", "type": "HTTP", "parameters": { "url": "http://example.com" } }
  }'
```

Verified live: `{"type":"SUCCESS","content":"Environment [<env>] created successfully.","contentObject":null,"link":""}`.
A `systemName` that already exists in `envName` returns a **plain-text** `400 Bad Request`
(`System [<name>] already exists in environment [<env>]. Use PUT to update.`) — not a `ResponseMessage`, verified
live.

## Update a system's connection, or rename the environment or system

```bash
curl -X PUT '<TDM3_BASE_URL>/api/tdm/rest/create-env' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<project>",
    "envName": "<env>",
    "systemName": "<system>",
    "newSystemName": "<new name, optional>",
    "connection": { "name": "HTTP", "type": "HTTP", "parameters": { "url": "http://example.org" } }
  }'
```

Set `newEnvName` instead of/alongside `newSystemName` to rename the environment. **A `systemName` that doesn't
exist is the one failure this controller doesn't catch — verified live: a raw `404` with Spring Boot's default
error JSON (`timestamp`, `status`, `error`, `trace`, `message`, `path`), not a `ResponseMessage`,** unlike every
other error in this controller. The message inside the trace also has project and environment swapped
(`"System [X] not found in project [<envName>], environment [<projectName>]"`) — read the identifiers by position,
not by the label next to them.

## Delete a system, or the whole environment

```bash
curl -X DELETE '<TDM3_BASE_URL>/api/tdm/rest/create-env' \
  -H 'Content-Type: application/json' \
  -d '{ "projectName": "<project>", "envName": "<env>", "systemDeleteName": "<system, optional>" }'
```

Omit `systemDeleteName` to delete the whole environment. Verified live both ways: on success, `content` reads
`"Environment [<env>] deleted successfully."` **even when only a system was deleted** — the message doesn't say
which happened, so don't parse it to confirm scope. An unknown `envName` is the one error this controller does
catch gracefully (unlike PUT above) — verified live: `HTTP 404` with a proper `ResponseMessage`,
`{"type":"ERROR","content":"Environment [<env>] not found in project [<project>].",...}`.

## Common pitfalls

- Omitting `connection.type`: required on create/update, verified live to fail with plain-text `400`,
  `Connection 'type' must not be blank.`, even though nothing else in the request looked wrong.
- Omitting `db_type` from a `DB` connection's `parameters` because `jdbc_url` is already set: the create/update
  call still succeeds (nothing validates `parameters`' contents), but every later operation that uses the
  connection fails — the missing-field error shows up far from where the mistake was made.
- Assuming every failure returns a `ResponseMessage`: only the create-duplicate case and the delete-not-found case
  do. Update-on-a-missing-system is a raw 404 stack trace; create-on-a-missing-`type` is a raw 400 plain text.
- Trusting delete's success message to say what was deleted: it's the same text for "removed one system" and
  "removed the whole environment."
