# Database transfer design

Status: proposal for discussion. This page elaborates
[ADR 0001](../adr/0001-database-transfer-through-rest-api.md), which records the decision. It describes the behavior the
feature will have, the components that implement it, and the questions the team has to settle before the work starts.

## Goals and non-goals

The feature lets a project move the whole state of one TDM3 service to another TDM3 service through the REST API, with
no access to the file system of either service.

Goals:

- Export the whole database from a running service.
- Import a database into a service so that the service starts from the imported data.
- Keep the previous database restorable when an import fails.
- Keep the feature off until a deployer enables it, and keep it behind a secret afterwards.

Non-goals:

- Moving a part of the data. The existing per-table export and import cover that.
- Merging a dump into a database that already holds data. An import replaces the whole database.
- Access control for the rest of the service.
- Support for in-memory or remote databases. The service refuses the operations when `JDBC_URL` is not a file database.
- Downgrades. A dump from a newer version of the service is rejected.

## Overview

The user exports a dump from the source service and uploads it to the target service. The upload only stores the dump in
a staging directory. A second request approves the replacement and restarts the service. The replacement itself runs
during startup, before the service opens a connection pool.

```mermaid
flowchart LR
    subgraph Source["Source TDM3"]
        EXP["GET /api/tdm/database/export"]
    end
    USER(["User or deployer"])
    subgraph Target["Target TDM3"]
        UP["POST /api/tdm/database/import"]
        APPLY["POST /api/tdm/database/import/apply"]
        STAGING[("Staging directory on the database volume")]
        BOOT["Startup: restore step"]
        DB[("H2 database files")]
    end
    EXP -->|"dump.sql.gz"| USER
    USER -->|"dump.sql.gz"| UP
    UP --> STAGING
    USER -->|"confirm"| APPLY
    APPLY -->|"approve and restart"| BOOT
    STAGING --> BOOT
    BOOT -->|"replace"| DB
```

The two-step import is deliberate. An upload destroys nothing, so a wrong file costs nothing. The replacement needs a
separate, explicit request. A restart that has nothing to do with the transfer, such as a rescheduled pod, never applies
a dump that nobody approved.

## Components

The class names are proposals. The packages follow the existing layout of the backend module.

| Component | Package | Responsibility |
| --- | --- | --- |
| `DatabaseTransferController` | `controllers` | The five REST operations under `/api/tdm/database`. |
| `DatabaseTransferService` and its implementation | `service`, `service.impl` | Streams the export, stages and validates an upload, records the status, approves and cancels. |
| `TransferKeyInterceptor` | `configuration` | Checks the transfer key for every request under `/api/tdm/database/**`. |
| `DumpValidator` | `service.impl` | Reads a dump once and rejects it if it is not a TDM3 dump or contains a forbidden statement. |
| `DatabaseRestoreInitializer` | `configuration` | Replaces the database files at startup. Called from `DbConfiguration.dataSource` before the pool is created. |
| `TransferStatusStore` | `service.impl` | Reads and writes `status.json` in the staging directory. |

`DbConfiguration.dataSource` creates a `HikariDataSource` with a minimum idle size of at least one connection, so the
pool opens the database file as soon as the bean exists. The restore step therefore runs first, inside that method,
before `new HikariDataSource(config)`. Quartz, ShedLock, and Liquibase all depend on this `DataSource` bean, so none of
them touches the database before the restore step finishes.

## Configuration

| Property | Environment variable | Default | Meaning |
| --- | --- | --- | --- |
| `tdm.db.transfer.key` | `TDM_DB_TRANSFER_KEY` | unset | The transfer key. At least 32 characters. The feature is off while the key is unset or shorter. |
| `tdm.db.transfer.max-size` | `TDM_DB_TRANSFER_MAX_SIZE` | `100MB` | Largest compressed dump the service accepts, as a Spring data size. `100MB` is 104857600 bytes. Independent of `MAX_FILE_SIZE` and `MAX_REQUEST_SIZE`. |
| `tdm.db.transfer.staging-dir` | `TDM_DB_TRANSFER_DIR` | `transfer` next to the database files | Directory for the staged dump, the status file, and the backups. It must be on the same volume as the database. |
| `tdm.db.transfer.backups` | `TDM_DB_TRANSFER_BACKUPS` | `2` | Number of database backups to keep after a successful import. |

