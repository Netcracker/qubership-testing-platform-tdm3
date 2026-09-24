# Qubership Testing Platform TDM3 Service

## About

The main goal of TDM (Test Data Management) Service is to simplify test data usage and management on the project for
manual and automated Standalone/End-to-End testing.

The concept of test data management assumes usage of TDM tool as single centralized data storage, for creation,
updating and tracking of test data usage on different environments.

This approach gives a user the only entry point for test data usage on different environments. New scripts for test
data collecting or updating can be performed in few clicks on different servers.

TDM3 differs from the TDM service in these ways:

- It keeps its data in an H2 file database instead of PostgreSQL.
- Authentication is reduced, so the service runs with the `disable-security` Spring profile. See
  [`ACTIVE_PROFILES_SPRING`](docs/configuration.md#general).
- It does not use MongoDB.

This repository holds the backend: a Spring Boot 3 service on Java 21 that runs on Undertow. The UI is developed in a
separate repository. The backend serves the UI files from the `web/` directory of its working directory; the build
packs `qubership-atp-tdm-backend/web/` into the distribution when that directory exists. Without UI files, the REST API
works, and a request to `/` returns HTTP 500.

## Repository layout

| Module                                                                                           | Contents                                                                                                                                                         |
|--------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`qubership-atp-tdm-backend`](qubership-atp-tdm-backend)                                         | The service: REST controllers, services, repositories, Liquibase changelog, and configuration.                                                                   |
| [`qubership-atp-tdm-env-configurator`](qubership-atp-tdm-env-configurator)                       | Access to projects, environments, and systems that test data tables refer to.                                                                                    |
| [`qubership-atp-tdm-rest-openapi-specifications`](qubership-atp-tdm-rest-openapi-specifications) | OpenAPI specifications of the earlier TDM API. The backend build generates interfaces and models from them, and the controllers do not implement the interfaces. |
| [`qubership-atp-tdm-distribution`](qubership-atp-tdm-distribution)                               | The distribution ZIP that the Docker image is built from.                                                                                                        |
| [`qubership-atp-tdm-benchmarks`](qubership-atp-tdm-benchmarks)                                   | JMH benchmarks.                                                                                                                                                  |
| [`qubership-atp-tdm-contract-test-pacts`](qubership-atp-tdm-contract-test-pacts)                 | Pact consumer tests for the clients of other ATP services.                                                                                                       |
| [`parent`](parent)                                                                               | Parent POMs with dependency versions and the H2 settings for local runs.                                                                                         |
| [`deployments/charts/atp3-tdm-be`](deployments/charts/atp3-tdm-be)                               | Helm chart.                                                                                                                                                      |

## Build

You need JDK 21 and Maven 3.9. Build with the `github` profile, which CI uses too:

```bash
mvn -P github clean package
```

The build runs the unit tests and produces the distribution ZIP
`qubership-atp-tdm-distribution/target/qubership-atp-tdm-distribution-<version>-dist.zip`. Add `-DskipTests` to skip
the tests.

The [`Dockerfile`](Dockerfile) builds the image from that ZIP. It expects the build output under
`build-context/qubership-atp-tdm-distribution/target/`, which is where the CI workflow puts it.

## Run locally

