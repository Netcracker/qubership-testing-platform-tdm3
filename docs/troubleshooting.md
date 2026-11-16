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

## Database transfer

The steps of the transfer are in [Database transfer](database-transfer.md). An error of the five operations comes back
as plain text with the reason. The state of the last import is in `GET /api/tdm/database/import`.

| Answer                                                                                                             | Cause                                                                                                             | Action                                                                                                                             |
|--------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `404` with no body                                                                                                 | `TDM_DB_TRANSFER_KEY` is unset or shorter than 32 characters.                                                     | Set a key of at least 32 characters and restart the service. The startup log says `The database transfer is off`.                  |
| `403` `The transfer key is missing or wrong.`                                                                      | The `X-TDM-Transfer-Key` header is missing, or it differs from the key of this service.                           | Send the key of the service that you call. The log has a `WARN` with your address and the path.                                    |
| `409` `The database transfer needs an H2 file database, and jdbc.Url does not point to one.`                       | `JDBC_URL` is not an H2 file URL, for example an in-memory database.                                              | The transfer works only on an H2 file database.                                                                                    |
| `409` `Another export is already running.`                                                                         | An export has not finished.                                                                                       | Wait for it. A closed download releases the slot.                                                                                  |
| `409` `Another import operation is running.`, or `A restore is running.`                                           | An upload, an approval, or a cancel is in progress.                                                               | Repeat the call when the first one ends.                                                                                           |
| `409` `No dump is staged.`                                                                                         | The approval found no `STAGED` or `APPROVED` dump.                                                                | Upload the dump first.                                                                                                             |
| `400` `Pass confirm=REPLACE_ALL_DATA to replace all data of this service.`                                         | The `confirm` parameter is missing or has another value.                                                          | Pass exactly `confirm=REPLACE_ALL_DATA`.                                                                                           |
| `400` `The upload is not a complete gzip-compressed UTF-8 text: <cause>`                                           | The body is not a gzip file, is cut short, or is not UTF-8.                                                       | Upload the file that the export wrote. With `curl`, write `--data-binary "@<file>"` with the `@`.                                  |
| `400` `Invalid dump: <cause>`                                                                                      | The dump has no header line, or holds a statement that a TDM3 dump never contains.                                | Upload an unmodified export. The cause names the statement, for example `The statement "CALL 1..." is not allowed in a TDM3 dump.` |
| `400` `The dump comes from <version>, which is newer than this service (<version>). Downgrades are not supported.` | The source runs a newer version than the target.                                                                  | Update the target to the version of the source, or export from an older service.                                                   |
| `413` `The dump is larger than the limit of <bytes> bytes (tdm.db.transfer.max-size).`                             | The upload is larger than `TDM_DB_TRANSFER_MAX_SIZE`.                                                             | Raise `TDM_DB_TRANSFER_MAX_SIZE` and restart the service. A proxy in front of the service can also answer `413`.                   |
| `415` `Unsupported Media Type`                                                                                     | The upload has a content type other than `application/gzip`, `application/x-gzip`, or `application/octet-stream`. | Set the `Content-Type` of the upload.                                                                                              |
| `507` `The volume of the staging directory has <n> bytes free, and the import needs <m> bytes.`                    | The volume has too little free space for the upload and the restore.                                              | Free space or enlarge the volume.                                                                                                  |

### An import ended in FAILED

`GET /api/tdm/database/import` shows `"state": "FAILED"` and the reason in `error`. The service started with the data it
had before. The dump of the failed import is in `transfer/failed/`.

| `error` starts with                                                    | Cause                                                                                                                                  |
|------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `The approved dump is not usable:`                                     | The stored dump changed after the upload (`The staged dump changed after it was validated.`) or no longer passes the check at startup. |
| `The restore failed and the previous database was put back:`           | The database rejected the dump, or it lacks a table that the service needs. The text after the colon is the message of the database.   |
| `The restore was interrupted, and the previous database was put back.` | The process stopped during an earlier restore. The next start rolled it back.                                                          |

The startup log has the details: `The database restore failed: <message>` at `ERROR` level, then `Put the previous
database back from <directory>`. Upload a new export of the source to try again.

### Recovery when the service does not start

After a successful replacement the service starts as it always does, and Liquibase runs on the new database. If
Liquibase or another startup step fails, the service does not start, and the REST API is not available. Put the
previous database back on the volume:

1. Stop the service. In Kubernetes, scale the Deployment to zero replicas.
2. Find the newest directory under `transfer/backup/` next to the database files. It holds the files that the import
   replaced. The log of the last start has the line `The database was replaced with the imported dump. The previous
   database is in <directory>` at `WARN` level.
3. Move the current database files (`<database>.mv.db` and `<database>.trace.db`) out of the database directory. Keep
   them for the analysis of the failure.
4. Copy the files from the backup directory into the database directory. Leave the file `MOVED` behind.
5. Start the service. It runs on the previous data. `status.json` still says `APPLIED`, which does no harm, because only
   an `APPROVED` dump is applied.

The imported dump is not kept after a successful restore. To analyze the failure, use the files that you moved aside in
step 3, or export the source again.

If the log says `Cannot put the previous database back`, the rollback itself failed and the service refuses to start on
files in an unknown state. The message names the backup directory and the database directory. Move the files as in
steps 3 and 4 by hand.

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