The Helm chart in `deployments/charts/atp3-tdm-be` passes `TDM_DB_TRANSFER_KEY` from a Kubernetes Secret.

## Access: the transfer key

The service has no user model and runs with the `disable-security` profile, so the transfer endpoints cannot reuse a
role check. They use a shared secret instead.

- A `HandlerInterceptor` registered for `/api/tdm/database/**` checks the key. It works the same way in every Spring
  profile, so it does not depend on the security stack.
- While the key is unset or shorter than 32 characters, every transfer endpoint answers `404`. At startup the service
  logs one warning that says why the feature is off, without printing the key.
- With a key configured, a request must carry it in the `X-TDM-Transfer-Key` header. A missing or wrong key gets `403`
  with the same body in both cases.
- The comparison runs in constant time (`MessageDigest.isEqual` on the digests of both values).
- The key protects the transfer endpoints only. It does not change who can call the rest of the API.

The request logging of the service writes request headers to the log by default (`atp.logging.controller.headers`). The
feature adds `X-TDM-Transfer-Key` to the default of `atp.logging.controller.headers.ignore`, so the key never reaches
the log. A deployer who overrides that property has to keep the entry.

The key goes in a header, so the service must be reached over TLS. Terminating TLS at the ingress is enough.

### The dump is executable SQL

`RUNSCRIPT` runs every statement in the dump with the privileges of the service's database user. H2 can compile Java
source in `CREATE ALIAS` and in triggers, read files with `FILE_READ`, and load files with `CSVREAD`. A crafted dump is
therefore a way to run code inside the service JVM, which can read the environment, including `ATP_CRYPTO_KEY` and the
database credentials.

The transfer key already allows a holder to read and replace all data, so the key holder is trusted at administrator
level. The risk is bigger than data replacement, though, so the import adds a second barrier. `DumpValidator` reads the
whole dump once while it is stored, splitting it into statements with a tokenizer that understands quoted strings,
quoted identifiers, and comments. It rejects the dump when:

- a statement is not one of the kinds a TDM3 dump contains: creating or altering schemas, tables, indexes, sequences,
  and constraints, `INSERT ... VALUES`, comments, and a short list of `SET` options;
- any statement uses `ALIAS`, `TRIGGER`, `AGGREGATE`, `RUNSCRIPT`, `CALL`, `SCRIPT`, `CSVREAD`, `CSVWRITE`,
  `FILE_READ`, `FILE_WRITE`, `LINK_SCHEMA`, `SHUTDOWN`, or a statement that creates or changes users, roles, or grants;
