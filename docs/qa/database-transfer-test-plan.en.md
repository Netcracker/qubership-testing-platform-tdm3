# Test plan: TDM3 database transfer (export and import)

This plan is for the QA engineer. It covers the feature described in `docs/design/database-transfer.md`: exporting the
whole H2 database of a TDM3 service through REST and importing it into another service (or the same one).

## 1. Scope

| Operation | Endpoint |
| --- | --- |
| Export | `GET /api/tdm/database/export` |
| Upload a dump | `POST /api/tdm/database/import` |
| Read the import status | `GET /api/tdm/database/import` |
| Cancel a staged dump | `DELETE /api/tdm/database/import` |
| Approve the import | `POST /api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA&restart=true\|false` |

Every endpoint needs the transfer key in the `X-TDM-Transfer-Key` header.

How an import works:

1. The upload (`POST /import`) validates the dump and stores it in a `transfer/` folder next to the database. The
   database does not change. State: `STAGED`.
2. The approval (`POST /import/apply`) only marks the dump as approved. The database still does not change. State:
   `APPROVED`.
3. At the next start of the service, before any connection to the database is opened, the dump is loaded into a new
   database. The old database files move to `transfer/backup/<time>/`. State: `APPLIED`, or `FAILED` when the load
   does not succeed. In that case the old database is put back.

## 2. Test environment

- A build of the branch `feat/database-transfer-import` or later.
- The JDBC URL points to an H2 **file** database (`jdbc:h2:file:...`). With an in-memory database every transfer
  operation answers `409`.
- The environment variable `TDM_DB_TRANSFER_KEY` holds at least 32 characters. While it is unset or shorter, every
  `/api/tdm/database/**` request that reaches the key check answers `404`.
- Optional variables:

  | Variable | Default | Meaning |
  | --- | --- | --- |
  | `TDM_DB_TRANSFER_MAX_SIZE` | `100MB` | Largest accepted upload (the gzip file) |
  | `TDM_DB_TRANSFER_DIR` | `transfer` next to the database file | Folder for dumps, status, and backups |
  | `TDM_DB_TRANSFER_BACKUPS` | `2` | How many database backups to keep |

- Copy the database folder before you start: an import replaces the whole database.
- For the two-service scenarios, start a second instance (B) with **different** data and a different database password.
- In the commands below `<KEY>` is the key, `<HOST>` is for example `localhost:8080`, `<FILE>` is a dump file name.
  Write `--data-binary "@<FILE>"` with the `@`. Without it curl sends the file name as the request body.
- `curl.exe -OJ` saves a response under the name from `Content-Disposition`.

## 3. Already verified by the developer

Run on November 16, 2026 against build `1.0.14-SNAPSHOT`, a single service, database `qa1_atp3_tdm_be`, in two passes:
the first on the build of the first import implementation, the second on the rebuilt service that includes the
filter for the key. Repeat these briefly as acceptance checks. Section 4 lists the full plan.

