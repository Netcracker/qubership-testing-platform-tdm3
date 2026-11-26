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

### Shell setup for the commands

The commands in section 4 are for Bash with `curl`, `gzip`, and `sed`. On Windows, run them in Git Bash. A comment
such as `# 3.2` names the step that a command belongs to. Run these lines once in each new shell, from an empty working
directory, and replace the values:

```bash
export BASE=http://localhost:8080                  # the service under test
export KEY='<the value of TDM_DB_TRANSFER_KEY>'    # the key of that service
export TRANSFER_DIR='<database folder>/transfer'   # the transfer folder of that service
export LOG='<log file of the service>'

# Upload the file given as the argument. Keep the "@": without it cURL sends the filename as the request body.
upload() {
  curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" -H "Content-Type: application/gzip" \
    --data-binary "@$1" "$BASE/api/tdm/database/import"
  echo
}

# Print the import status.
status() {
  curl -sS -i -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/import"
  echo
}
```

`curl -i` prints the status line and the headers before the body.

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
| Key in the logs | The key does not appear in `console.log` or in the start logs |
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
| 1.7 | Search every request log on the stand (ingress, gateway, if any) for the key | Not found |
| 1.8 | No key in the request, and a wrong `Content-Type` (`POST /import` with `text/plain`) or a wrong HTTP method (`GET /import/apply`, `DELETE /export`) | `403` with a key set, `404` while the transfer is off; no stack trace in the body. With the right key the same requests answer `415` or `405` |
| 1.9 | Paths that try to dodge the match: `curl --path-as-is` with `/api/tdm/database/../database/export`, `//api/tdm/database/export`, `/api/tdm/database;x=1/export`, `/api/tdm/database/export/` | Each answers `403` or `404` without the key. None reaches a controller |

```bash
# 1.1 and 1.2: restart the service without the key, then with a short key. Every request answers 404.
curl -sS -i "$BASE/api/tdm/database/export"
curl -sS -i "$BASE/api/tdm/database/import"
curl -sS -i -X DELETE "$BASE/api/tdm/database/import"
curl -sS -i -X POST "$BASE/api/tdm/database/import/apply"
curl -sS -i -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/export"
curl -sS -i -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/import"
curl -sS -i -H "X-TDM-Transfer-Key: $KEY" -X DELETE "$BASE/api/tdm/database/import"
curl -sS -i -H "X-TDM-Transfer-Key: $KEY" -X POST "$BASE/api/tdm/database/import/apply"
grep -a "The database transfer is off" "$LOG"                                            # 1.2

# 1.3: no header
curl -sS -i "$BASE/api/tdm/database/import"

# 1.4: one character off, other case, extra space inside the value
curl -sS -i -H "X-TDM-Transfer-Key: ${KEY%?}#" "$BASE/api/tdm/database/import"
curl -sS -i -H "X-TDM-Transfer-Key: ${KEY^^}" "$BASE/api/tdm/database/import"
curl -sS -i -H "X-TDM-Transfer-Key: ${KEY:0:5} ${KEY:5}" "$BASE/api/tdm/database/import"

# 1.5: the right value
status

# 1.6: several wrong requests, then the log
for i in 1 2 3; do curl -sS -i -H "X-TDM-Transfer-Key: wrong-key-$i" "$BASE/api/tdm/database/import"; echo; done
grep -a "Rejected a database transfer request" "$LOG" | tail -n 3
grep -a -c -F -e "$KEY" -e "wrong-key-" "$LOG"                                           # expect 0

# 1.8: wrong content type and wrong method, first without the key, then with it
curl -sS -i -X POST -H "Content-Type: text/plain" --data "x" "$BASE/api/tdm/database/import"
curl -sS -i "$BASE/api/tdm/database/import/apply"
curl -sS -i -X DELETE "$BASE/api/tdm/database/export"
curl -sS -i -H "X-TDM-Transfer-Key: $KEY" -X POST -H "Content-Type: text/plain" --data "x" \
  "$BASE/api/tdm/database/import"
curl -sS -i -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/import/apply"
curl -sS -i -H "X-TDM-Transfer-Key: $KEY" -X DELETE "$BASE/api/tdm/database/export"

# 1.9: no key
for p in '/api/tdm/database/../database/export' '//api/tdm/database/export' \
         '/api/tdm/database;x=1/export' '/api/tdm/database/export/'; do
  echo "== $p"
  curl -sS -i --path-as-is "$BASE$p"
  echo
done
```

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

