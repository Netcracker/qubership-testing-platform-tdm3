# REST and WebSocket API

The service exposes its functionality as a REST API and, for long-running bulk operations, as WebSocket endpoints.
[`docs/openapi.json`](openapi.json) is the generated description of every REST operation, request, and response, and a
running service also serves it live and renders it in Swagger UI; see [REST API](../README.md#rest-api) in the readme
for the addresses. This page gives the shape the generated description does not: the endpoint groups, the gateway
prefix, the response and error formats, the WebSocket bulk operations, and the Dynamic Environment API.

## Base path and the ATP gateway

Every REST path in this page and in `docs/openapi.json` is relative to the service root. When the service is deployed
behind the ATP gateway, prepend the configured gateway prefix
([`ATP_SERVICE_PATH`](configuration.md#atp-integration), default `/api/atp-tdm/v1`) to it.

## Endpoint groups

Each REST controller is one Swagger tag. `docs/openapi.json` and Swagger UI group operations by tag; this table gives
each tag's base path and purpose. For what an individual operation does, see its entry there.

| Tag                       | Base path                                              | Purpose                                                                                                   |
|---------------------------|--------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `test-data-controller`    | `/api/tdm`                                             | Test data tables: import, read, occupy and release rows, export, maintenance.                             |
| `test-data-controller-v2` | `/api/tdm/v2`                                          | The same import-by-SQL operation as `test-data-controller`, with its parameters in the request body.      |
| `atp-action-controller`   | `/api/tdm/rest`                                        | The operations ATP actions call on a test data table, addressed by project, environment, and system name. |
| `atp-env-controller`      | `/api/tdm/rest/create-env`                             | The [Dynamic Environment API](#dynamic-environment-api).                                                  |
| `data-refresh-controller` | `/api/tdm/data/refresh`                                | Refresh settings and manual refresh runs of test data tables.                                             |
| `data-cleanup-controller` | `/api/tdm/cleanup`                                     | Cleanup settings and manual cleanup runs of test data tables.                                             |
| `statistics-controller`   | `/api/tdm/statistics`                                  | Statistics on test data tables and the scheduled email reports.                                           |
| `environments-controller` | under `/api/tdm/` (`environments`, `envs`, `projects`) | Projects, dynamic environments, and their systems.                                                        |
| `versions-controller`     | `/api/tdm/versions`                                    | Service name and version.                                                                                 |
| `route-info-controller`   | `/atp-integration`                                     | Contributed by the ATP integration library, not by this service's own code.                               |

## Response format

Most operations return their result directly, typed as shown in `docs/openapi.json`: a list, an object, or nothing.
Two groups of operations instead wrap every result, success or failure, in a `ResponseMessage`:

```json
{
  "type": "SUCCESS",
  "content": "Environment [myEnv] created successfully.",
  "contentObject": null,
  "link": ""
}
```

`type` is `SUCCESS` or `ERROR`. `content` carries a message; `contentObject`, when set, carries a structured result
instead of or in addition to it; `link` carries a URL into the TDM UI, or an empty string.

- The `atp-action-controller` operations that report per row, such as occupying or releasing rows, return one
  `ResponseMessage` per row request, with HTTP status 200 whether that row request succeeded or failed. A caller
  must read `type` field by field; the HTTP status alone does not say whether a row request succeeded.
- The [Dynamic Environment API](#dynamic-environment-api) returns a `ResponseMessage` on success, and on some, but
  not all, of its failures; see [Failure responses](#failure-responses) there.

## Errors

An operation that is not one of the two groups above reports a failure in one of two shapes, depending on how the
exception that caused it is handled:

- **A validation failure** (an `IllegalArgumentException`, or a caller without the required access, an
  `AccessDeniedException`) returns its message as plain text, with `Content-Type: text/plain`:

  ```text
  HTTP/1.1 400 Bad Request
  Content-Type: text/plain;charset=UTF-8

  Project name is missed
  ```

  An `AccessDeniedException` returns HTTP 403 the same way.

- **Every other failure**, including one from a class such as `TdmSearchTableException` whose name suggests a
  purpose-built response, falls through to Spring Boot's default error page:

  ```json
  {
    "timestamp": "2026-09-24T15:54:15.255+00:00",
    "status": 500,
    "error": "Internal Server Error",
    "trace": "org.qubership.atp.tdm.exceptions.internal.TdmSearchTableException: Table [NoSuchTable] under project [...] and system [...] wasn't found.\n\tat ...",
    "message": "TDM-0017",
    "path": "/api/tdm/table/row"
  }
  ```

  `message` carries the code from the exception class's `@ResponseStatus(reason = ...)` when it declares one (most
  of the exceptions under `org.qubership.atp.tdm.exceptions` do, as `TDM-nnnn`), and otherwise the HTTP reason phrase
  or a generic `Request processing failed: ...` text. `trace` carries the server's full stack trace as a string, in
  every response of this shape, including on a production deployment.

## WebSocket bulk operations

A bulk operation applies to every test data table of a project (and, depending on the operation, of one system or
with one title), instead of to one table at a time. The five bulk operations are WebSocket endpoints, because they
run for longer than an HTTP request and report progress as they go:

| Path                     | Applies to                                                           | Effect                                                                            |
|--------------------------|----------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `websocket/bulk/refresh` | tables of a project and system with an import query                  | Runs the refresh of each table, as `POST /api/tdm/data/refresh/run` does for one. |
| `websocket/bulk/cleanup` | tables of a project and system with cleanup configured and enabled   | Runs the cleanup of each table.                                                   |
| `websocket/bulk/import`  | tables of a project and title with an import query                   | Runs the same refresh as `websocket/bulk/refresh`.                                |
| `websocket/bulk/drop`    | tables of a project and title                                        | Drops each table.                                                                 |
| `websocket/bulk/links`   | tables of a project and system that have column links configured     | Refreshes the links of each table.                                                |

A client sends one text message to start the operation, a JSON object with these fields:

| Field               | Description                                                                                                                                                    |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `projectId`         | Project ID.                                                                                                                                                    |
| `systemId`          | System ID. Read by `refresh`, `cleanup`, and `links`; ignored by `import` and `drop`.                                                                          |
| `tableTitle`        | Title of the tables to act on. Read by `import` and `drop`; ignored by the others.                                                                             |
| `saveOccupiedData`  | For `refresh` and `import`: keeps the occupied rows instead of deleting them.                                                                                  |
| `executeInParallel` | Runs the tables of the operation on up to 10 threads at once, instead of one at a time.                                                                        |
| `sendResult`        | Also emails the results to `recipients` when the operation finishes.                                                                                           |
| `recipients`        | Email addresses for the result email. Read only when `sendResult` is set.                                                                                      |
| `environmentId`     | Read only when `sendResult` is set: the environment and, with `systemId`, the system name the email reports. Does not filter the tables the operation acts on. |

The server replies with one or more text messages, each a JSON object, and then closes the connection:

1. `{"id": <processId>, "status": "STARTED"}`, once, when the operation begins.
2. One message per table the operation found, in the shape of `BulkActionResult`: `tableTitle`, `tableName`,
   `environmentName`, and either `results` (the same per-table result an operation returns over REST, such as
   `RefreshResults` or `CleanupResults`) or `exception`, whichever the table produced.
3. `{"id": <processId>, "status": "NOTHING_FOUND"}` instead of the above, when no table matched.
4. `{"id": <processId>, "status": "FINISHED"}`, once, when every table has been processed.

`processId` is the same number across all the messages of one run, generated when the run starts. The server locks
the path and the project for the run's duration ([`LOCK_BULK_ACTION_SEC`](configuration.md#locks)), so a second run of
the same bulk operation on the same project waits for the first to finish; a different bulk operation on the same
project does not wait.

## Dynamic Environment API

The **Dynamic Environment API** lets you create, update, and delete test environments at runtime without editing YAML
configuration files. Dynamic environments are stored in H2 and read from it on every request; there is no in-memory
cache of them, so a change is visible immediately.

**Base path:** `/api/tdm/rest/create-env`

All endpoints accept `Content-Type: application/json`. `POST`, `PUT`, and `DELETE` return a `ResponseMessage`
object on success. `GET` returns the connections themselves (see [Get connections](#4-get-connections)):

```json
{
  "type": "SUCCESS",
  "content": "Environment [myEnv] created successfully."
}
```

### Failure responses

These endpoints do not report every failure the same way:

| Code  | When                                                                                                | Body                                    |
|-------|-----------------------------------------------------------------------------------------------------|-----------------------------------------|
| `400` | A validation error: a duplicate system, an invalid connection type, a missing or invalid parameter. | `ResponseMessage` with `type: "ERROR"`. |
| `400` | `GET` with a missing `projectName` or `envName`, or a project that does not exist.                  | `ResponseMessage` with `type: "ERROR"`. |
| `404` | The environment does not exist (`GET`, `PUT`, `DELETE`).                                            | `ResponseMessage` with `type: "ERROR"`. |
| `404` | The system does not exist (`GET` when `systemName` is set, and `PUT`).                              | `GET`: `ResponseMessage` with `type: "ERROR"`. `PUT`: the [generic error body](#errors). |

Validation errors and a missing environment return `ResponseMessage`. A missing system on `PUT` still uses the generic
error body. Check the shape you get against the table above, or against [Errors](#errors) for anything not listed in it.

### Request body

Fields can be sent as a flat JSON object or nested under an `environment` property (both formats are supported):

| Field              | Required                         | Used by        | Description                                                                                          |
|--------------------|----------------------------------|----------------|------------------------------------------------------------------------------------------------------|
| `projectName`      | yes                              | all            | Name of an existing TDM project.                                                                     |
| `envName`          | yes                              | all            | Environment name to create, update, delete, or read.                                                 |
| `systemName`       | yes for POST and PUT; optional for GET | POST, PUT, GET | System name within the environment. On GET, omit it to return every system in the environment. |
| `connections`      | yes for POST and PUT*            | POST, PUT      | List of connection objects. Must contain one or two items. |
| `newEnvName`       | no                               | PUT            | Rename the environment.                                                                              |
| `newSystemName`    | no                               | PUT            | Rename the target system.                                                                            |
| `systemDeleteName` | no                               | DELETE         | When set, deletes only this system; otherwise deletes the whole environment.                         |

\* `connections` is required on POST and PUT. `GET` ignores it.

**Connection object** (each item of `connections`):

| Field        | Required | Description                                                                                                     |
|--------------|----------|-----------------------------------------------------------------------------------------------------------------|
| `name`       | no       | Deprecated compatibility field. It is ignored. |
| `type`       | yes      | `DB` or `HTTP` only (case-insensitive). Stored as `DB` or `HTTP`.                                               |
| `parameters` | yes      | Key-value map of connection parameters (e.g. host, port, credentials). Please use lowercase! Must not be empty. |

A system can hold at most two connections: one `DB` and one `HTTP`. Connection type is its identity, so the request
cannot contain duplicate types. `POST` stores every connection on the new system. `PUT` upserts by type; connections
that are not in the request stay as they are.

### Endpoints

| Method   | Path                       | Description                                                                                  |
|----------|----------------------------|----------------------------------------------------------------------------------------------|
| `GET`    | `/api/tdm/rest/create-env` | Return connections for an environment, or for one system. Secret values are masked.         |
| `POST`   | `/api/tdm/rest/create-env` | Create a new environment with a system, or add a system to an existing environment.         |
| `PUT`    | `/api/tdm/rest/create-env` | Add or update canonical connections by type, and optionally rename the environment or system. |
| `DELETE` | `/api/tdm/rest/create-env` | Delete an entire environment, or a single system within it. Connections are deleted with them. |

### Supported connection types

`create-env` accepts **`DB` and `HTTP` only**. Other types (`GIT`, `LDAP`, `SSH`, and the rest of the connection-type
enum) are rejected with HTTP `400`. Matching ignores case (`db`, `Http`); the stored value is `DB` or `HTTP`.

### User guide

#### 1. Create a new environment

Creates an environment with one system and registers it in TDM. The project must already exist.

```bash
curl -X POST http://localhost:8080/api/tdm/rest/create-env \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "MyProject",
    "envName": "myEnv",
    "systemName": "system1",
    "connections": [
      {
        "name": "DB",
        "type": "DB",
        "parameters": {
          "host": "localhost",
          "port": "5432"
        }
      }
    ]
  }'
```

#### 2. Add another system to an existing environment

Send another `POST` with the same `projectName` and `envName` but a different `systemName`. If the system name
already exists, the API returns `400` with a message to use `PUT` instead.

```bash
curl -X POST http://localhost:8080/api/tdm/rest/create-env \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "MyProject",
    "envName": "myEnv",
    "systemName": "system2",
    "connections": [
      {
        "name": "HTTP",
        "type": "HTTP",
        "parameters": {
          "url": "https://example.com"
        }
      }
    ]
  }'
```

#### 3. Update connection parameters

Use `PUT` to add a connection or change an existing one by type. Connections you do not send are left unchanged. You
can also rename the environment or system with `newEnvName` / `newSystemName`.

```bash
curl -X PUT http://localhost:8080/api/tdm/rest/create-env \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "MyProject",
    "envName": "myEnv",
    "systemName": "system1",
    "connections": [
      {
        "name": "DB",
        "type": "DB",
        "parameters": {
          "host": "db.example.com",
          "port": "5432"
        }
      }
    ]
  }'
```

Rename environment (all systems in the environment are renamed):

```bash
curl -X PUT http://localhost:8080/api/tdm/rest/create-env \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "MyProject",
    "envName": "myEnv",
    "systemName": "system1",
    "newEnvName": "renamedEnv",
    "connections": [
      {
        "name": "DB",
        "type": "DB",
        "parameters": {
          "host": "localhost",
          "port": "5432"
        }
      }
    ]
  }'
```

#### 4. Get connections

`GET` uses the same name-based body as create. `connections` on that body is ignored, so a pasted
create payload still works. When `systemName` is omitted, every system in the environment is returned. Values of
parameters whose names look like secrets (`password`, `token`, `secret`, and names that contain those words, such as
`db_password` or `apiToken`) are returned as `***`. The stored values are not changed.

```bash
curl -X GET http://localhost:8080/api/tdm/rest/create-env \
  -H "Content-Type: application/json" \
  -d '{
    "environment": {
      "projectName": "MyProject",
      "envName": "myEnv",
      "systemName": "system1"
    }
  }'
```

#### 5. Delete an environment or system

Delete the entire environment (all systems):

```bash
curl -X DELETE http://localhost:8080/api/tdm/rest/create-env \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "MyProject",
    "envName": "myEnv"
  }'
```

Delete a single system (other systems in the same environment remain):

```bash
curl -X DELETE http://localhost:8080/api/tdm/rest/create-env \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "MyProject",
    "envName": "myEnv",
    "systemDeleteName": "system1"
  }'
```

#### Alternative request format

The same payload can be wrapped in an `environment` object (useful when calling from ATP Actions):

```json
{
  "environment": {
    "projectName": "MyProject",
    "envName": "myEnv",
    "systemName": "system1",
    "connections": [
      {
        "name": "DB",
        "type": "DB",
        "parameters": {
          "host": "localhost",
          "port": "5432"
        }
      }
    ]
  }
}
```

#### Typical workflow

1. Ensure the target **project** exists in TDM.
2. **Create** the environment with `POST` and the system's connections (`DB`, `HTTP`, or both).
3. **Add** more systems with additional `POST` calls if needed.
4. **Add or update** connections, or rename, with `PUT` when infrastructure changes.
5. **Read** connections with `GET`. Secret parameter values come back masked.
6. **Delete** obsolete systems or whole environments with `DELETE`.

After creation, the environment is available in the TDM UI and for test data operations on that project.