| What | Result |
| --- | --- |
| Key unset: export with and without the header | `404` |
| Key set: no header or a wrong value (export, upload, approve) | `403`, a `WARN` from `TransferKeyInterceptor` with the client address and path |
| Key shorter than 32 characters | The service starts and logs `WARN` "The database transfer is off: …"; upload answers `404` |
| Export with the right key | `200`, `application/gzip`, name `tdm-<database>-<yyyyMMdd-HHmmss>.sql.gz`, SQL with the header line `-- TDM3-DUMP v1 service=… h2=… created=… database=…` |
| Upload of the exported dump | `202`, `state=STAGED`, `transfer/pending.sql.gz` and `transfer/status.json` created |
| Approve with `restart=false` | `202`, `state=APPROVED`, the service keeps running and the database does not change |
| Approve with `restart=true` | `202`, the service exits after about 1.5 seconds ("Exiting so that the platform restarts the service…") |
| Start after the approval | `APPLIED`; the data is replaced; Liquibase and Quartz start without errors; the schedules are back |
| Files after the import | `backup/<time>/` with the old `.mv.db` and `.trace.db` and a `MOVED` marker. (The first run was on a build that also kept the dump in `applied/`; the current build deletes it) |
| Status operations | `GET` returns `NONE` before any upload; `DELETE` answers `204` for `NONE`, `STAGED`, `APPROVED`, and `APPLIED`, and keeps `APPLIED` |
| Approve with no confirmation, or with `confirm=replace_all_data` | `400` "Pass confirm=REPLACE_ALL_DATA to replace all data of this service.", the state stays `STAGED` |
| Approve with nothing staged | `409` "No dump is staged." |
| Upload with `application/octet-stream`, `application/x-gzip` | `202` for both |
| Upload over an `APPROVED` dump | `202`, the new dump replaces the old one, state `STAGED` |
| Cancel an `APPROVED` dump, then restart | State `NONE`; the database is not replaced and no new backup appears |
| Upload while another upload runs (slow first upload) | The second upload, an approve, and a cancel all answer `409` "Another import operation is running."; the first upload ends with `202` |
| Rejected dumps (25 variants) | `400`, the state and files stay unchanged. See section 4.4 for the list |
| Account statements in the dump | `CREATE USER`, `CREATE ROLE`, and `GRANT` lines are removed from the stored copy; `202` |
| A string value that contains `SELECT`, `CALL`, `--`, `;`, or `FILE_READ` | Accepted and kept (`202`) |
| Approved dump changed on disk after approval | After the restart: `FAILED`, "The staged dump changed after it was validated."; the dump moves to `failed/`; the data stays the same; a leftover `incoming.sql.gz.part` is deleted |
| Dump that passes validation but cannot be loaded (duplicate primary key) | After the restart: `FAILED` with the database error; the old database is put back; the data stays the same; the dump moves to `failed/` |
| Header value in the logs | The key value does not appear in `console.log` or in the start logs |
| Rebuilt service: no key, `POST /import` with `text/plain`, `GET /import/apply`, `DELETE /export`, `PUT /import`, `POST /export` | `403` "The transfer key is missing or wrong.", no stack trace. With the right key the same requests answer `415` or `405` |
| Rebuilt service: paths `/../`, `/./`, `;x=1`, `%65xport`, a trailing `/`, and the bare `/api/tdm/database`, no key | `403`. `//api/tdm/database/export` answers `400` from the web server before the filter |
| Rebuilt service with a 13-character key (transfer off): ten requests (five kinds, with and without the key) | `404` for all of them |
| Rebuilt service: export, upload, approve with `restart=true`, start | `APPLIED`; `transfer/` holds only `status.json` and `backup/<time>/`; no `*.sql.gz` file and no `applied/` |

Observations from that run, and what the build under test changes:

- The run found that a request with a wrong `Content-Type` (`POST /import`) or a wrong method (`GET /import/apply`) was
  answered by the web framework before the key check, with `415` or `405` and a stack trace, also while the transfer
  was off. The build under test checks the key in a servlet filter that runs first, so such a request gets `403` (key
  set) or `404` (transfer off). With the right key the `415` and `405` answers stay, and their stack-trace body is a
  setting of the whole service. Step 1.8 checks this.
- The build under test deletes the dump after a successful restore, so there is no `applied/` directory. `failed/` keeps
  the dump of the last failed restore. An `applied/` directory that an earlier build created can be deleted by hand.
- After a failed restore, `backup` in the status is `null`, because the old database files were moved back and the
  backup directory no longer exists.
- The `error` text of a failed restore contains the database message twice, in the system language and in English.
- The sizes differ: the export of the same data is slightly larger than `sizeBytes` in the status. The service
  compresses the validated dump again when it stores it, and `sizeBytes` is the size of the stored file.

## 4. Test plan

"Expect" lists everything to check. Unless stated otherwise, the body of an error answer is plain text with the reason.

### 4.1. Key protection

| # | Step | Expect |
| --- | --- | --- |
| 1.1 | Service without `TDM_DB_TRANSFER_KEY`. Send `GET /export`, `GET /import`, `DELETE /import`, and `POST /import/apply`, with and without the header | `404`, no hint about a key |
| 1.2 | The key has fewer than 32 characters | The service starts; the log has `WARN` "The database transfer is off: tdm.db.transfer.key (TDM_DB_TRANSFER_KEY) is unset or shorter than 32 characters."; requests answer `404` as in 1.1 |
| 1.3 | Key set, request without the header | `403`, "The transfer key is missing or wrong." |
| 1.4 | Key set, header with a wrong value (one character off, other case, extra space) | `403`, the same text as in 1.3 |
| 1.5 | Key set, right value | The request is processed |
| 1.6 | Several wrong requests in a row | Each one is logged as `WARN` with the client address and path. Neither the configured key nor the key a client sent appears in the log |
| 1.7 | Search every request log on the stand (ingress, gateway, if any) for the key value | Not found |
| 1.8 | No key in the request, and a wrong `Content-Type` (`POST /import` with `text/plain`) or a wrong HTTP method (`GET /import/apply`, `DELETE /export`) | `403` with a key set, `404` while the transfer is off; no stack trace in the body. With the right key the same requests answer `415` or `405` |
| 1.9 | Paths that try to dodge the match: `curl --path-as-is` with `/api/tdm/database/../database/export`, `//api/tdm/database/export`, `/api/tdm/database;x=1/export`, `/api/tdm/database/export/` | Each answers `403` or `404` without the key. None reaches a controller |

