# tdm-test-data-assistant

Skills that call the TDM3 (`qubership-atp-tdm`) REST API directly, so an agent can find, reserve, and manage test
data without a separate MCP server. Each skill sends its own HTTP requests; there's no authentication layer, since
TDM3 is reached from inside the network perimeter.

## Skills

### Context

| Skill | Purpose |
|---|---|
| [tdm-select-context](.apm/skills/tdm-select-context/SKILL.md) | Resolve which project, environment, and system to work against. Run this first. |

### `atp-action-controller` (`/api/tdm/rest/*`)

The API an automated action or agent calls to work with one table's rows. Every skill below sends its own
`projectName`/`envName`/`systemName`/`title-table` addressing (see Configuration) and reads a `ResponseMessage` —
`{type, content, contentObject, link}` — unless its own page says otherwise.

| Skill | Purpose |
|---|---|
| [tdm-find-test-data](.apm/skills/tdm-find-test-data/SKILL.md) | Search for available rows matching column criteria, without reserving them. |
| [tdm-reserve-test-data](.apm/skills/tdm-reserve-test-data/SKILL.md) | Reserve (occupy) or release rows for a test run. |
| [tdm-insert-test-data](.apm/skills/tdm-insert-test-data/SKILL.md) | Add new rows, creating the table if needed. |
| [tdm-update-test-data](.apm/skills/tdm-update-test-data/SKILL.md) | Change column values on existing rows, or append to one without losing its current value. |
| [tdm-refresh-test-data-table](.apm/skills/tdm-refresh-test-data-table/SKILL.md) | Re-run a table's saved import query on demand. |
| [tdm-cleanup-test-data-table](.apm/skills/tdm-cleanup-test-data-table/SKILL.md) | Delete every row of a table, or run its configured cleanup rule. Destructive. |
| [tdm-resolve-table-name](.apm/skills/tdm-resolve-table-name/SKILL.md) | Look up the underlying H2 database table name behind a table title. |

### `test-data-controller` (`/api/tdm/*`)

The UI-facing API: broader than `atp-action-controller` (pagination, sorting, reading occupied rows, file
import/export) but addressed mostly by **database table name** (`TDM_<hash>`, from `tdm-list-tables`) and
**project/environment/system UUIDs**, not by title and name — check each skill's own page for its exact addressing.

| Skill | Purpose |
|---|---|
| [tdm-list-tables](.apm/skills/tdm-list-tables/SKILL.md) | Discover which tables exist for a project or environment, and resolve a title to a database table name. |
| [tdm-browse-test-data-table](.apm/skills/tdm-browse-test-data-table/SKILL.md) | Page, sort, or inspect rows (including occupied ones); list a column's distinct values. |
| [tdm-occupy-test-data-rows-by-id](.apm/skills/tdm-occupy-test-data-rows-by-id/SKILL.md) | Occupy, release, or delete specific rows already identified by `ROW_ID`. |
| [tdm-import-test-data](.apm/skills/tdm-import-test-data/SKILL.md) | Load rows from an Excel file or a SQL query, or refresh a table's rows from a new SQL query result. |
| [tdm-manage-test-data-table](.apm/skills/tdm-manage-test-data-table/SKILL.md) | Drop a table, delete all its rows, or rename its title. Destructive. |
| [tdm-export-test-data-table](.apm/skills/tdm-export-test-data-table/SKILL.md) | Download a table as an Excel or CSV file. |
| [tdm-configure-column-links](.apm/skills/tdm-configure-column-links/SKILL.md) | Preview or save a column's values as clickable links in the TDM3 UI. |
| [tdm-table-utilities](.apm/skills/tdm-table-utilities/SKILL.md) | Check a table's unoccupied-row validation flag; resolve `${...}` macros in a query. |
| [tdm-run-legacy-migrations](.apm/skills/tdm-run-legacy-migrations/SKILL.md) | One-time legacy migrations. Two of five are confirmed broken on H2 — read before calling any of them. |

## Configuration

Every skill in this package needs to know which TDM3 server, project, environment, and system to target. They all
read the same config file, so resolve it once with `tdm-select-context` and the other skills reuse the result.

**Resolution order, for every setting below: an explicit value in the user's current request wins, then the matching
environment variable, then the config file, then ask the user.**

The config file is JSON, and its keys match their environment-variable overrides by name:

```json
{
  "TDM3_BASE_URL": "http://localhost:8080",
  "PROJECT_ID": "b0c1fd9e-19a7-4156-90e0-f04028a58720",
  "PROJECT_NAME": "MyProject",
  "ENV_ID_DEFAULT": "5f2c1e2a-1234-4a5b-8c9d-abcdef012345",
  "ENV_NAME_DEFAULT": "STAGE",
  "SYSTEM_ID_DEFAULT": "9a8b7c6d-4321-4b5a-9c8d-fedcba987654",
  "SYSTEM_NAME_DEFAULT": "BillingDB"
}
```

Look for it at `.tdm-assistant/config.json` in the current repository first, then at
`~/.tdm-assistant/config.json` for a personal default that applies across repositories. Write resolved values back
to the repository-level file (creating `.tdm-assistant/` if needed), after confirming with the user, so later
sessions don't ask again.

| Key | Meaning | Set by |
|---|---|---|
| `TDM3_BASE_URL` | Base URL of the running TDM3 service. | The user, once. Never guess a default such as `localhost:8080`. |
| `PROJECT_ID` | TDM3 project UUID. Convention says one TDM3 install serves one project, but `GET /api/tdm/projects/lazy` only lists what the service's own `PROJECTS_INFO` setting names — a project can have real data without being listed there, so this is resolved and can be switched the same way as environment and system. | `tdm-select-context`, on first use and whenever the user switches projects. |
| `PROJECT_NAME` | The project's name, cached alongside `PROJECT_ID` so calls that need a name (not a UUID) skip a lookup. | `tdm-select-context`, alongside `PROJECT_ID`. |
| `ENV_ID_DEFAULT`, `ENV_NAME_DEFAULT` | The environment currently in scope. Changeable at any time. | `tdm-select-context`, on first use and whenever the user switches environments. |
| `SYSTEM_ID_DEFAULT`, `SYSTEM_NAME_DEFAULT` | The system currently in scope, within `ENV_ID_DEFAULT`. Changeable at any time. | `tdm-select-context`, on first use and whenever the user switches systems. |

Every `atp-action-controller` skill reads `PROJECT_NAME`, `ENV_NAME_DEFAULT`, and `SYSTEM_NAME_DEFAULT` for the
`projectName`, `envName`, and `systemName` fields of its request body — except
[tdm-cleanup-test-data-table](.apm/skills/tdm-cleanup-test-data-table/SKILL.md), which needs `PROJECT_ID` instead of
`PROJECT_NAME` for its two operations; see that skill's own page. None of them resolve project, environment, or
system on their own — run `tdm-select-context` first if the config file doesn't have them yet.