```bash
# 2.1: -D - prints the response headers, -o saves the body
curl -sS -D - -o dump.sql.gz -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/export"

# 2.2
gzip -t dump.sql.gz && echo "valid gzip"
gzip -dc dump.sql.gz > dump.sql
head -n 1 dump.sql

# 2.3: use the password of the database. The first command is meaningless when the password equals the database user.
grep -n -F -e '<database password>' dump.sql
grep -n "PASSWORD" dump.sql

# 2.4
grep -E '^CREATE CACHED TABLE' dump.sql

# 2.5: run the export while you create and change data in the UI
curl -sS -i -o busy.sql.gz -w 'HTTP %{http_code}\n' -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/export"

# 2.6: the first export must still run when the second one starts. This needs a database of many megabytes,
# because the service finishes the export of a small database at once, however slowly the client reads it.
curl -sS -o first.sql.gz --limit-rate 100k -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/export" &
sleep 1
curl -sS -i -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/export"
wait

# 2.7: break off the download after 2 seconds, then export again
curl -sS -o broken.sql.gz --limit-rate 100k --max-time 2 -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/export"
curl -sS -o again.sql.gz -w 'HTTP %{http_code}\n' -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/export"

# 2.8: against a service with an in-memory database
curl -sS -i -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/export"
```

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
| 3.19 | Upload a dump larger than 2 MB, the default request size limit of the web server | `202`, `state=STAGED`, `sizeBytes` above 2097152 |

The commands below use `dump.sql.gz` and `dump.sql` from steps 2.1 and 2.2.

```bash
curl -sS -i -X DELETE -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/import"   # start from NONE
status                                                                                # 3.1

# 3.2
curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" -H "Content-Type: application/gzip" \
  --data-binary "@dump.sql.gz" "$BASE/api/tdm/database/import"

# 3.3
ls -l "$TRANSFER_DIR"
cat "$TRANSFER_DIR/status.json"
gzip -t "$TRANSFER_DIR/pending.sql.gz" && echo "valid gzip"
sha256sum "$TRANSFER_DIR/pending.sql.gz"

# 3.5 and 3.6
for type in application/octet-stream application/x-gzip text/plain application/json; do
  echo "== $type"
  curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" -H "Content-Type: $type" \
    --data-binary "@dump.sql.gz" "$BASE/api/tdm/database/import"
  echo
done

# 3.7
upload dump.sql.gz

# 3.8 to 3.10 and 3.12: change service= in the header line. Pick versions that fit the version under test.
with_version() { sed "1s/service=[^ ]*/service=$1/" dump.sql | gzip > version.sql.gz; upload version.sql.gz; }
with_version 1.0.0      # 3.8: older, same major version
with_version 0.9.0      # 3.9: older major version
with_version 99.0.0     # 3.10: newer
with_version unknown    # 3.12

# 3.11
tail -n +2 dump.sql | gzip > noheader.sql.gz && upload noheader.sql.gz

# 3.13: not gzip, then a truncated gzip
upload dump.sql
head -c 1000 dump.sql.gz > truncated.sql.gz && upload truncated.sql.gz

# 3.14: bytes that are not valid UTF-8
{ head -n 1 dump.sql; printf '\377\376\n'; } | gzip > badutf8.sql.gz && upload badutf8.sql.gz

# 3.15: restart the service with TDM_DB_TRANSFER_MAX_SIZE=10KB. The dump of a nearly empty database is smaller than
# that, so check the size first and set a lower limit, for example 1KB, when needed.
ls -l dump.sql.gz
upload dump.sql.gz
ls -l "$TRANSFER_DIR"

# 3.16: a larger file keeps the first upload running for about 40 seconds. Do not approve this file.
{ cat dump.sql
  printf 'INSERT INTO "PUBLIC"."QA_FILLER" VALUES (\047'
  head -c 300000 /dev/urandom | base64 -w 0
  printf '\047);\n'
} | gzip > big.sql.gz
curl -sS -i --limit-rate 6k -X POST -H "X-TDM-Transfer-Key: $KEY" -H "Content-Type: application/gzip" \
  --data-binary "@big.sql.gz" "$BASE/api/tdm/database/import" > first-upload.txt &
sleep 3
upload dump.sql.gz
curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" \
  "$BASE/api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA&restart=false"
curl -sS -i -X DELETE -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/import"
wait
cat first-upload.txt

# 3.17: over a STAGED dump, then over an APPROVED one
upload dump.sql.gz
upload dump.sql.gz
curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" \
  "$BASE/api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA&restart=false"
upload dump.sql.gz
status

# 3.18: with the volume of the transfer folder nearly full
df -h "$TRANSFER_DIR"
upload dump.sql.gz

# 3.19: a file of about 4 MB. Cancel it afterwards, because it cannot be restored.
{ cat dump.sql
  printf 'INSERT INTO "PUBLIC"."QA_FILLER" VALUES (\047'
  head -c 4000000 /dev/urandom | base64 -w 0
  printf '\047);\n'
} | gzip > large.sql.gz
ls -l large.sql.gz
upload large.sql.gz
curl -sS -i -X DELETE -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/import"
```

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