1. Build the service as described in [Build](#build).
2. Unpack the distribution ZIP into an empty directory, and open a terminal in the directory that contains `config/`
   and `lib/`.
3. Set the database variables. The H2 database file is created on the first start, relative to the working directory:

   ```bash
   export JDBC_URL="jdbc:h2:file:./database/atptdm;MODE=PostgreSQL"
   export TDM_DB_USER=tdmadmin
   export TDM_DB_PASSWORD=tdmadmin
   ```

4. Start the service:

   ```bash
   java --add-opens java.base/java.lang=ALL-UNNAMED \
     -Dspring.config.location=file:./config/application.properties \
     -cp "config/:lib/*" org.qubership.atp.tdm.Main
   ```

   On Windows, separate the classpath entries with `;`: `-cp "config/;lib/*"`.

The service listens on these addresses:

| URL                                               | Purpose                                                             |
|---------------------------------------------------|---------------------------------------------------------------------|
| `http://localhost:8080/api/tdm/versions/backend`  | Service name and version. Use it to check that the service is up.   |
| `http://localhost:8080/api/tdm/...`               | REST API.                                                           |
| `http://localhost:8080/swagger-ui.html`           | Swagger UI.                                                         |
| `http://localhost:8080/v3/api-docs`               | OpenAPI description of the REST API, as JSON.                       |
| `http://localhost:8080/h2-console`                | H2 web console. Connect with the same JDBC URL, user, and password. |
| `http://localhost:8080/rest/deployment/readiness` | Readiness probe.                                                    |
| `http://localhost:8080/rest/deployment/liveness`  | Liveness probe.                                                     |
| `http://localhost:8090/metrics`                   | Prometheus metrics.                                                 |

To run the service from IntelliJ IDEA, use the `backend` run configuration in
[`.run/backend.run.xml`](.run/backend.run.xml). It starts `org.qubership.atp.tdm.Main` with the repository root as the
working directory, so the H2 database file is created in `database/` at the repository root.

## Run tests

`mvn -P github clean package` runs the unit tests of all modules. The Pact consumer tests in
`qubership-atp-tdm-contract-test-pacts` write the pact files to `target/classes/pacts` of that module. Two kinds of
tests do not run in the build:

- Classes named `*RestAssuredTest` in `qubership-atp-tdm-backend`, which Surefire excludes.
- The benchmarks in `qubership-atp-tdm-benchmarks`, which run only with `-Dskip.tests=false`.

### Updating the OpenAPI description

`OpenApiDescriptionTest` fails when the REST API differs from the committed description in
[`docs/openapi.json`](docs/openapi.json). The failure lists the added, removed, and changed paths and schemas. After a
change to a controller or to a request or response model, regenerate the file from the repository root, review its
diff, and commit it with the change:

```bash
mvn -P github -pl qubership-atp-tdm-backend -am test -Dtest=OpenApiDescriptionTest -Dsurefire.failIfNoSpecifiedTests=false -Dopenapi.update=true
```

## REST API

[`docs/openapi.json`](docs/openapi.json) is the OpenAPI description of the REST API, generated from the controllers. A
running service also serves it at `/v3/api-docs` and shows it in Swagger UI at `/swagger-ui.html`, unless
[`SWAGGER_ENABLED`](docs/configuration.md#general) is `false`. The Helm chart sets it to `false`.

The OpenAPI files in `qubership-atp-tdm-rest-openapi-specifications` describe the API of the earlier TDM service, and
the controllers do not follow them.

For the endpoint groups, the gateway prefix, the response and error formats, the WebSocket bulk operations, and the
Dynamic Environment API, see [docs/api.md](docs/api.md).

## Deploy with Helm

The Helm chart is in [`deployments/charts/atp3-tdm-be`](deployments/charts/atp3-tdm-be). It deploys one replica with
the `Recreate` strategy, and keeps the H2 database on a `ReadWriteOnce` persistent volume claim named
`<SERVICE_NAME>-pvc`, mounted at `/atp-tdm/database`.

CI publishes the image `ghcr.io/netcracker/qubership-testing-platform-tdm3` with the tags `<branch>-<timestamp>` and
`<branch>_latest`, for example `main_latest`.

`values.yaml` does not set the resources and the replica count. Take them from a resource profile in
[`resource-profiles`](deployments/charts/atp3-tdm-be/resource-profiles), `dev.yaml` or `prod.yaml`:

```bash
helm install atp3-tdm-be deployments/charts/atp3-tdm-be \
  --namespace <namespace> \
  -f deployments/charts/atp3-tdm-be/resource-profiles/dev.yaml \
  --set DOCKER_TAG=ghcr.io/netcracker/qubership-testing-platform-tdm3:main_latest \
  --set CLOUD_PUBLIC_HOST=<cluster domain> \
  --set ATP_TDM_URL=https://<service host> \
  --set atp3tdm.storageClassName=<storage class>
```

| Value                      | Description                                                                                                                           |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `DOCKER_TAG`               | Full image reference, including the registry and the tag.                                                                             |
| `CLOUD_PUBLIC_HOST`        | Domain for the Ingress host `<SERVICE_NAME>-<namespace>.<CLOUD_PUBLIC_HOST>`. Set `ATP3_TDM_BE_URL` instead to choose the whole host. |
| `atp3tdm.storageClassName` | Storage class of the database volume. The chart default is `csi-sc-cinderplugin`.                                                     |
| `ATP_TDM_URL`              | Public URL of the service. `run.sh` requires it for the UI unless `atp3tdm.identityProviderUrl` is set.                               |

[`.github/deploy_templates/values-dev.yaml`](.github/deploy_templates/values-dev.yaml) is the values file of the CI
test deployment. The chart passes most other values to the service as environment variables; see the
[configuration reference](docs/configuration.md).

## Configuration

The service reads its settings from environment variables. The
[configuration reference](docs/configuration.md) lists every variable with its default, the Helm value that sets it,
and what it controls.
