# Database transfer

The database transfer moves the whole database of one TDM3 service into another TDM3 service through the REST API:
every test data table, every setting, and every schedule. Use it to move a project to another environment when you
cannot reach the volume that holds the H2 files. An import replaces all data of the target service. The per-table
import and export of the [test data API](api.md#endpoint-groups) move one table at a time and are not part of this
feature.

The transfer is off until you set a [transfer key](#enable-the-transfer). The five operations are in
[Database transfer API](api.md#database-transfer-api), and the errors and the recovery steps are in
[Troubleshooting](troubleshooting.md#database-transfer).

## Enable the transfer

Set the key on both services, the source and the target. The key is a shared secret of at least 32 characters, and the
two services need not have the same key. Every request sends the key of the service it calls in the
`X-TDM-Transfer-Key` header.

- **Outside Helm:** set [`TDM_DB_TRANSFER_KEY`](configuration.md#database-transfer) in the environment of the service.
- **With the Helm chart:** set `atp3tdm.dbTransfer.enabled` to `true`. See
  [Deploy the key with Helm](#deploy-the-key-with-helm).

While the key is unset or shorter than 32 characters, every transfer operation answers `404`, and the service logs one
warning at startup that says so. The service must run on an H2 file database. With any other `JDBC_URL`, every
operation answers `409`.

Before the first transfer, check that the ingress or gateway in front of both services accepts a request body as large
as the largest dump and waits longer than the longest export. For NGINX that is `proxy-body-size` and the read timeout.

## Move a database

The examples use `curl`. Replace `<source>` and `<target>` with the addresses of the services, add the
[gateway prefix](api.md#base-path-and-the-atp-gateway) where your deployment has one, and set `SOURCE_KEY` and
`TARGET_KEY` to the two keys.

1. **Export the source.** The response is a gzip file that the service streams while it reads the database, so it has no
   length. `-OJ` saves it under the filename from the response, `tdm-<database>-<yyyyMMdd-HHmmss>.sql.gz`.

   ```bash
   curl -sS -OJ -H "X-TDM-Transfer-Key: $SOURCE_KEY" https://<source>/api/tdm/database/export
   ```

   Only one export runs at a time. A second request gets `409`.

2. **Upload the dump to the target.** The body is the raw gzip file and not a multipart form. The service checks the
   dump while it receives it, and the database of the target does not change.

   ```bash
   curl -sS -X POST -H "X-TDM-Transfer-Key: $TARGET_KEY" -H "Content-Type: application/gzip" \
     --data-binary "@tdm-<database>-<timestamp>.sql.gz" https://<target>/api/tdm/database/import
   ```

   A valid dump gets `202` and the status `STAGED`. Read the `warnings` in the answer: a dump from an older major
   version of the service is accepted with a warning. An invalid dump gets `400` with the reason, and nothing is stored.

3. **Approve the import.** The call confirms that you want to replace all data of the target. With `restart=true`, the
   default, the service exits about 1.5 seconds after it answers, and the platform has to start it again. Pass
   `restart=false` to approve the dump and restart the service yourself later.

   ```bash
   curl -sS -X POST -H "X-TDM-Transfer-Key: $TARGET_KEY" \
     "https://<target>/api/tdm/database/import/apply?confirm=REPLACE_ALL_DATA&restart=false"
   ```

   The status is `APPROVED` and waits for the next start. `DELETE /api/tdm/database/import` withdraws the dump before
   that.

4. **Restart the target** unless `restart=true` did it. The service replaces the database while it starts, before it
   opens any connection to the database, and then starts as usual.

5. **Check the result.**

   ```bash
   curl -sS -H "X-TDM-Transfer-Key: $TARGET_KEY" https://<target>/api/tdm/database/import
   ```

   `"state": "APPLIED"` means the target now holds the data of the source. `"state": "FAILED"` means the service kept
   its old data, and `error` says why; see [Troubleshooting](troubleshooting.md#database-transfer).

The states of the status:

| State      | Meaning                                                                                                              |
|------------|----------------------------------------------------------------------------------------------------------------------|
| `NONE`     | No dump is staged.                                                                                                   |
| `STAGED`   | A checked dump waits for approval. A new upload replaces it.                                                         |
| `APPROVED` | The dump replaces the database at the next start of the service.                                                     |
| `APPLYING` | A restore is running, or the process died during one. The next start rolls it back.                                  |
| `APPLIED`  | The last restore succeeded. The state stays until the next upload.                                                   |
| `FAILED`   | The last restore did not run or failed, and the service runs on its old data. The state stays until the next upload. |

## What an import changes

- **Data and settings.** All tables of the target are replaced by the tables of the dump, including the catalog of test
  data tables, the settings of cleanup, refresh, and statistics, and the Quartz schedules.
- **Schedules.** The schedules of the source run on the target after the import, and the state of jobs that the source
  was running at export time is reset. If the source keeps running, both services run the same schedules and send the
  same statistics emails. Stop or disable the source when the move is final.
- **Database login.** The target keeps its own `TDM_DB_USER` and `TDM_DB_PASSWORD`. The dump carries neither a password
  nor an account.
- **Versions.** A dump from a newer version of the service than the target is rejected with `400`, because a downgrade
  is not supported. A dump from the same or an older version is accepted, and Liquibase brings the schema forward at
  the next start. A dump from an older major version is accepted with an entry in `warnings`.
- **Old database.** The files that the import replaces stay in `transfer/backup/<yyyyMMdd-HHmmss>/` next to the
  database. The service keeps the newest [`TDM_DB_TRANSFER_BACKUPS`](configuration.md#database-transfer) of them, two by
  default. Each is as large as the database it replaced, so size the volume for them.

## Table expiry and transferred data

TDM3 deletes a test data table when its last use is older than the retention period, one month by default (see
[Data retention](configuration.md#data-retention)). A transfer neither disables this cleanup nor changes the last use of
a table, and a job that missed its time runs when the service starts. In a test, a restored database whose cleanup was
19 days overdue lost 8 of its 11 tables during the first start. Expect these cases:

1. **The source has a short retention period.** It has already deleted every table older than its period, so the dump
   holds only what is left, and that can be little.
2. **The dump is old.** A table whose last use is older than the retention period of the target is deleted on the first
   start after the import, or at the next midnight at the latest. A dump that you stored for weeks can bring back few
   tables or none.
3. **The retention settings differ.** The per-project timeouts are part of the dump, so those of the source replace
   those of the target. The default period comes from the environment of the target and is not part of the dump.
4. **The dump waits for approval.** Staging and approval do not change the last use of a table, so the time a dump waits
   counts against its tables.
5. **You roll back from `backup/`.** The database in a backup is as old as it was at the import, so the first start
   after the rollback deletes the tables that expired in between.

## Protect the dump

A dump is plain SQL that holds the data of the source service, including the connection settings that the service
stores in its tables, such as the passwords of the database connections of a dynamic environment. Treat it like the
database file:

- Move it over HTTPS only, and do not attach it to a ticket or put it on a shared drive.
- The key protects the endpoints, not the file. Anyone who holds a dump can read it.
- A successful import deletes the staged dump from the target. The dump of the last failed import stays in
  `transfer/failed/` for analysis, and the replaced database files stay in `transfer/backup/`. Delete them when you no
  longer need them.
- The service accepts only the statements that an H2 `SCRIPT` of TDM3 contains. A dump with a `CALL`, `SELECT`,
  `RUNSCRIPT`, `FILE_READ`, or a comment inside a statement is rejected, and the account statements (`CREATE USER`,
  `CREATE ROLE`, `GRANT`) are removed at upload. A dump still runs as SQL under the account of the service, so give
  the key only to people who may replace the data of the service.

## Deploy the key with Helm

The [Helm chart](../README.md#deploy-with-helm) keeps the key in a Kubernetes Secret, so the key is not in the pod
specification. While `atp3tdm.dbTransfer.enabled` is `false`, the chart renders no Secret and no variable. When it is
`true`, the key comes from the first of these sources that is set:

| Helm value                          | Meaning                                                                                                                                   |
|-------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `atp3tdm.dbTransfer.existingSecret` | The name of a Secret that you manage. It holds the key in the entry `TDM_DB_TRANSFER_KEY`. The chart creates nothing.                     |
| `atp3tdm.dbTransfer.key`            | A key that you supply, of at least 32 characters. The chart stores it in the Secret `<SERVICE_NAME>-db-transfer`.                         |
| both empty                          | The chart generates a key of 48 random characters and stores it in the Secret `<SERVICE_NAME>-db-transfer`. Later upgrades keep that key. |

`atp3tdm.dbTransfer.maxSize`, `100MB` by default, sets `TDM_DB_TRANSFER_MAX_SIZE`. To raise it above
`atp3tdm.maxHttpPostSize`, raise that value too.

The chart never prints a generated key, because Helm output reaches CI logs. Read it from the cluster:

```bash
kubectl get secret <SERVICE_NAME>-db-transfer -n <namespace> -o jsonpath='{.data.TDM_DB_TRANSFER_KEY}' | base64 -d
```

To rotate a generated key, delete the Secret, run `helm upgrade`, and restart the pod. The Secret survives
`helm uninstall`, so a reinstall finds the old key; delete it to start over.

- A tool that renders the chart without a cluster, such as `helm template` or Argo CD, cannot look up the Secret and
  generates a new key at every render. Set `key` or `existingSecret` for such a deployment.
- The service reads the key at startup. A change of `key` restarts the pod by itself. A change inside an
  `existingSecret` needs a manual restart.
- A key shorter than 32 characters fails the render.
- On the `COMPOSE` platform, which has no Secrets, set `atp3tdm.dbTransfer.key`. The chart does not generate one.
- Anyone who can read Secrets in the namespace can read the key.

The chart also adds `X-TDM-Transfer-Key` to
[`atp3tdm.atpHttpLoggingHeadersIgnore`](configuration.md#monitoring-and-logging), so the header stays out of the HTTP
log.

## Operational notes

- **Disk.** During a restore the volume holds the replaced database, the restored database, and the staged dump. In
  measurements the restored file was 4.2 to 4.6 times the size of the compressed dump and at most half the size of the
  file it came from, so twice the size of the database plus the dump should be enough, plus the backups that stay.
  An upload fails with `507` when the volume has less free space than the import needs, and the answer states both
  numbers.
- **Memory.** The upload and the export stream, so memory use does not grow with the size of the database.
- **Restart.** `restart=true` ends the process with status 0. A Kubernetes Deployment starts the container again. A
  plain `docker run` without a restart policy does not, so restart it yourself.
- **Upload size.** [`TDM_DB_TRANSFER_MAX_SIZE`](configuration.md#database-transfer) limits the compressed file. It is
  independent of `MAX_FILE_SIZE` and `MAX_REQUEST_SIZE`. A body over
  [`MAX_HTTP_POST_SIZE`](configuration.md), 101 MB by default, gets `400` with no body, so raise both to accept a
  larger dump.
