# Architecture

This page describes the design behind the backend module: how a test data table is stored, where projects and
environments come from, how an import or a refresh reaches an external system, and how scheduled and bulk operations
run. For the module layout of the repository, see [Repository layout](../README.md#repository-layout) in the readme;
for the REST and WebSocket surface, see [docs/api.md](api.md).

## Request handling

A REST request enters through a controller in `org.qubership.atp.tdm.controllers`, which calls one service interface
in `org.qubership.atp.tdm.service`. A service holds the business rules and calls one or more repositories in
`org.qubership.atp.tdm.repo` for persistence, and, for the operations that reach another system, `SqlRepository` for a
connection to that system's own database. `GlobalExceptionHandler` converts the exceptions services throw into the
HTTP responses [docs/api.md](api.md#errors) describes.

## Where test data lives

Each test data table is two things: a row in `TestDataTableCatalog` (project, environment, and system IDs, the table's
title, and the IDs of its cleanup and refresh configurations) and a literal table in the H2 database, created and
altered at runtime with the columns the imported data has. `TestDataTableRepositoryImpl` builds and runs the `CREATE
TABLE`, `INSERT`, and `SELECT` statements for that table directly; there is no ORM entity for its rows, because their
shape is not known until the first import.

An import runs a SQL query against the target system's own database (see
[Importing and refreshing data](#importing-and-refreshing-data)) and stores every returned row in this per-table H2
table, alongside the system columns `ROW_ID`, `CREATED_WHEN`, `OCCUPIED_BY`, and `OCCUPIED_DATE` that occupying and
releasing a row read and write.

## Projects, environments, and systems

TDM3 does not call an external environments service. Two different sources stand in for it:

- **Projects** are a static list: the [`PROJECTS_INFO`](configuration.md#projects) environment variable maps each
  project ID to a name, and `EnvironmentsServiceImpl` reads that map directly. A project name that is not in the map
  is rejected with `Project [<name>] not found.`; adding a project requires setting the variable and restarting the
  service.
- **Environments and systems** are rows in the local H2 tables behind `DynamicEnvironmentRepository` and
  `DynamicSystemRepository`, created, renamed, and deleted through the
  [Dynamic Environment API](api.md#dynamic-environment-api). A system's connection parameters are stored as a JSON
  object in that row.

`qubership-atp-tdm-env-configurator` supplies the shared model for both: `LazyProject`, `LazyEnvironment`, and
`LazySystem` carry only the fields the REST API and the scheduler need, and `EnvironmentsServiceImpl` builds them from
the project map and the two H2 tables rather than from a client call.

## Importing and refreshing data

An import (`POST /api/tdm/v2` or the SQL import operations of `test-data-controller`) and a scheduled or manual
refresh both run the table's stored SQL query against the system's own database connection, opened through
`SqlRepository` with the connection parameters `EnvironmentsServiceImpl` resolves for that system. The query result
replaces the table's rows in H2. A query that fails, times out, or returns no rows leaves the existing rows in place;
see the `ImportTestDataStatistic` schema in [docs/openapi.json](openapi.json) for what an import reports per
environment.

## Scheduled jobs

Cleanup and refresh each run on their own Quartz schedule, stored per table as a `TestDataCleanupConfig` or
`TestDataRefreshConfig` row. The statistics email reports run per project instead, stored as a
`TestDataTableMonitoring` (general statistics), `TestDataTableUsersMonitoring` (occupation by user), or
`TestAvailableDataMonitoring` (available-data-by-column) row. `JobsInitListener` reads every enabled configuration of
all five kinds and registers its job with Quartz once, on `ApplicationReadyEvent`, after the application context is
fully started; saving or deleting a configuration through the REST API updates the corresponding job through
`SchedulerService` from then on. The same listener also schedules two service-wide housekeeping jobs that are not tied
to a saved configuration: `TableCleanerJob`, which drops tables past their retention on the
[`TABLE_EXPIRATION_CRON`](configuration.md#data-retention) schedule, and `CleanRemovingHistoryJob`, which prunes the
history of tables `TableCleanerJob` already dropped.

A trigger that fires after its configuration was deleted logs `An error occurred while running scheduled
<cleanup|refresh> with id: <id>` and the job stops there; the scheduler and the other jobs are unaffected.
`CleanupType.CLASS` is a special case: the `TestDataCleanupConfig` schema in [docs/openapi.json](openapi.json)
describes it, and [Troubleshooting](troubleshooting.md#cleanup-type-class-is-rejected) covers the error it produces.

## Bulk operations over WebSocket

`test-data-controller`'s bulk cleanup, refresh, import, drop, and link-refresh operations act on every table matching
a project, environment, or system filter, which can take longer than a single REST request comfortably allows. These
run over a WebSocket connection instead: a handler under `org.qubership.atp.tdm.websocket.bulkaction` processes the
matching tables one at a time and pushes a message per table as it finishes, plus a final `FINISHED` message. See
[WebSocket bulk operations](api.md#websocket-bulk-operations) for the message shapes.

## Logging correlation

`TdmMdcHelper` adds `environmentId`, `systemId`, and, for an environment or system change notification, `projectId` to
the logging context (SLF4J MDC) around one table's cleanup, refresh, or bulk operation, and removes them once that
operation finishes. Every log line logged while they are set carries these fields, so grepping or filtering by one of
them isolates that table's log lines from the rest of the service's traffic in the same time window.
