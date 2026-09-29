# tdm-test-data-assistant

Skills that call the TDM3 (`qubership-atp-tdm`) REST API directly, so an agent can find, reserve, and (later) create
test data without a separate MCP server. Each skill sends its own HTTP requests; there's no authentication layer,
since TDM3 is reached from inside the network perimeter.

## Skills

| Skill | Purpose |
|---|---|
| [tdm-select-context](.apm/skills/tdm-select-context/SKILL.md) | Resolve which project, environment, and system to work against. Run this first. |
| [tdm-find-test-data](.apm/skills/tdm-find-test-data/SKILL.md) | Search TDM3 for available rows matching column criteria. |
| [tdm-reserve-test-data](.apm/skills/tdm-reserve-test-data/SKILL.md) | Reserve (occupy) or release rows for a test run. |

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

`tdm-find-test-data` and `tdm-reserve-test-data` read `PROJECT_NAME`, `ENV_NAME_DEFAULT`, and `SYSTEM_NAME_DEFAULT`
for the `projectName`, `envName`, and `systemName` fields of their request bodies. Neither skill resolves them on
its own — run `tdm-select-context` first if they aren't set yet.