### 4.2. Export

| # | Step | Expect |
| --- | --- | --- |
| 2.1 | Export with the right key | `200`; `Content-Type: application/gzip`; `Content-Disposition: attachment; filename="tdm-<database>-<time>.sql.gz"` |
| 2.2 | Decompress the file | A valid gzip with UTF-8 SQL. The first line is `-- TDM3-DUMP v1 service=<version> h2=<H2 version> created=<UTC time> database=<name>` |
| 2.3 | Search the SQL for the database password and for `PASSWORD` | The password is absent. The only account statement is `CREATE USER IF NOT EXISTS "<user>" PASSWORD '' ADMIN` (empty password); grep also matches data values that contain the word |
| 2.4 | Check the content | Tables `TEST_DATA_TABLE_CATALOG`, `DATABASECHANGELOG`, `QRTZ_*`, and the user data tables are present |
| 2.5 | Export while the service works: create and change data in the UI at the same time | The export finishes, the service keeps working, and the dump is consistent as of the start of the export |
| 2.6 | Two exports at the same time | The second answers `409` "Another export is already running."; the first finishes normally |
| 2.7 | Close the download in the middle, then start a new export | The new export is accepted (`409` does not appear) |
| 2.8 | Service with an in-memory database | `409` "The database transfer needs an H2 file database, and jdbc.Url does not point to one." |
| 2.9 | Look for sensitive data in the dump (connection settings of external systems, tokens) | Note the findings. The dump carries all application data, including the connection settings stored in the tables, so protect the file like the database |

### 4.3. Upload (state `STAGED`)

Start with the status `NONE` (the `transfer/` folder is empty or missing) and a dump from step 2.1.

| # | Step | Expect |
| --- | --- | --- |
| 3.1 | `GET /import` before any upload | `200`, `state=NONE` |
| 3.2 | `POST /import` with `Content-Type: application/gzip` and the dump as the body | `202`; JSON with `state=STAGED`, `uploadedAt`, `sizeBytes`, `sha256` (64 hex characters), `dumpServiceVersion`, `dumpH2Version`, `warnings`; the other fields are `null` |
| 3.3 | Inspect `transfer/` | `pending.sql.gz` (valid gzip with SQL) and `status.json` equal to the response. There is no `incoming.sql.gz.part`. The SHA-256 of `pending.sql.gz` equals `sha256` in the status |
| 3.4 | Check the database after the upload | Unchanged and working |
| 3.5 | Upload with `application/octet-stream` and with `application/x-gzip` | `202` for both |
| 3.6 | Upload with `text/plain` or `application/json` | `415`; nothing is stored |
| 3.7 | Dump made by the same service version | `warnings` is empty |
| 3.8 | Dump from an older version of the same major version | `202`, `warnings` is empty |
| 3.9 | Dump from an older major version | `202`, one entry in `warnings` that names both versions |
| 3.10 | Dump from a newer version (raise `service=` in the first line) | `400` "The dump comes from … which is newer than this service (…). Downgrades are not supported."; the previous status and files stay |
| 3.11 | Dump without the header line | `400` "Invalid dump: The first line is not the TDM3 dump header …" |
| 3.12 | Header with `service=unknown` | `400` "The dump header has no valid service version: 'unknown'." |
| 3.13 | Body that is not gzip, and a truncated gzip | `400` "The upload is not a complete gzip-compressed UTF-8 text: …" |
| 3.14 | Gzip with bytes that are not valid UTF-8 | `400`, same prefix as in 3.13 |
| 3.15 | Dump larger than the limit. Set `TDM_DB_TRANSFER_MAX_SIZE=10KB` and upload the normal dump | `413`; `transfer/` has no new files and no `.part` file. (The developer did not run this step) |
| 3.16 | Two uploads at the same time. Throttle the first one with `curl --limit-rate 6k` | The second upload, and an approve and a cancel sent meanwhile, answer `409` "Another import operation is running."; the first upload ends with `202` |
| 3.17 | Upload over a `STAGED` dump, and over an `APPROVED` dump | `202`; the new dump replaces the old one; the state is `STAGED` and the earlier approval no longer applies |
| 3.18 | Upload with less free disk space than the upload plus five times its size | `507`; `transfer/` is unchanged |

