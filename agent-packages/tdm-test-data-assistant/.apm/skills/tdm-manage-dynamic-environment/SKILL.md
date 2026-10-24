---
name: tdm-manage-dynamic-environment
description: Use when the user wants to read, create, update, or delete a TDM3 dynamic environment, system, or connection — the environments and systems tdm-select-context resolves against. Calls the TDM3 REST API directly.
user-invocable: false
---

# Read, create, update, or delete a TDM3 dynamic environment or system

All four operations share one endpoint, `/api/tdm/rest/create-env`, distinguished by HTTP method. This is the
read and write side of what [tdm-select-context](../tdm-select-context/SKILL.md) and
[tdm-list-tables](../tdm-list-tables/SKILL.md) resolve against.

## Required inputs

- `projectName` — must be a name from `PROJECTS_INFO` (see [tdm-select-context](../tdm-select-context/SKILL.md)'s
  `PROJECT_NAME`).
- `envName` — the environment to read, create, update, or delete.
- For create and update: `systemName` and a `connections` list. Each item is `{type, parameters}`. `type` is `DB` or
  `HTTP` (any case) and `parameters` is a non-empty map of type-specific values such as `url`. The list holds one or two
  items, and no two share a type.

POST, PUT, and DELETE take a JSON body, which can be sent flat (as below) or nested under an `"environment"` key; both
are equivalent. GET takes query parameters instead.

## `DB`-type connection parameters

For a connection of type `DB`, the minimal working set of `parameters` — verified live end-to-end, including a real
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

## Read the connections of an environment or system

```bash
curl -G '<TDM3_BASE_URL>/api/tdm/rest/create-env' \
  --data-urlencode 'projectName=<project>' \
  --data-urlencode 'envName=<env>' \
  --data-urlencode 'systemName=<system, optional>'
```

Omit `systemName` to get every system of the environment. The response is not a `ResponseMessage`:

```json
{
  "projectName": "<project>",
  "envName": "<env>",
  "systems": [
    { "systemName": "<system>", "connections": [ { "type": "HTTP", "parameters": { "url": "http://example.com" } } ] }
  ]
}
```

The values of secret-looking parameters come back as `***`; the stored values are unchanged. A parameter name is
matched ignoring case and the separators `-`, `_`, `.`, and space, and its value is masked when the name contains
`password`, `token`, `secret`, `credential`, `authentication`, `authorization`, `apikey`, or `privatekey`, or when it
is `auth` or ends with `auth`. So `db_password` is masked and `author` is not. Do not read a password, token, or key
back through GET: to confirm a secret, compare against the value you sent, and to change one, send it again with PUT.

## Create an environment and system, or add a system to an existing environment

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/rest/create-env' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<project>",
    "envName": "<env>",
    "systemName": "<system>",
    "connections": [ { "type": "HTTP", "parameters": { "url": "http://example.com" } } ]
  }'
```

On success, the response is `{"type":"SUCCESS","content":"Environment [<env>] created successfully.",...}`. A
`systemName` that already exists in `envName` returns `400` with a `ResponseMessage` of `type: "ERROR"`
(`System [<name>] already exists in environment [<env>]. Use PUT to update.`).

## Update connections, or rename the environment or system

```bash
curl -X PUT '<TDM3_BASE_URL>/api/tdm/rest/create-env' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<project>",
    "envName": "<env>",
    "systemName": "<system>",
    "newSystemName": "<new name, optional>",
    "connections": [ { "type": "HTTP", "parameters": { "url": "http://example.org" } } ]
  }'
```

PUT upserts each connection by `type`: a `DB` item replaces the system's `DB` connection, and a connection whose type
is not in the request stays as it is. To remove a connection, delete the system and create it again.

Set `newEnvName` instead of or alongside `newSystemName` to rename the environment. A rename cannot be sent alone:
PUT without `connections` fails with `400`, `At least one connection is required in 'connections'.`, so send at least
one connection with it, and that connection is upserted as well.

A `systemName` that doesn't exist returns `404` with a `ResponseMessage` whose `content` reads
`System [<system>] not found in project [<project>], environment [<env>].`

## Delete a system, or the whole environment

```bash
curl -X DELETE '<TDM3_BASE_URL>/api/tdm/rest/create-env' \
  -H 'Content-Type: application/json' \
  -d '{ "projectName": "<project>", "envName": "<env>", "systemDeleteName": "<system, optional>" }'
```

Omit `systemDeleteName` to delete the whole environment. On success, `content` reads
`"Environment [<env>] deleted successfully."` **even when only a system was deleted**; the message doesn't say
which happened, so don't parse it to confirm scope. An unknown `envName` returns `404` with a `ResponseMessage`,
`{"type":"ERROR","content":"Environment [<env>] not found in project [<project>].",...}`.

## Error responses

| Code  | When                                                                                  | Body                                                |
|-------|---------------------------------------------------------------------------------------|-----------------------------------------------------|
| `400` | Validation: duplicate system, bad `type`, missing or empty `parameters` or `connections`, unknown project, GET without `projectName` or `envName` | `ResponseMessage` with `type: "ERROR"`              |
| `404` | Unknown environment (GET, PUT, DELETE)                                                | `ResponseMessage` with `type: "ERROR"`              |
| `404` | Unknown system (GET with `systemName`, PUT)                                           | `ResponseMessage` with `type: "ERROR"`              |

Every error of this endpoint is a `ResponseMessage`; read the reason from `content`.

## Common pitfalls

- Sending the old `connection` object or a connection `name`: the request body now takes `connections`, a list, and a
  `name` is ignored. A POST or PUT without `connections` fails with `400`.
- Using a type other than `DB` or `HTTP` (`GIT`, `LDAP`, `SSH`, ...): rejected with `400`.
- Sending two connections of the same type in one request: rejected, because the type identifies the connection.
- Omitting `db_type` from a `DB` connection's `parameters` because `jdbc_url` is already set: the create/update
  call still succeeds (nothing validates `parameters`' contents beyond being non-empty), but every later operation
  that uses the connection fails — the missing-field error shows up far from where the mistake was made.
- Expecting PUT to replace the whole connection list: it only upserts the types you send.
- Treating a masked `***` from GET as the stored value, or sending it back in a PUT: that overwrites the real secret
  with the three asterisks.
- Trusting delete's success message to say what was deleted: it's the same text for "removed one system" and
  "removed the whole environment."
