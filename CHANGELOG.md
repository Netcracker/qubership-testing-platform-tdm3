# Changelog

All notable changes to this project are documented in this file, in the
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format. This project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Reconstructed from the commit history on 2026-09-25; entries for releases before that date are dated by their
release commit, not by when they were written.

## [Not released yet]

### Added

- `docs/architecture.md`: the design behind the backend module, including how a test data table is stored, where
  projects and environments come from, and how scheduled and bulk operations run.
- `docs/troubleshooting.md`: a failing health check, common REST errors, and the service's custom Prometheus
  metrics.
- `docs/api.md`: the REST and WebSocket APIs endpoint groups, response and error formats, bulk operations, and the
  Dynamic Environment API.
- `docs/configuration.md`: every environment variable, its default, and the Helm value that sets it.
- `@Schema` descriptions on every REST request and response model, and Javadoc on the backend service interfaces,
  the REST controllers, and the env-configurator module's public API.

### Fixed

- Swagger UI and `/v3/api-docs` returned HTTP 500 instead of the API description, because the build pulled in a
  `springdoc-openapi-ui` release built for Spring Boot 2. Replaced it with the Spring Boot 3 release and committed
  the generated `docs/openapi.json`, checked on every build.
- The generated OpenAPI schema for `AddInfoToRowRequest` and `UpdateRowRequest` described `search-row-parameters-set`
  as a `TestDataTableFilter`, the wrong type; it accepts `ApiDataFilter`.
- The generated OpenAPI schema for `ResponseMessage.type` listed the enum values as lowercase `success`/`error`;
  the API actually returns uppercase `SUCCESS`/`ERROR`.

## [1.0.10] - 2026-09-07

### Added

- The Prometheus metrics port is exposed in the Helm chart (`MONITOR_PORT`, `8090` by default).

### Changed

- Dependency and Docker base image updates.

## [1.0.9] - 2026-06-18

### Added

- The Dynamic Environment API (`atp-env-controller`): create, update, and delete a project's environments and
  systems at runtime over REST, stored in the service's own database instead of a Git-based environments backend.
- Custom Prometheus metrics for cleanup, refresh, and statistics job runs, and for the ATP action operations
  (`atp_tdm_execute_cleanup_by_cron` and similar).

### Changed

- Environments and systems are now stored in two dedicated database tables instead of one.
- A connection with no name defaults to its connection type.

### Removed

- The in-memory cache layer for projects, environments, and systems. The endpoints and methods named after it
  (`GET /envs/reset/caches`, `getLazyEnvironmentsFromCache`, `getLazyEnvironmentsRefresh`) remain for compatibility
  but no longer cache anything.
- The envgene-file-based environment configuration that the Dynamic Environment API replaces.

### Fixed

- A system could be lost from its environment after a restart.
- Renaming an environment or a system did not update every place that referenced it, including its occupation
  statistics.

### Security

- Bumped several APK packages (`libcrypto3`, `libssl3`, `curl`, `libpng`, `sops`, `xz-libs`, `libexpat`, `musl`,
  `musl-utils`) to fix reported vulnerabilities in the Docker image.

## [1.0.8] - 2026-03-10

### Added

- `POST /api/tdm/v2/import/sql`: the SQL import operation with its parameters in the request body, instead of query
  parameters.

### Fixed

- An SQL import failed when the query needed ESAPI-encoded characters, because the encoding was also applied to the
  `SELECT` keyword itself.
- Saving a table's available column values (`table_column_values`) compared the table name case-sensitively while
  reading it compared case-insensitively, so a save and a later read of the same table could disagree.

### Security

- Bumped several APK packages (`sops`, `zlib`, `libpng`, `libexpat`) and the `com.mchange:c3p0` dependency to fix
  reported vulnerabilities.

## [1.0.7] - 2026-02-05

### Added

- `atp-action-controller`: an operation to resolve a table's database table name from its project, environment,
  system, and title.

### Fixed

- The statistics of a renamed table kept showing its old title.

### Security

- Bumped `libcrypto3`, `libssl3`, and the JDK to 21.0.10 in the Docker image, and `org.springframework:spring-core`
  to 6.2.13 and Undertow to 2.3.21.Final, to fix reported vulnerabilities.

## [1.0.6] - 2026-01-26

### Security

- Replaced the `org.lz4:lz4-java` dependency Kafka pulled in with `at.yawk.lz4:lz4-java` 1.10.1, to fix
  CVE-2025-66566 and CVE-2025-12183.

## [1.0.5] - 2025-12-16

### Security

- Switched the Docker image's APK repositories to Alpine 3.23 and bumped its packages, including `sops`, to fix a
  reported vulnerability. Forced `libpng` to 1.6.53-r0.

## [1.0.4] - 2025-12-04

### Added

- A decryption mechanism for envgene-encrypted files (SOPS with an age key; see
  `org.qubership.atp.tdm.env.configurator.utils.decryptor`).

### Security

- Bumped several APK packages in the Docker image, and moved its base image to the `v3.22` Alpine repositories, to
  fix reported vulnerabilities.

## [1.0.3] - 2025-12-01

### Fixed

- A path variable was read incorrectly on some endpoints.

### Changed

- The `jakarta.servlet:jakarta.servlet-api` dependency's scope was changed from `provided` to the default, so it is
  packaged with the application.

## [1.0.2] - 2025-11-25

### Fixed

- A Helm chart value in `values.yaml` was set incorrectly; corrected, with the `README.md` updated to match.
- Excluded `org.apache.tomcat.embed:tomcat-embed-core`, which conflicted with the embedded Undertow server.

## [1.0.1] - 2025-11-21

### Added

- Standardized the envgene deployment parameters used to configure this service.

## [1.0.0] - 2025-11-18

Initial release of TDM3, a simplified fork of the Test Data Management (TDM) service. Unlike TDM, this service:

- keeps its data in an H2 file database instead of PostgreSQL;
- runs with authentication reduced to the `disable-security` Spring profile instead of the full ATP security stack;
- does not use MongoDB;
- reads its environment configuration from envgene's file structure instead of integrating with the environments
  and catalogue services directly.

[Not released yet]: https://github.com/Netcracker/qubership-testing-platform-tdm3/compare/v1.0.10...HEAD
[1.0.10]: https://github.com/Netcracker/qubership-testing-platform-tdm3/compare/v1.0.9...v1.0.10
[1.0.9]: https://github.com/Netcracker/qubership-testing-platform-tdm3/compare/v1.0.8...v1.0.9
[1.0.8]: https://github.com/Netcracker/qubership-testing-platform-tdm3/compare/v1.0.7...v1.0.8
[1.0.7]: https://github.com/Netcracker/qubership-testing-platform-tdm3/compare/v1.0.6...v1.0.7
[1.0.6]: https://github.com/Netcracker/qubership-testing-platform-tdm3/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/Netcracker/qubership-testing-platform-tdm3/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/Netcracker/qubership-testing-platform-tdm3/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/Netcracker/qubership-testing-platform-tdm3/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/Netcracker/qubership-testing-platform-tdm3/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/Netcracker/qubership-testing-platform-tdm3/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/Netcracker/qubership-testing-platform-tdm3/releases/tag/v1.0.0
