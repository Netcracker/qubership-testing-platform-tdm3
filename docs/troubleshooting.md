# Troubleshooting

This page explains what to check for a symptom you can observe from outside the service: a failed health check, a
REST error, or a log line. For the service's internal design, see [Architecture](architecture.md); for every
environment variable, see [Configuration](configuration.md).

## Readiness or liveness probe fails

`GET /rest/deployment/readiness` and `GET /rest/deployment/liveness` both run `SELECT` against the table catalog in
H2 and return HTTP 200 only if that query succeeds. A failing probe means the H2 database file is not reachable:
check that the persistent volume (or, locally, the `database/` directory) is mounted and writable, and that no other
process holds the H2 file lock. The [Helm chart](../README.md#deploy-with-helm) runs one replica with the `Recreate`
strategy for this reason: two processes must never open the same H2 file at once.

## Common REST errors

| Message                                                                     | Cause                                                                                                              | Action                                                                                                                |
|-----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `Project [<name>] not found.`                                               | The name is not a value in the [`PROJECTS_INFO`](configuration.md#projects) map.                                   | Add the project to `PROJECTS_INFO` and restart the service; the map is read once, at startup.                         |
| `Environment [<name>] not found in project [<project>].`                    | No [dynamic environment](architecture.md#projects-environments-and-systems) with that name exists for the project. | Create it with `POST` to the [Dynamic Environment API](api.md#dynamic-environment-api).                               |
| `System [<name>] already exists in environment [<env>]. Use PUT to update.` | A `POST` tried to add a system that is already registered in the environment.                                      | Use `PUT` to change its connection instead of `POST`.                                                                 |
| `Current search Class is not allowed here <class>`                          | A cleanup configuration was saved or run with `type: CLASS`.                                                       | See [Cleanup type CLASS is rejected](#cleanup-type-class-is-rejected) below; use `type: SQL` or `type: DATE` instead. |

## Cleanup type CLASS is rejected

`TestDataCleanupConfig.type` accepts `CLASS`, but saving or running a configuration with that type always fails with
`Current search Class is not allowed here <class>`. The check is a whitelist of classes that implement
`TestDataCleaner` and carry a Spring stereotype annotation; no class in this codebase does, so the whitelist is
always empty and every class name is rejected. Use `type: SQL` with a `searchSql` query, or `type: DATE` with a
`searchDate` age, both of which work as documented.

## Scheduled cleanup or refresh logs an error right after a delete

`An error occurred while running scheduled <cleanup|refresh> with id: <id>` in the log means a Quartz trigger fired
for a cleanup or refresh configuration, or its table, that was deleted between the trigger being scheduled and it
firing. The job logs the failure and stops; it does not retry, and it does not affect the scheduler or any other
job. If the deletion was intentional, no action is needed.

## Reading the logs for one table's cleanup or refresh

While a table's cleanup, refresh, or bulk operation runs, its log lines carry `environmentId` and `systemId` (and,
for an environment or system change, `projectId`) in the logging context. Filter or grep by one of these to isolate
that operation's log lines from concurrent ones; see
[Logging correlation](architecture.md#logging-correlation) for where they come from and when they are set.

## Metrics

`GET http://localhost:8090/metrics` (see [Monitoring and logging](configuration.md#monitoring-and-logging)) serves
Prometheus metrics, including these service-specific ones:

| Metric                                                                                                                                             | Meaning                                                                                                                                                                  |
|----------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `atp_tdm_tables_count`                                                                                                                             | Total number of test data tables.                                                                                                                                        |
| `atp_tdm_tables_count_per_project`                                                                                                                 | Number of test data tables, tagged by `project_id`.                                                                                                                      |
| `atp_tdm_execute_cleanup_by_cron`                                                                                                                  | Counter of scheduled cleanup runs, tagged by `cleanup_id`, `project_id`, and `table_title`.                                                                              |
| `atp_tdm_execute_refresh_by_cron`                                                                                                                  | Counter of scheduled refresh runs, tagged by `refresh_id`, `project_id`, and `table_title`.                                                                              |
| `atp_tdm_execute_statistics_by_cron`                                                                                                               | Counter of scheduled general-statistics email runs, tagged by `project_id`.                                                                                              |
| `atp_tdm_execute_user_statistics_by_cron`                                                                                                          | Counter of scheduled user-occupation email runs, tagged by `project_id`.                                                                                                 |
| `atp_tdm_insert_action`, `atp_tdm_occupy_action`, `atp_tdm_release_action`, `atp_tdm_update_action`, `atp_tdm_delete_action`, `atp_tdm_get_action` | Counters of the matching `test-data-controller` and `test-data-controller-v2` operations, tagged by `project_id`. `project_id` is `UNKNOWN` where the call carries none. |
