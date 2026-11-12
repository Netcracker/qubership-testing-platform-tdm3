# Transfer the whole database through the REST API

## Status

Proposed

<!-- markdownlint-disable MD001 -->
#### Date

#### Owner

[kagw95](https://github.com/kagw95)

#### Participants and approvers

TBD: the TDM3 maintainers and the teams that deploy TDM3 per project.

#### Related ADRs

None.
<!-- markdownlint-enable MD001 -->

## Context

TDM3 keeps all of its state in one file-based H2 database: the test data tables, the catalog, the cleanup and refresh
settings, the dynamic environments and systems, and the Quartz and ShedLock tables. The service creates the database on
the first start when the file is missing.

A project sometimes moves its TDM3 to another cloud or another environment and wants to start from the old data, not
from an empty database. The move needs the whole database, not a part of it.

Copying the database files is technically enough, but the people who ask for the move are project users. They have
access to the REST API of the service and no access to the file system where the files live.

TDM3 runs without access control. It is deployed once per project, or once per project environment, with the
`disable-security` Spring profile and `KEYCLOAK_ENABLED=false`. The `@PreAuthorize` annotations in the controllers check
nothing in this profile. Replacing the whole database is the most destructive operation the service could offer, and
the existing model gives no way to restrict it to an administrator.

The H2 console (`/h2-console`) is enabled, but it accepts connections from the local host only, so it is not a remote
way to run SQL today.

## Decision

We will add a database transfer feature to the backend, exposed as REST endpoints under `/api/tdm/database`:

- **Export** streams the whole database as a gzip-compressed SQL script produced by the H2 `SCRIPT` command.
- **Import** has two steps. The upload validates the script and stores it in a staging directory on the volume that
  holds the database; it changes nothing else. A separate request approves the replacement and restarts the service.
  The next start applies an approved script before any connection pool exists: the old database files are kept as a
  backup, the script is run into a new database, and a failure restores the backup. A restart without an approval never
  applies a staged script.
- **Validation** treats the script as executable SQL. The import rejects a script that contains statements outside the
  kinds a TDM3 dump holds, such as `CREATE ALIAS`, triggers, or file access functions.
- **Access** is restricted by a transfer key, a secret that the deployer passes to the service through the environment.
  Without the key the endpoints answer `404`. With it, a request must carry the key in a header. The key protects the
  transfer endpoints only; it does not add access control to the rest of the service.

The detailed design is in [Database transfer design](../design/database-transfer.md).

### Justification

The REST API is the only channel the users of a project have, so the transfer has to go through it.

Staging the import and applying it at startup keeps a replacement of the database away from the running service. The
Hikari pool, the Quartz scheduler, ShedLock, and Liquibase all use the same database file while the service runs.
Replacing the data under them needs a coordinated stop of every one of them, and any mistake leaves a half-restored
database. At startup none of them is running yet, and the previous files are still on disk for a rollback.

A transfer key is the smallest mechanism that matches the deployment model. The deployer already controls the
environment of the service, so the deployer is the one person who can authorize a replacement of the data. The key needs
no user store and no identity provider. It keeps the feature off until someone enables it on purpose.

Alternatives that we considered:

| Alternative | Why we rejected it |
| --- | --- |
| Archive the database files and copy them | Needs file system access that project users do not have. Copying the files of a running H2 database can also produce an inconsistent copy. |
| Run the script into the live database (`RUNSCRIPT` after `DROP ALL OBJECTS`) | Hikari, Quartz, and ShedLock keep working against the database while it is rebuilt. A failure in the middle leaves a partial database and no way back. |
| H2 `BACKUP TO` and restore the binary file | The file format depends on the H2 version, and a restore still needs a replaced file under a running database. |
| Per-table export and import that already exist | They move one table at a time and lose the catalog, the settings, and the scheduler state. |
| A startup property that points at a dump to load | Project users cannot change the configuration or reach the storage the dump would sit in. |
| Turn on Keycloak and role checks | A much larger change that reverses the decision to deploy without access control. It would also need a role model that does not exist. |
| Leave the transfer endpoints without protection | Anyone who can reach the service could read or replace all data. The H2 console is not a precedent, because it is limited to the local host. |

## Consequences

Positive:

- A project can move its TDM3 data between clouds without any access to the file system.
- A failed or interrupted import leaves the previous database in place.
- A dump from an older version of the service loads into a newer one, because Liquibase brings the schema forward on
  the next start. Downgrades are not supported.

Negative:

- An import needs a restart of the service. The approval request restarts it by default, and that works only where the
  platform starts the process again. Otherwise someone with access to the deployment has to restart it.
- The transfer key becomes a new secret that deployers have to create, store, and hand to the people who run transfers.
  Whoever holds it can read and replace all data.
- An export and an import need disk space for several copies of the database: the staged script, the backup, and the new
  database. A large database needs a volume sized for it.
- The REST API grows by five operations: export, upload, status, cancel, and approve.
- A script is code. The statement filter lowers the risk of running a crafted script but does not remove it, so the
  transfer key must be treated as an administrator credential.

Neutral:

- The feature works only for a file-based H2 database. A service configured with an in-memory database or a remote
  database refuses the operations.
- Access control for the rest of the service is unchanged. If TDM3 gains real authorization later, the transfer key can
  be replaced by a role check in one place.