### 4.4. Dump content filter

Edit the unpacked dump, compress it with gzip again, and upload it. Every case below answers `400` with a text that
starts "Invalid dump:". The stored dump and the status stay as they were.

| # | Add to the dump | Expect |
| --- | --- | --- |
| 4.1 | `CALL 1;` or `SELECT 1;` | `400` "The statement "…" is not allowed in a TDM3 dump." |
| 4.2 | `CREATE ALIAS …` (other than the standard `UUID_GENERATE_V4`), `CREATE TRIGGER …` | `400` |
| 4.3 | `RUNSCRIPT FROM '…';`, `SHUTDOWN;`, `EXECUTE IMMEDIATE …;` | `400` |
| 4.4 | `DROP TABLE …`, `DELETE FROM …`, `UPDATE …`, `TRUNCATE TABLE …`, `CREATE SCHEMA …` | `400` |
| 4.5 | A `--` comment inside a statement | `400` "A comment starts inside a statement." |
| 4.6 | A `/* … */` comment, `//`, or `$$ … $$` | `400` "The dump uses a block comment, a slash comment, or a dollar-quoted string, which a TDM3 dump never contains." |
| 4.7 | `FILE_READ(...)` in an `INSERT` value | `400` "… uses FILE_READ, which is not allowed in a TDM3 dump." |
| 4.8 | `INSERT … SELECT * FROM CSVREAD(…)` | `400` |
| 4.9 | `INSERT … VALUES ("CALL")` (a quoted name after `VALUES`) | `400` "An INSERT statement uses a quoted name after VALUES." |
| 4.10 | `JAVA_OBJECT` in a `CAST` | `400` "… uses JAVA_OBJECT, which is not allowed in a TDM3 dump." |
| 4.11 | `cAlL 1;` (mixed case) and `C/**/ALL 1;` | `400` |
| 4.12 | A string value that contains `SELECT`, `CALL`, `--`, `;`, or `FILE_READ` inside the quotes | `202`, the value is kept unchanged |
| 4.13 | `CREATE USER "EVIL" PASSWORD 'x' ADMIN;`, `CREATE ROLE …;`, `GRANT … TO "EVIL";` | `202`; the lines are absent from `pending.sql.gz`. After the restart the user `EVIL` does not exist and the service uses its own configured password |

### 4.5. Approval (state `APPROVED`)

| # | Step | Expect |
| --- | --- | --- |
| 5.1 | `POST /import/apply` without `confirm` | `400` "Pass confirm=REPLACE_ALL_DATA to replace all data of this service."; the state stays `STAGED` |
| 5.2 | `confirm=replace_all_data` or any other value | `400`, the same text; the state stays `STAGED` |
| 5.3 | `confirm=REPLACE_ALL_DATA&restart=false` while `STAGED` | `202`, `state=APPROVED`, `approvedAt` set; `status.json` updated; a `WARN` with the SHA-256 in the log; the service runs on and the database is unchanged |
| 5.4 | Approve while the state is `NONE`, `APPLIED`, or `FAILED` | `409` "No dump is staged." |
| 5.5 | Approve again while `APPROVED` | `202`, `approvedAt` is refreshed |
| 5.6 | `restart` omitted | Same as `restart=true`: the service exits after about 1.5 seconds. Run this only where the platform restarts the container |
| 5.7 | `restart=true` in Kubernetes | The pod restarts by itself; the log of the new start shows the database replacement; the status is `APPLIED` |

### 4.6. Cancel

| # | Step | Expect |
| --- | --- | --- |
| 6.1 | `DELETE /import` while `STAGED` | `204`; `pending.sql.gz` is gone; `GET /import` returns `NONE` |
| 6.2 | `DELETE /import` while `APPROVED`, then restart the service | `204`; after the restart the database is not replaced and no backup folder appears |
| 6.3 | `DELETE /import` while `NONE` | `204`, no error |
| 6.4 | `DELETE /import` while `APPLIED` or `FAILED` | `204`; the status stays as it was |