`try_line` appends its argument to `dump.sql`, compresses the result, and uploads it. Run `status` before the first
case and after the last rejected one: the answers must be equal.

```bash
try_line() { { cat dump.sql; printf '%s\n' "$1"; } | gzip > case.sql.gz; upload case.sql.gz; }

try_line 'CALL 1;'                                                          # 4.1
try_line 'SELECT 1;'
try_line "CREATE ALIAS EVIL FOR 'java.lang.System.exit';"                   # 4.2
try_line "CREATE TRIGGER T BEFORE INSERT ON \"PUBLIC\".\"T\" CALL 'x.Y';"
try_line "RUNSCRIPT FROM '/tmp/x.sql';"                                     # 4.3
try_line 'SHUTDOWN;'
try_line "EXECUTE IMMEDIATE 'SHUTDOWN';"
try_line 'DROP TABLE "PUBLIC"."T";'                                         # 4.4
try_line 'DELETE FROM "PUBLIC"."T";'
try_line 'UPDATE "PUBLIC"."T" SET "A" = 1;'
try_line 'TRUNCATE TABLE "PUBLIC"."T";'
try_line 'CREATE SCHEMA EVIL;'
try_line 'INSERT INTO "PUBLIC"."T" VALUES (1 -- comment'                    # 4.5
try_line '/* comment */'                                                    # 4.6
try_line '// comment'
try_line 'INSERT INTO "PUBLIC"."T" VALUES ($$x$$);'
try_line "INSERT INTO \"PUBLIC\".\"T\" VALUES (FILE_READ('/etc/passwd'));"  # 4.7
try_line "INSERT INTO \"PUBLIC\".\"T\" SELECT * FROM CSVREAD('x.csv');"     # 4.8
try_line 'INSERT INTO "PUBLIC"."T" VALUES ("CALL");'                        # 4.9
try_line "INSERT INTO \"PUBLIC\".\"T\" VALUES (CAST(X'00' AS JAVA_OBJECT));"  # 4.10
try_line 'cAlL 1;'                                                          # 4.11
try_line 'C/**/ALL 1;'
status

# 4.12: accepted, and the value is unchanged in the stored dump. Do not approve it: table T does not exist.
try_line "INSERT INTO \"PUBLIC\".\"T\" VALUES ('SELECT CALL -- ; FILE_READ');"
gzip -dc "$TRANSFER_DIR/pending.sql.gz" | tail -n 1

# 4.13: accepted, and the stored dump holds no EVIL line. Approve and restart (5.3) to check the users.
{ cat dump.sql
  echo "CREATE USER \"EVIL\" PASSWORD 'x' ADMIN;"
  echo 'CREATE ROLE "EVIL_ROLE";'
  echo 'GRANT "EVIL_ROLE" TO "EVIL";'
} | gzip > accounts.sql.gz && upload accounts.sql.gz
gzip -dc "$TRANSFER_DIR/pending.sql.gz" | grep EVIL                         # expect no output
```

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