- the first line is not the TDM3 dump header (see [Dump format](#dump-format-and-compatibility)).

The filter lowers the risk and does not remove it, because H2 is a large SQL dialect. The exact allowlist is fixed by
[spike S4](#spikes), which runs the filter against a real dump of a production-like database.

The service also stores the SHA-256 of the validated file in the status file and checks it again at startup before it
runs the dump.

## REST API

All paths are relative to the service root, as in [docs/api.md](../api.md#base-path-and-the-atp-gateway). Every
operation needs the `X-TDM-Transfer-Key` header.

| Operation | Method and path | Success | Errors |
| --- | --- | --- | --- |
| Export | `GET /api/tdm/database/export` | `200`, `application/gzip` | `404` feature off, `403` wrong key, `409` not a file database or an export already running |
| Stage a dump | `POST /api/tdm/database/import` | `202`, status body | `400` invalid dump with the reason, `403`, `404`, `409` not a file database, `413` over the size limit, `507` not enough free disk space |
| Read the status | `GET /api/tdm/database/import` | `200`, status body | `403`, `404` |
| Cancel | `DELETE /api/tdm/database/import` | `204` | `403`, `404` |
| Approve and restart | `POST /api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA&restart=true` | `202`, status body | `400` missing or wrong `confirm`, `403`, `404`, `409` nothing is staged |

Details:

- **Export.** The response has `Content-Disposition: attachment; filename="tdm-<database>-<timestamp>.sql.gz"`.
  The body is streamed, so the response has no `Content-Length`.
- **Stage a dump.** The request body is the raw `.sql.gz` file with `Content-Type: application/gzip`, not a multipart
  form. This avoids the global `spring.servlet.multipart` limit (`MAX_FILE_SIZE`, 100 MB by default), which also applies
  to the table import. A new upload replaces a staged or approved dump and sets the state back to `STAGED`.
- **Approve and restart.** `confirm` must be exactly `REPLACE_ALL_DATA`. With `restart=true` (the default) the service
  exits cleanly after the response is sent and relies on the platform to start it again. With `restart=false` the
  dump is approved and waits for the next restart, which the deployer triggers.
- **Cancel.** Deletes the staged dump and sets the state to `NONE`. It is also the way to withdraw an approved dump
  before the restart.

The status body:

```json
{
  "state": "STAGED",
  "uploadedAt": "2026-11-11T10:15:30Z",
  "sizeBytes": 73400320,
  "sha256": "9f2c...e1",
  "dumpServiceVersion": "1.0.13",
  "dumpH2Version": "2.2.224",
  "warnings": [],
  "approvedAt": null,
  "finishedAt": null,
  "error": null
}
```

`warnings` lists problems that do not stop the import, such as a large version gap (see
[Dump format](#dump-format-and-compatibility)). The upload response and every later status response carry it.

The states are `NONE`, `STAGED`, `APPROVED`, `APPLYING`, `APPLIED`, and `FAILED`. After a restore, `GET` shows the last
result (`APPLIED` or `FAILED` with `error`) until the next upload.

The controller uses the same `@Operation` and `@AuditAction` annotations as its siblings, so the operations appear in
Swagger UI and in the audit log. The frozen specifications in `qubership-atp-tdm-rest-openapi-specifications` are not
touched.

## Export

The export runs the H2 `SCRIPT` command on one connection from the pool and writes each returned row straight into the
response through a `GZIPOutputStream`. It uses `StreamingResponseBody`, so neither the dump nor a temporary file sits in
memory or on disk.

```mermaid
sequenceDiagram
    actor User
    participant Guard as TransferKeyInterceptor
    participant API as DatabaseTransferController
    participant Svc as DatabaseTransferService
    participant DB as H2 (pool connection)

    User->>Guard: GET /api/tdm/database/export (key)
    Guard->>API: key accepted
    API->>Svc: export(responseStream)
    Svc->>DB: SCRIPT
    Svc-->>User: dump header line
    loop each result row
        DB-->>Svc: one SQL statement
        Svc-->>User: gzip-compressed statement
    end
    Svc-->>User: end of stream
```

Only one export runs at a time. A second request gets `409`. The export holds one pooled connection for its whole
duration. The default pool has four connections (`jdbc.MaxPoolSize`), so the limit of one export keeps it from starving
the service.

The first line of the dump is a comment that the service writes before the `SCRIPT` rows:

```text
-- TDM3-DUMP v1 service=1.0.13 h2=2.2.224 created=2026-11-11T10:15:30Z database=atptdm
```

## Import: staging

The upload streams into `transfer/incoming.sql.gz.part`. The same pass counts bytes against the size limit, decompresses
the stream, and feeds it to `DumpValidator`, so a large file is read once. Only a file that passes becomes
`transfer/pending.sql.gz` by an atomic rename, and only then does the status become `STAGED`.

```mermaid
sequenceDiagram
    actor User
    participant Guard as TransferKeyInterceptor
    participant Svc as DatabaseTransferService
    participant FS as Staging directory

    User->>Guard: POST /api/tdm/database/import (key, body)
    Guard->>Svc: key accepted
    Svc->>FS: check free space
    Svc->>FS: stream body to incoming.sql.gz.part
    Svc->>Svc: validate header, version, statements
    alt dump is valid
        Svc->>FS: rename to pending.sql.gz
        Svc->>FS: write status STAGED
        Svc-->>User: 202 status body
    else dump is invalid or too large
        Svc->>FS: delete incoming.sql.gz.part
        Svc-->>User: 400 or 413 with the reason
    end
```

The free-space check requires room for the upload, a backup of the current database, and the restored database. It
estimates the restored size as the size of the dump times a factor that [spike S6](#spikes) measures.

The version rule at this step compares the service version in the dump header with the version of the running service.
See [Dump format](#dump-format-and-compatibility) for the outcomes.

## Import: applying at startup

`DatabaseRestoreInitializer` runs when the `DataSource` bean is created. It only acts when the status is `APPROVED`, or
`APPLYING` after a crash.

```mermaid
flowchart TD
    A["DbConfiguration.dataSource starts"] --> B{"jdbc.Url is a file database?"}
    B -- no --> POOL["Create the connection pool"]
    B -- yes --> C{"Status in status.json"}
    C -- "NONE, STAGED, APPLIED, FAILED" --> POOL
    C -- APPLYING --> CRASH["Previous restore did not finish: delete new files, restore backup, mark FAILED"]
    CRASH --> POOL
    C -- APPROVED --> D["Check SHA-256, validate the dump again"]
    D -- invalid --> F1["Mark FAILED, move the dump to failed/"]
    F1 --> POOL
    D -- valid --> E["Mark APPLYING, move database files to backup/timestamp/"]
    E --> G["Create a new database with jdbc.Url, jdbc.User, jdbc.Password; RUNSCRIPT the dump"]
    G -- error --> RB["Delete new files, move the backup back, mark FAILED, move the dump to failed/"]
    RB --> POOL
    G -- ok --> H["Clear scheduler runtime state, run sanity checks"]
    H -- error --> RB
    H -- ok --> I["Close the connection, mark APPLIED, move the dump to applied/, prune old backups"]
    I --> POOL
```

Rules that the diagram does not show:

- A failed restore does not stop the service. The service starts with the previous data, and `GET
  /api/tdm/database/import` reports `FAILED` with the reason. An import is a request, and a failed request does not take
  the service down.
- A crash during the restore rolls back. A dump that crashes the JVM (out of memory, for example) would otherwise
  loop on every restart.
- The database file path comes from `jdbc.Url`: `jdbc:h2:file:./database/atptdm;MODE=PostgreSQL` means
  `./database/atptdm.mv.db`. The new database is opened with the configured `jdbc.Url`, so settings in the URL, such as
  `MODE=PostgreSQL`, apply to it and do not come from the dump.
- The sanity checks read the Liquibase changelog table and the TDM3 catalog table and require that both exist. They do
  not run Liquibase.

### Scheduler state

The dump includes the Quartz and ShedLock tables. They hold two kinds of rows.

**Definitions** say what runs and when: `QRTZ_JOB_DETAILS`, `QRTZ_TRIGGERS` with the cron, simple, and blob trigger
tables, `QRTZ_CALENDARS`, and `QRTZ_PAUSED_TRIGGER_GRPS`. They are project data, such as the cleanup, refresh, and
statistics schedules. The restore keeps them unchanged, so the target runs the same schedules as the source.

**Runtime state** says what the source process was doing when the dump was taken:

- `QRTZ_SCHEDULER_STATE` holds the source's scheduler instance (`instanceId` is `AUTO`, so every start gets a new one).
- `QRTZ_FIRED_TRIGGERS` holds the executions that were in progress.
- `QRTZ_TRIGGERS.TRIGGER_STATE` is `ACQUIRED`, `BLOCKED`, or `PAUSED_BLOCKED` for triggers that an instance had claimed.
- Every row of the `shedlock` table is a lock held by the source, valid until its `lock_until` time.

The target must not inherit this state, because it belongs to a process that the target does not have. No scheduler runs
during the restore step, so the rows do no harm there. They take effect after the target starts, when Quartz looks for
failed instances and the first job asks for a lock. A claimed trigger could stay claimed, a job that was running on the
source could be recovered on the target, and a bulk action could wait for a lock that expires up to
`LOCK_BULK_ACTION_SEC` (3600 seconds by default) later.

After `RUNSCRIPT`, and before the sanity checks, the restore step clears the runtime state in one transaction:

1. Delete all rows of `QRTZ_FIRED_TRIGGERS` and `QRTZ_SCHEDULER_STATE`.
2. Set `TRIGGER_STATE` to `WAITING` where it is `ACQUIRED` or `BLOCKED`, and to `PAUSED` where it is `PAUSED_BLOCKED`.
   These are the changes Quartz makes itself when it recovers the jobs of a scheduler.
3. Delete all rows of `shedlock`.

The rows of `QRTZ_LOCKS` stay: they are fixed rows that the Quartz job store needs. A failure in the cleanup fails the
restore, and the rollback applies. A job that was running on the source does not run again on the target. A trigger
whose fire time has passed is handled by Quartz misfire handling when the service starts, as after any downtime.

The cleanup runs on the import side, not on the export side. The target is where the rule has to hold for every dump,
and removing the rows from the export stream would not reset `TRIGGER_STATE`. The dump stays a complete copy of the
source database. [Spike S5](#spikes) confirms the statements against the Quartz version in use.

## State machine

```mermaid
stateDiagram-v2
    [*] --> NONE
    NONE --> STAGED: upload accepted
    STAGED --> STAGED: new upload
    STAGED --> NONE: cancel
    STAGED --> APPROVED: apply
    APPROVED --> STAGED: new upload
    APPROVED --> NONE: cancel
    APPROVED --> APPLYING: service starts
    APPLYING --> APPLIED: restore succeeded
    APPLYING --> FAILED: restore failed or interrupted
    APPLIED --> STAGED: new upload
    FAILED --> STAGED: new upload
```

## Files on the volume

```text
database/
  atptdm.mv.db                      the live database
  transfer/
    status.json                     state, hashes, versions, last error
    incoming.sql.gz.part            an upload in progress
    pending.sql.gz                  the staged or approved dump
    backup/<yyyyMMdd-HHmmss>/       database files replaced by an import
    applied/<yyyyMMdd-HHmmss>.sql.gz
    failed/<yyyyMMdd-HHmmss>.sql.gz
```

`applied/` and `failed/` hold the dump of the last restore only, to keep disk use bounded. The
`tdm.db.transfer.backups` property limits `backup/`.

## Failure handling

| Failure | What happens |
| --- | --- |
| Upload interrupted | `incoming.sql.gz.part` is deleted on the next upload or at startup. The previous staged dump stays. |
| Dump fails validation | `400` with the reason. Nothing is staged. |
| Disk full during upload | `507` where the pre-check catches it. Otherwise the part file is deleted and the request fails. |
| Wrong or missing key | `403`. The attempt is logged with the client address and no key value. |
| Restore fails | Rollback to the backup, status `FAILED`, service starts with the old data. |
| Process dies during the restore | Next start rolls back (`APPLYING` found), status `FAILED: interrupted`. |
| Liquibase or a later startup step fails after a successful restore | The service does not start, as it does not start for any broken database. The API is not available, so the [recovery runbook](#recovery-when-the-service-does-not-start) works on the volume. |
| Platform does not restart the service after `apply` | The dump stays `APPROVED` until someone restarts the service. `GET` shows it. |

## Dump format and compatibility

- The file is a gzip stream of UTF-8 SQL statements, one `SCRIPT` row per statement, preceded by the header line.
- The header carries the version of the service that wrote the dump (its `project.version`). A qualifier such as
  `-SNAPSHOT` is ignored when the versions are compared. The upload compares it with the version of the running service:

  | Dump version | Outcome |
  | --- | --- |
  | Newer than the service | Rejected with `400`. Downgrades are not supported. |
  | Same major version, equal or older | Accepted. Liquibase brings the schema forward on the next start. |
  | Older major version | Accepted with an entry in `warnings`, for example a dump from 1.0.5 loaded into 5.10.20. |
  | Missing or not a version | Rejected with `400`. |

  The warning is information, not a block: the approval is a separate request, and the person who sends it sees the
  warning in the status. A version gap inside one major version, such as 1.0.5 into 1.5.10, gives no warning.
- The H2 version of the dump is recorded for diagnostics. Loading a dump from another H2 major version is not
  guaranteed. [Spike S3](#spikes) tests the versions the project uses.

## Operations

- **Disk.** Size the volume for the live database, one backup, and the staged dump together. A rule of thumb is three
  times the database, until spike S6 gives a measured factor.
- **Ingress.** The upload and the export are large and long. The ingress or gateway must allow a request body as large
  as the biggest dump (for NGINX, `proxy-body-size`) and a read timeout longer than the longest export.
- **Restart.** `apply` with `restart=true` exits with status 0. A Kubernetes Deployment restarts the container. A plain
  `docker run` without a restart policy does not, so the deployer restarts it.
- **Memory.** The upload and the export stream, so memory use does not grow with the size of the database.
- **The source service.** The target runs the same schedules as the source: cleanup, refresh, and the statistics emails.
  If the source keeps running after the export, both services run them. Stop or disable the source when the move is
  final.

## Recovery when the service does not start

After a successful replacement, startup continues as an ordinary start, and Liquibase runs as it always does. If
Liquibase or another step fails, the service does not start and the REST API, including the status endpoint, is not
available. The recovery therefore works on the volume. The implementation turns these steps into a page in
`docs/troubleshooting.md` for administrators and the support team:

1. Stop the service.
2. In the staging directory, find the newest directory under `backup/`. It holds the database files that the import
   replaced. The service logs its path at `INFO` level when the replacement finishes.
3. Move the current database files out of the database directory, and keep them for the analysis of the failure.
4. Copy the files from the backup directory into the database directory.
5. Start the service. It starts with the previous data. `status.json` still says `APPLIED`, which does no harm: only an
   `APPROVED` dump is applied.
6. Keep the dump in `applied/` for the analysis. A new upload replaces it.

## Tests

- Round trip: export a database with several test data tables, import the dump into an empty service, compare the
  catalog and the table contents.
- Older dump: import a dump from the previous schema version and check that Liquibase upgrades it.
- Version rule: a dump from a newer version and a dump with a missing version are rejected. A dump from an older major
  version is accepted with an entry in `warnings`. A dump from an older minor version of the same major version is
  accepted with no warning.
- Scheduler state: export while a cleanup job runs and a bulk action holds a lock, import the dump, and check that the
  target keeps the schedules, holds no scheduler instance, fired trigger, claimed trigger state, or lock of the source,
  and fires the jobs on schedule. See [Scheduler state](#scheduler-state).
- Rollback: stage a dump that fails in the middle of `RUNSCRIPT`, restart, and check that the old data is back and the
  status is `FAILED`.
- Crash: kill the process between moving the backup and finishing the restore, restart, and check the rollback.
- Validator: dumps that contain `CREATE ALIAS`, a trigger, `FILE_READ`, a nested `RUNSCRIPT`, and `INSERT ... SELECT`
  are rejected. A real dump of a large database is accepted.
- Key: no key, short key, wrong key, and right key, for each of the five operations, and the `404` while the feature is
  off. The key never appears in the log.
- Unapproved dump: restart with a `STAGED` dump and check that nothing changes.
- Size: an upload over the limit gets `413`, and the part file is removed.

## Documentation impact

Documentation that the implementation updates:

- `docs/api.md`: a row in the endpoint groups table for the new tag, and a section for the five operations.
- `docs/configuration.md`: the four properties above, in a new section.
- `docs/troubleshooting.md`: the errors of the transfer, including a restore that ended in `FAILED`, and the
  [recovery runbook](#recovery-when-the-service-does-not-start).
- `docs/architecture.md`: a short section that points to this page.
- `CHANGELOG.md`: an entry under the next version.
- `deployments/charts/atp3-tdm-be`: the Secret, the `TDM_DB_TRANSFER_KEY` variable, and the default of
  `atp.logging.controller.headers.ignore`.

## Spikes

Each spike runs against H2 2.2.224, the version in `parent/qubership-atp-tdm-parent-java/pom.xml`, before the
implementation starts.

| # | Question | Why it matters |
| --- | --- | --- |
| S1 | Does `SCRIPT` give a consistent snapshot while other sessions write, and does it block writers? For how long on a large database? | Decides whether the export can run on a live service. |
| S2 | Which `SCRIPT` options does the dump need so that it does not carry the source's user and password hash, and does not override settings that come from the target URL? | The target database user and password come from `TDM_DB_USER` and `TDM_DB_PASSWORD`. |
| S3 | Does a dump round trip for every column type the test data tables use, including large objects? Does a dump from another H2 2.x version load? | Decides the compatibility statement. |
| S4 | Which statement kinds does a real dump contain? Does the live database define any alias or trigger? | Fixes the allowlist of `DumpValidator`. |
| S5 | Which `TRIGGER_STATE` values does the Quartz version in use write for claimed triggers? Does the service start, keep its schedules, and run them after the cleanup of [Scheduler state](#scheduler-state)? Does `spring.quartz.jdbc.initialize-schema=ALWAYS` leave existing Quartz data intact? | Confirms the cleanup statements. |
| S6 | How large is the restored database compared with the gzip dump, and how long does `RUNSCRIPT` take for a database of several gigabytes? | Sets the disk pre-check factor and the timeout advice. |

## Decisions

1. **Restart.** `apply` with `restart=true` may make the service exit and rely on the platform to start it again. A
   manual stop and start after `apply` is also acceptable, because an import is a rare operation.
2. **Upload size.** The default of `tdm.db.transfer.max-size` is `100MB` for the compressed file, the same size as the
   defaults of `MAX_FILE_SIZE` and `MAX_REQUEST_SIZE`. The property is separate from them. A deployer who needs a
   larger dump raises it, and the ingress or gateway limits of the deployment apply as they do to every other upload.
3. **Scheduler state.** The import brings the definitions of the Quartz jobs and triggers over unchanged, like every
   other table. It clears the runtime state of the source: the scheduler instance, the fired triggers, the claimed
   trigger states, and the ShedLock rows. Definitions are project data, and runtime state belongs to the process that
   wrote it. See [Scheduler state](#scheduler-state).
4. **The key.** A shared secret is acceptable for now.
5. **Liquibase failure.** After the replacement, startup continues as an ordinary start, and Liquibase runs as it always
   does. If it fails, the service does not start. The administrators and the support team get the
   [recovery runbook](#recovery-when-the-service-does-not-start), which includes putting the previous database back.
6. **Version rule.** A dump from a newer version than the service is rejected. A dump from an older major version is
   accepted with a warning. See [Dump format](#dump-format-and-compatibility).

No open questions remain. The [spikes](#spikes) hold the facts that the implementation has to establish first.