### 4.7. Apply at start (state `APPLIED`)

Use two services: A (the source) and B (the target), with different data, database passwords, and schedules.

| # | Step | Expect |
| --- | --- | --- |
| 7.1 | Export from A, upload to B, approve with `restart=false`, stop B, start B | The start log of B has, before the Hikari lines, `WARN` "Replacing the database with the approved dump. The previous files go to …\backup\<time>" and "The database was replaced with the imported dump." |
| 7.2 | `GET /import` on B | `state=APPLIED`, `finishedAt` set, `error=null`, `backup=<folder name>` |
| 7.3 | Data in B (UI and REST) | Every table, row, and setting equals A at the export time. Data created in B before the import is gone |
| 7.4 | `transfer/` of B | No `*.sql.gz` file is left: `pending.sql.gz` is deleted and there is no `applied/`; `backup/<time>/` holds the old `.mv.db` and `.trace.db` and the file `MOVED` |
| 7.5 | The database password of B | Unchanged (from the configuration of B), not the password of A |
| 7.6 | Start log of B | No Liquibase errors, no Quartz errors, the "Starting jobs for … configurations" lines have no exceptions, and the service answers requests |
| 7.7 | Schedules (cleanup, refresh, statistics) in B | The schedules of A exist and run; no job is stuck as running. Paused schedules stay paused |
| 7.8 | The `QRTZ_SCHEDULER_STATE` and `SHEDLOCK` tables of B | They hold no row of the scheduler instance that created the dump |
| 7.9 | A dump from an older major version, applied | `APPLIED`, the warning stays in `warnings`, and Liquibase applies the missing migrations at start |
| 7.10 | Start B once more | The database is not replaced again; the log has no replacement messages |
| 7.11 | Export from B after the import and compare with the dump of A | The data is equal, except for the time stamps and the scheduler state |
| 7.12 | Four imports in a row with `TDM_DB_TRANSFER_BACKUPS=2` | `transfer/backup/` keeps the two newest folders |

### 4.8. Failure and rollback

| # | Step | Expect |
| --- | --- | --- |
| 8.1 | Upload a dump that passes the filter but cannot be loaded (copy an `INSERT` of `DATABASECHANGELOGLOCK` so that the primary key repeats), approve, restart | The service starts on the **old** database. `GET /import`: `state=FAILED`, `error` explains the cause. The dump moves to `transfer/failed/`. The log has `ERROR` "The database restore failed" and "Put the previous database back from …". The data is unchanged |
| 8.2 | Remove the statements of `TEST_DATA_TABLE_CATALOG` or `DATABASECHANGELOG` from a dump and run the cycle | `FAILED`, the old database is back, the data is unchanged |
| 8.3 | After a `FAILED` result, upload a correct dump and run the cycle | Accepted; the cycle ends with `APPLIED` |
| 8.4 | After the approval and before the restart, replace `pending.sql.gz` with another gzip file | After the restart: `FAILED`, "The approved dump is not usable: The staged dump changed after it was validated."; the dump moves to `failed/`; the data is unchanged |
| 8.5 | Create `transfer/incoming.sql.gz.part` by hand and restart | The service deletes the file at start |
| 8.6 | Kill the process (`kill -9`) in the middle of the replacement, with a large dump | At the next start the `APPLYING` state counts as an interrupted replacement: the old database is restored and the status is `FAILED`. (Hard to hit by hand; unit tests cover it) |
| 8.7 | Make `transfer/` read-only, then upload | An error answer (`5xx`) with a clear text; the service keeps working |

### 4.9. The service around the transfer

| # | Step | Expect |
| --- | --- | --- |
| 9.1 | Use the service normally (UI, REST, create tables, search, schedules) while the state is `STAGED` and `APPROVED` | Everything works without limits |
| 9.2 | Check `docs/openapi.json` and Swagger | All five operations are described and the descriptions match the behavior |
| 9.3 | Check the audit log | The transfer operations are recorded with their action names |
| 9.4 | Helm deployment | Covered after stage 3 (the chart and the secret with the key); not part of this plan |

## 5. What to attach to the results

- The build version and the transfer variables (never the key itself).
- The response code and body for every step that expects an error.
- The service log from the start to the line "Started Main" for sections 4.7 and 4.8.
- The content of `transfer/` (file names and `status.json`) after each stage.
- For a defect: the dump when it holds no sensitive data, the exact command, and the time of the request.
