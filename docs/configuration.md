# Configuration reference

The TDM3 backend reads its settings from environment variables. Each variable on this page is either mapped to a
Spring property in [`application.properties`](../qubership-atp-tdm-backend/src/main/config/application.properties) or
read by the container start script [`run.sh`](../qubership-atp-tdm-backend/src/main/resources/run.sh). A variable
that `application.properties` maps can also be passed as a Java system property, for example `-DJDBC_URL=...`.

The tables use these columns:

- **Default** is the value the service uses when the variable is unset. `unset` means there is no default.
- **Helm** is the `values.yaml` key the chart in [`deployments/charts/atp3-tdm-be`](../deployments/charts/atp3-tdm-be)
  sets the variable from, and the value of that key in the chart. A dash means the chart does not set the variable,
  so a Helm deployment runs with the service default.

## Required variables

The database variables have no default. Set these three when you run the service outside the Docker image; the image
sets them to the values in the [Database](#database) table.

- [`JDBC_URL`](#database)
- [`TDM_DB_USER`](#database)
- [`TDM_DB_PASSWORD`](#database)

The container start script `run.sh` also requires [`KEYCLOAK_REALM` and `IDENTITY_PROVIDER_URL`](#ui-and-keycloak), and
exits before the JVM starts when either is empty. The Helm chart sets both.

## General

| Variable                 | Default            | Helm                                         | Description                                                                                             |
|--------------------------|--------------------|----------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `SERVICE_NAME`           | `atp-tdm`          | `SERVICE_NAME`: `atp3-tdm-be`                | Spring application name. The service registers under it in Eureka and tags its metrics with it.         |
| `ACTIVE_PROFILES_SPRING` | `disable-security` | `ACTIVE_PROFILES_SPRING`: `disable-security` | Active Spring profiles. TDM3 runs with `disable-security`; see the [Readme](../README.md#about).        |
| `LOG_LEVEL`              | `INFO`             | `atp3tdm.logLevel`: `INFO`                   | Log level of the `org.qubership.atp.tdm` loggers and of the root logger.                                |
| `LOCALE_RESOLVER`        | `en`               | `atp3tdm.localeResolver`: `en`               | Language of a request that has no `Accept-Language` header.                                             |
| `ATP_TDM_URL`            | `localhost:8080`   | -                                            | Base URL of the TDM UI in the links to project and table pages that ATP actions return.                 |
| `MAX_FILE_SIZE`          | `100MB`            | `atp3tdm.maxFileSize`: `100MB`               | Largest file the service accepts in one upload, such as an Excel import. Uses Spring `DataSize` syntax. |
| `MAX_REQUEST_SIZE`       | `100MB`            | `atp3tdm.maxRequestSize`: `100MB`            | Largest multipart request the service accepts. Uses Spring `DataSize` syntax.                           |
| `SWAGGER_ENABLED`        | `true`             | `SWAGGER_ENABLED`: `false`                   | Turns the springdoc OpenAPI endpoint on or off.                                                         |

## Projects

At every startup, the service saves settings for each project that `PROJECTS_INFO` lists: the time zone, the date and
time formats, and the table expiration timeout from the other variables in this table. The saved settings replace the
earlier settings of those projects, so a change takes effect after a restart.

| Variable                             | Default                                                     | Helm                                                                                                  | Description                                                                                                                                     |
|--------------------------------------|-------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `PROJECTS_INFO`                      | `{ 'c9858f9f-f87b-3eae-aa86-bfa92856d55c': 'Test Project'}` | `atp3tdm.gitProjectsInfo`: `{}`, which renders `{"b0c1fd9e-19a7-4156-90e0-f04028a58720":"MyProject"}` | Map of project IDs (UUID) to project names, as a SpEL map literal or a JSON object.                                                             |
| `PROJECTS_TIME_ZONE`                 | `GMT+03:00`                                                 | -                                                                                                     | Time zone of the project, used to show dates.                                                                                                   |
| `PROJECTS_DATE_FORMAT`               | `d MMM yyyy`                                                | -                                                                                                     | Date format of the project, in `java.text.SimpleDateFormat` syntax.                                                                             |
| `PROJECTS_TIME_FORMAT`               | `hh:mm:ss a`                                                | -                                                                                                     | Time format of the project, in `java.text.SimpleDateFormat` syntax.                                                                             |
| `PROJECTS_EXPIRATION_MONTHS_TIMEOUT` | `1`                                                         | -                                                                                                     | Months after the last use of a test data table before the service deletes it. `0` applies [`DEFAULT_TABLE_EXPIRATION_MONTHS`](#data-retention). |

## Database

TDM3 keeps its data in an H2 file database. The Quartz scheduler stores its jobs in the same database.

| Variable                             | Default | Helm                                                                       | Description                                                                                                                                                                                                    |
|--------------------------------------|---------|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `JDBC_URL`                           | unset   | Built from `atp3tdm.h2DbAddr`, `atp3tdm.tdmDb`, and `atp3tdm.h2DbProperty` | JDBC URL of the H2 database, for example `jdbc:h2:file:./database/atptdm;MODE=PostgreSQL`. A relative path is resolved against the working directory. The Docker image sets `jdbc:h2:file:./database/atptdm;`. |
| `TDM_DB_USER`                        | unset   | `atp3tdm.tdmDbUser`, stored in the `<SERVICE_NAME>-secrets` secret         | Database user. The Docker image sets `tdmadmin`.                                                                                                                                                               |
| `TDM_DB_PASSWORD`                    | unset   | `atp3tdm.tdmDbPassword`, stored in the `<SERVICE_NAME>-secrets` secret     | Database password. The Docker image sets `tdmadmin`.                                                                                                                                                           |
| `LIQUIBASE_LAUNCH_ENABLED`           | `true`  | `atp3tdm.liquibaseLaunchEnabled`: `true`                                   | Runs the Liquibase changelog at startup. The changelog creates and updates the service schema.                                                                                                                 |
| `SERVICE_ENTITIES_MIGRATION_ENABLED` | `false` | `atp3tdm.serviceEntitiesMigrationEnabled`: `false`                         | Runs the `SEND_SERVICE_ENTITIES_TO_KAFKA` changeset. The changeset does nothing, because the Kafka migration is disabled.                                                                                      |

H2 creates the database with the user and password of the first connection. Keep `TDM_DB_USER` and `TDM_DB_PASSWORD`
unchanged for an existing database file.

In the Helm chart, the database file lives on a persistent volume:

| Helm value                 | Default               | Description                                                                                                                                              |
|----------------------------|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `atp3tdm.h2DbAddr`         | `database`            | Directory of the database file, relative to `/atp-tdm`. The volume is mounted at `/atp-tdm/database`, so another value puts the file outside the volume. |
| `atp3tdm.tdmDb`            | empty                 | Name of the database file. Empty means `<namespace>_<SERVICE_NAME>` with hyphens replaced by underscores.                                                |
| `atp3tdm.h2DbProperty`     | `MODE=PostgreSQL`     | Settings appended to the JDBC URL after `;`.                                                                                                             |
| `atp3tdm.tdmDbUser`        | empty                 | Database user. Empty means the same value as the database filename.                                                                                      |
| `atp3tdm.tdmDbPassword`    | empty                 | Database password. Empty means the same value as the database filename.                                                                                  |
| `atp3tdm.storageSize`      | `1Gi`                 | Size of the persistent volume claim `<SERVICE_NAME>-pvc`.                                                                                                |
| `atp3tdm.storageClassName` | `csi-sc-cinderplugin` | Storage class of the persistent volume claim.                                                                                                            |

## Data retention

The service deletes test data tables that nobody uses, and later deletes the history records of those tables. Cron
expressions use the Quartz format, which starts with a seconds field.

| Variable                              | Default           | Helm | Description                                                                                                                                                            |
|---------------------------------------|-------------------|------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `TABLE_EXPIRATION_CRON`               | `0 0 0 ? * * *`   | -    | When the job that deletes expired tables runs. The default runs it daily at midnight.                                                                                  |
| `DEFAULT_TABLE_EXPIRATION_MONTHS`     | `1`               | -    | Months after the last use before a table is deleted, for tables of a project without its own [expiration timeout](#projects) and for tables of unknown projects.       |
| `CLEAN_REMOVED_TABLES_HISTORY_MONTHS` | `0 0 0 ? * 1/7 *` | -    | When the job that deletes history records of deleted tables runs. Despite the name, the value is a cron expression. The default runs the job every Sunday at midnight. |
| `DEFAULT_CLEAN_TABLES_MONTHS`         | `6`               | -    | Months the service keeps the history record of a deleted table.                                                                                                        |

## Data refresh and cleanup

Refresh and cleanup settings of a table run SQL queries against the database of the table's environment.

| Variable                         | Default | Helm                                          | Description                                                                                                                                         |
|----------------------------------|---------|-----------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `EXTERNAL_QUERY_DEFAULT_TIMEOUT` | `1800`  | `atp3tdm.externalQueryDefaultTimeout`: `1800` | Timeout of such a query in seconds, when the table settings do not set one.                                                                         |
| `EXTERNAL_QUERY_MAX_TIMEOUT`     | `3600`  | `atp3tdm.externalQueryMaxTimeout`: `1800`     | Largest timeout in seconds that refresh and cleanup settings accept. A larger value is rejected with `The timeout is not within the allowed range.` |

## Locks

Operations that change a test data table, such as insert, occupy, import, and truncate, take a lock on the table in
the database. A second operation on the same table waits until the lock is released or expires.

| Variable                    | Default | Helm                                   | Description                                                                                                                                     |
|-----------------------------|---------|----------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `LOCK_DEFAULT_DURATION_SEC` | `60`    | `atp3tdm.lockDefaultDurationSec`: `60` | Longest time an operation holds a table lock, in seconds. After that, the lock expires even if the operation still runs.                        |
| `LOCK_RETRY_TIMEOUT_SEC`    | `10800` | `atp3tdm.lockRetryTimeoutSec`: `10800` | How long an operation waits for a locked table, in seconds (3 hours). Then it fails with an error that starts with `Cannot obtain lock by key`. |
| `LOCK_RETRY_PACE_SEC`       | `3`     | `atp3tdm.lockRetryPaceSec`: `3`        | Interval between attempts to take a lock, in seconds.                                                                                           |
| `LOCK_BULK_ACTION_SEC`      | `3600`  | -                                      | Longest time a bulk action holds its lock, in seconds. A bulk action locks the action type for the whole project.                               |

## Environments cache

The service caches the environments and systems that test data tables refer to.

| Variable                         | Default   | Helm                                             | Description                                                  |
|----------------------------------|-----------|--------------------------------------------------|--------------------------------------------------------------|
| `ENVIRONMENTS_SPRING_CACHE_TYPE` | `GENERIC` | `atp3tdm.environmentsSpringCacheType`: `GENERIC` | Spring cache type. `GENERIC` turns the cache on, `NONE` off. |
| `ENVIRONMENTS_CACHE_DURATIONS`   | `15`      | `atp3tdm.environmentsCacheDurations`: `15`       | Minutes a cached entry lives after it is written.            |

## Mail and statistics reports

The service sends statistics reports and bulk action results by email through the ATP mail sender service, and renders
report charts through the ATP charts service.

| Variable                     | Default                  | Helm                                                   | Description                                                                   |
|------------------------------|--------------------------|--------------------------------------------------------|-------------------------------------------------------------------------------|
| `MAIL_SENDER_ENABLE`         | `true`                   | `MAIL_SENDER_ENABLE`: `false`                          | Turns email sending on or off.                                                |
| `FROM_EMAIL_ADDRESS`         | `example@example.com`    | `atp3tdm.fromEmailAddress`: empty                      | Sender address of the emails.                                                 |
| `FEIGN_ATP_MAILSENDER_URL`   | empty                    | `FEIGN_ATP_MAILSENDER_URL`: empty                      | Address of the mail sender service. Empty means the service is found by name. |
| `FEIGN_ATP_MAILSENDER_NAME`  | `ATP-MAIL-SENDER`        | `FEIGN_ATP_MAILSENDER_NAME`: `ATP-MAIL-SENDER`         | Name of the mail sender service.                                              |
| `FEIGN_ATP_MAILSENDER_ROUTE` | `api/atp-mail-sender/v1` | `FEIGN_ATP_MAILSENDER_ROUTE`: `api/atp-mail-sender/v1` | Path prefix of the mail sender API.                                           |
| `FEIGN_ATP_HIGHCHARTS_URL`   | empty                    | `FEIGN_ATP_HIGHCHARTS_URL`: empty                      | Address of the charts service. Empty means the service is found by name.      |
| `FEIGN_ATP_HIGHCHARTS_NAME`  | `ATP-CHARTS`             | `FEIGN_ATP_HIGHCHARTS_NAME`: `ATP-CHARTS`              | Name of the charts service.                                                   |
| `FEIGN_ATP_HIGHCHARTS_ROUTE` | `api/atp-charts/v1`      | `FEIGN_ATP_HIGHCHARTS_ROUTE`: `api/atp-charts/v1`      | Path prefix of the charts API.                                                |

## ATP integration

| Variable                                                               | Default                                   | Helm                                           | Description                                                                                                |
|------------------------------------------------------------------------|-------------------------------------------|------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `ATP_SERVICE_PUBLIC`                                                   | `true`                                    | `atp3tdm.atpServicePublic`: `false`            | Registers the service in the ATP gateway as public.                                                        |
| `ATP_SERVICE_PATH`                                                     | `/api/atp-tdm/v1/**`                      | `atp3tdm.atpServicePath`: `/api/atp-tdm/v1/**` | Path under which the ATP gateway routes requests to the service.                                           |
| `ATP_INTERNAL_GATEWAY_ENABLED`                                         | `false`                                   | `atp3tdm.atpInternalGatewayEnabled`: `false`   | Sends calls to other ATP services through the internal gateway. See [Internal gateway](#internal-gateway). |
| `FEIGN_ATP_INTERNAL_GATEWAY_NAME`                                      | unset                                     | -                                              | Name of the internal gateway. Read by `run.sh` when `ATP_INTERNAL_GATEWAY_ENABLED` is `true`.              |
| `EUREKA_CLIENT_ENABLED`                                                | `false`                                   | `atp3tdm.eurekaClientEnabled`: `false`         | Registers the service in Eureka and finds other services through it.                                       |
| `SERVICE_REGISTRY_URL`                                                 | `http://atp-registry-service:8761/eureka` | `SERVICE_REGISTRY_URL`: empty                  | Eureka server URL.                                                                                         |
| `FEIGN_CONNECT_TIMEOUT`                                                | `300000`                                  | `FEIGN_CONNECT_TIMEOUT`: `300000`              | Connect timeout of calls to other ATP services, in milliseconds (5 minutes).                               |
| `FEIGN_READ_TIMEOUT`                                                   | `300000`                                  | `FEIGN_READ_TIMEOUT`: `300000`                 | Read timeout of calls to other ATP services, in milliseconds (5 minutes).                                  |
| `FEIGN_ATP_USERS_URL`, `FEIGN_ATP_USERS_NAME`, `FEIGN_ATP_USERS_ROUTE` | empty, `ATP-USERS-BACKEND`, empty         | -                                              | Client settings of the ATP users service, which the ATP auth library calls for project access checks.      |

### Internal gateway

`run.sh` changes the client settings of the mail sender and charts services depending on
`ATP_INTERNAL_GATEWAY_ENABLED`:

- `true`: `FEIGN_ATP_MAILSENDER_NAME` and `FEIGN_ATP_HIGHCHARTS_NAME` take the value of
  `FEIGN_ATP_INTERNAL_GATEWAY_NAME`.
- Any other value: `FEIGN_ATP_MAILSENDER_ROUTE` and `FEIGN_ATP_HIGHCHARTS_URL` are cleared.

## Security

| Variable                  | Default                      | Helm                                                                                            | Description                                                                                                                                              |
|---------------------------|------------------------------|-------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `KEYCLOAK_ENABLED`        | `false`                      | `KEYCLOAK_ENABLED`: `false`                                                                     | Turns Keycloak authentication on or off.                                                                                                                 |
| `KEYCLOAK_AUTH_URL`       | `localhost`                  | `KEYCLOAK_AUTH_URL`: empty                                                                      | Keycloak server URL.                                                                                                                                     |
| `KEYCLOAK_REALM`          | `testRealm`                  | `KEYCLOAK_REALM`: `atp3`                                                                        | Keycloak realm. Also required by `run.sh`; see [UI and Keycloak](#ui-and-keycloak).                                                                      |
| `KEYCLOAK_CLIENT_NAME`    | `testRealm`                  | `atp3tdm.keycloakClientName`: `atp-tdm`, stored in the secret                                   | Keycloak client ID.                                                                                                                                      |
| `KEYCLOAK_SECRET`         | a built-in development value | `atp3tdm.keycloakSecret`: empty, which means a built-in development value; stored in the secret | Keycloak client secret.                                                                                                                                  |
| `PROJECT_INFO_ENDPOINT`   | unset                        | `atp3tdm.projectInfoEndpoint`: `/api/v1/users/projects`                                         | Endpoint of the ATP users service that returns project information for access checks. The service starts without it with the `disable-security` profile. |
| `CONTENT_SECURITY_POLICY` | `default-src 'self' *`       | `CONTENT_SECURITY_POLICY`: `*`                                                                  | Value of the `Content-Security-Policy` response header.                                                                                                  |
| `ATP_CRYPTO_KEY`          | a built-in development key   | `ATP_CRYPTO_KEY`: empty                                                                         | Encrypted key the service uses to decrypt encrypted values, such as encrypted connection parameters.                                                     |
| `ATP_CRYPTO_PRIVATE_KEY`  | a built-in development key   | `ATP_CRYPTO_PRIVATE_KEY`: empty                                                                 | Private key that decrypts `ATP_CRYPTO_KEY`.                                                                                                              |

Set your own `ATP_CRYPTO_KEY`, `ATP_CRYPTO_PRIVATE_KEY`, and `KEYCLOAK_SECRET` in production: the built-in values are
public in this repository.

The chart stores `ATP_CRYPTO_KEY` and `ATP_CRYPTO_PRIVATE_KEY` in the `<SERVICE_NAME>-secrets` secret, and passes
them to the container only when the Helm value `ENCRYPT` is `secrets`. With the chart default `dev`, the service uses
the built-in keys.

### UI and keycloak

At container start, `run.sh` writes `web/assets/routes.json`, the file the UI reads to find Keycloak and the backend.
The defaults in this table are those of `run.sh`.

| Variable                | Default | Helm                                                                | Description                                                                  |
|-------------------------|---------|---------------------------------------------------------------------|------------------------------------------------------------------------------|
| `KEYCLOAK_REALM`        | unset   | `KEYCLOAK_REALM`: `atp3`                                            | Keycloak realm for the UI login. `run.sh` exits when it is empty.            |
| `KEYCLOAK_AUTH_URL`     | unset   | `KEYCLOAK_AUTH_URL`: empty                                          | Keycloak URL for the UI login. `run.sh` drops the last path segment from it. |
| `IDENTITY_PROVIDER_URL` | unset   | `ATP_TDM_URL` or, when that is empty, `atp3tdm.identityProviderUrl` | Backend URL for the UI. `run.sh` exits when it is empty.                     |

## Monitoring and logging

| Variable                          | Default                                                | Helm                                                                                                            | Description                                                                                                                       |
|-----------------------------------|--------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `MONITOR_PORT`                    | `8090`                                                 | `MONITOR_PORT`: `8090`                                                                                          | Port of the Spring Boot Actuator endpoints.                                                                                       |
| `MONITOR_WEB_EXPOSE`              | `prometheus,info,scheduledtasks`                       | -                                                                                                               | Actuator endpoints to expose.                                                                                                     |
| `MONITOR_WEB_BASE`                | `/`                                                    | -                                                                                                               | Base path of the Actuator endpoints.                                                                                              |
| `MONITOR_WEB_MAP_PROM`            | `metrics`                                              | -                                                                                                               | Path of the Prometheus endpoint. With the defaults, metrics are at `http://<host>:8090/metrics`.                                  |
| `LOG_GRAYLOG_ON`                  | unset, which means off                                 | `GRAYLOG_ON`: `true`                                                                                            | Sends logs to Graylog when set to `true`.                                                                                         |
| `LOG_GRAYLOG_HOST`                | unset                                                  | `GRAYLOG_HOST`: `udp:graylog-service.example.com`                                                               | Graylog host.                                                                                                                     |
| `LOG_GRAYLOG_PORT`                | unset                                                  | `GRAYLOG_PORT`: `12201`                                                                                         | Graylog port.                                                                                                                     |
| `ATP_HTTP_LOGGING`                | `false`                                                | `atp3tdm.atpHttpLogging`: `true`                                                                                | Logs incoming and outgoing HTTP requests at `DEBUG` level: to Graylog when `LOG_GRAYLOG_ON` is `true`, to the log file otherwise. |
| `ATP_HTTP_LOGGING_HEADERS`        | `true`                                                 | `atp3tdm.atpHttpLoggingHeaders`: `true`                                                                         | Includes HTTP headers in the HTTP log.                                                                                            |
| `ATP_HTTP_LOGGING_HEADERS_IGNORE` | empty                                                  | `atp3tdm.atpHttpLoggingHeadersIgnore`: `Authorization`                                                          | Headers to leave out of the HTTP log.                                                                                             |
| `ATP_HTTP_LOGGING_URI_IGNORE`     | `/rest/deployment/readiness /rest/deployment/liveness` | `atp3tdm.atpHttpLoggingUriIgnore`: `/rest/deployment/readiness /rest/deployment/liveness /api/tdm/download/csv` | Request paths to leave out of the HTTP log, separated by spaces.                                                                  |
| `ZIPKIN_ENABLE`                   | `false`                                                | `ZIPKIN_ENABLE`: `false`                                                                                        | Turns Zipkin tracing on or off.                                                                                                   |
| `ZIPKIN_PROBABILITY`              | `1.0`                                                  | `ZIPKIN_PROBABILITY`: `1.0`                                                                                     | Share of requests to trace, from `0.0` to `1.0`.                                                                                  |
| `ZIPKIN_URL`                      | `http://127.0.0.1:9411`                                | `ZIPKIN_URL`: `http://zipkin.zipkin.svc:9411`                                                                   | Zipkin server URL.                                                                                                                |

## JVM

`run.sh` starts the JVM with these variables:

| Variable       | Default | Helm                                                                                          | Description                                                         |
|----------------|---------|-----------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| `JAVA_OPTIONS` | empty   | Built from the JDBC URL, `MEM_ARGS`, `atp3tdm.heapDumpEnabled`, and the `atp3tdm.jmx*` values | Extra JVM options.                                                  |
| `MAX_RAM`      | `1024m` | `MAX_RAM` from the resource profile: `1024m` (dev) or `1700m` (prod)                          | Value of `-XX:MaxRAM`, the memory size the JVM sizes its heap from. |

`run.sh` adds `-Djdbc.MinIdle=20 -Djdbc.MaxPoolSize=50` after `JAVA_OPTIONS`, so the database connection pool keeps at
least 20 idle connections and opens at most 50.

## Variables without effect

The chart sets these variables, but neither the service nor `run.sh` reads them: `MAIL_SENDER_URL`,
`MAIL_SENDER_ENDPOINT`, `MAIL_SENDER_PORT`, `LIQUIBASE_ENABLED`, `MICROSERVICE_NAME`, `PROFILER_ENABLED`,
`REMOTE_DUMP_HOST`, `REMOTE_DUMP_PORT`, and `CLOUD_NAMESPACE`. To configure mail, use the
[Mail and statistics reports](#mail-and-statistics-reports) variables; to turn Liquibase off, use
`LIQUIBASE_LAUNCH_ENABLED`.

`application.properties` maps `FEIGN_ATP_CATALOGUE_URL`, `FEIGN_ATP_CATALOGUE_NAME`, `FEIGN_ATP_CATALOGUE_ROUTE`,
`FEIGN_ATP_ENVIRONMENTS_URL`, `FEIGN_ATP_ENVIRONMENTS_NAME`, and `FEIGN_ATP_ENVIRONMENTS_ROUTE` to properties, but the
service has no client for the ATP catalogue and environments services, so these variables change nothing.