```bash
upload dump.sql.gz                                                          # state STAGED

# 5.1 and 5.2
curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/import/apply"
curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" \
  "$BASE/api/tdm/database/import/apply?confirm=replace_all_data"
status

# 5.3. Run it a second time for 5.5.
curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" \
  "$BASE/api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA&restart=false"
cat "$TRANSFER_DIR/status.json"
grep -a "is approved" "$LOG" | tail -n 1

# 5.4: with nothing staged
curl -sS -i -X DELETE -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/import"
curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" \
  "$BASE/api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA&restart=false"

# 5.6: restart omitted. The service exits and the database is replaced at the next start.
upload dump.sql.gz
curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" \
  "$BASE/api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA"

# 5.7: in Kubernetes
upload dump.sql.gz
curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" \
  "$BASE/api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA&restart=true"
kubectl get pods -n '<namespace>' -w
status                                                                      # after the pod is ready again
```

### 4.6. Cancel

| # | Step | Expect |
| --- | --- | --- |
| 6.1 | `DELETE /import` while `STAGED` | `204`; `pending.sql.gz` is gone; `GET /import` returns `NONE` |
| 6.2 | `DELETE /import` while `APPROVED`, then restart the service | `204`; after the restart the database is not replaced and no backup folder appears |
| 6.3 | `DELETE /import` while `NONE` | `204`, no error |
| 6.4 | `DELETE /import` while `APPLIED` or `FAILED` | `204`; the status stays as it was |

```bash
# 6.1
upload dump.sql.gz
curl -sS -i -X DELETE -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/import"
status
ls "$TRANSFER_DIR"

# 6.2: cancel an APPROVED dump, then restart the service and compare the backup folders
upload dump.sql.gz
curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" \
  "$BASE/api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA&restart=false"
ls "$TRANSFER_DIR/backup"
curl -sS -i -X DELETE -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/import"
# restart the service here
status
ls "$TRANSFER_DIR/backup"

# 6.3 and 6.4: the same request in the states NONE, APPLIED, and FAILED
curl -sS -i -X DELETE -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/import"
status
```

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
| 7.11 | Export from B after the import and compare with the dump of A | The data is equal, except for the timestamps and the scheduler state |
| 7.12 | Four imports in a row with `TDM_DB_TRANSFER_BACKUPS=2` | `transfer/backup/` keeps the two newest folders |

In this section and in section 4.8, `BASE`, `KEY`, `TRANSFER_DIR`, and `LOG` describe service B. Set two more
variables for service A:

```bash
export SRC='http://<host of A>:<port>'
export SRC_KEY='<the key of A>'

# 7.1: export from A, upload to B, approve, then stop and start B
curl -sS -o a.sql.gz -w 'HTTP %{http_code}\n' -H "X-TDM-Transfer-Key: $SRC_KEY" "$SRC/api/tdm/database/export"
upload a.sql.gz
curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" \
  "$BASE/api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA&restart=false"
# stop B and start it here
grep -a -E "Replacing the database|The database was replaced|Hikari" "$LOG"

status                                                                      # 7.2
ls -R "$TRANSFER_DIR"                                                       # 7.4
grep -a -E "ERROR|Starting jobs for" "$LOG"                                    # 7.6
curl -sS -i "$BASE/api/tdm/versions/backend"                                # 7.6

# 7.9: a dump of A that claims an older major version
gzip -dc a.sql.gz | sed '1s/service=[^ ]*/service=0.9.0/' | gzip > old.sql.gz && upload old.sql.gz
curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" \
  "$BASE/api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA&restart=false"
# restart B, then run: status

# 7.11: compare the data statements of the two dumps
curl -sS -o b.sql.gz -w 'HTTP %{http_code}\n' -H "X-TDM-Transfer-Key: $KEY" "$BASE/api/tdm/database/export"
diff <(gzip -dc a.sql.gz) <(gzip -dc b.sql.gz)

# 7.12: repeat four times, restarting B after each approval
upload a.sql.gz
curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" \
  "$BASE/api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA&restart=false"
ls "$TRANSFER_DIR/backup"
```

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

Each case ends the same way: `approve`, restart the service, and run `status`. `dump.sql` is an export of the service
under test (steps 2.1 and 2.2).

```bash
approve() {
  curl -sS -i -X POST -H "X-TDM-Transfer-Key: $KEY" \
    "$BASE/api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA&restart=false"
  echo
}

# 8.1: a repeated primary key
{ cat dump.sql
  echo 'INSERT INTO "PUBLIC"."DATABASECHANGELOGLOCK" VALUES (1, FALSE, NULL, NULL);'
} | gzip > duplicate.sql.gz && upload duplicate.sql.gz && approve
# restart the service, then:
status
ls "$TRANSFER_DIR/failed"
grep -a -E "The database restore failed|Put the previous database back" "$LOG"

# 8.2: remove one required table. Run the cycle again with DATABASECHANGELOG as the table.
table=TEST_DATA_TABLE_CATALOG
awk -v t="\"PUBLIC\".\"$table\"" '
  (/^CREATE CACHED TABLE / || /^INSERT INTO / || /^ALTER TABLE /) && index($0, t) { skip = 1 }
  !skip
  /;\r?$/ { skip = 0 }
' dump.sql | gzip > missing.sql.gz && upload missing.sql.gz && approve

# 8.3
upload dump.sql.gz && approve

# 8.4: replace the approved dump before the restart
upload dump.sql.gz && approve
echo "-- changed" | gzip > "$TRANSFER_DIR/pending.sql.gz"

# 8.5
touch "$TRANSFER_DIR/incoming.sql.gz.part"
# restart the service, then:
ls "$TRANSFER_DIR"

# 8.6: with a large dump, approve, start the service, and kill it while the log shows the replacement
kill -9 '<process id of the service>'

# 8.7: Linux. Restore the permissions afterwards.
chmod a-w "$TRANSFER_DIR"
upload dump.sql.gz
chmod u+w "$TRANSFER_DIR"
```

### 4.9. The service around the transfer

| # | Step | Expect |
| --- | --- | --- |
| 9.1 | Use the service normally (UI, REST, create tables, search, schedules) while the state is `STAGED` and `APPROVED` | Everything works without limits |
| 9.2 | Check `docs/openapi.json` and Swagger | All five operations are described and the descriptions match the behavior |
| 9.3 | Check the audit log | The transfer operations are recorded with their action names |
| 9.4 | Helm deployment | Covered after stage 3 (the chart and the secret with the key); not part of this plan |

```bash
# 9.2: the paths in the OpenAPI description of the running service
curl -sS "$BASE/v3/api-docs" | grep -oE '"/api/tdm/database[^"]*"'
```

## 5. What to attach to the results

- The build version and the transfer variables (never the key itself).
- The response code and body for every step that expects an error.
- The service log from the start to the line "Started Main" for sections 4.7 and 4.8.
- The content of `transfer/` (filenames and `status.json`) after each stage.
- For a defect: the dump when it holds no sensitive data, the exact command, and the time of the request.
