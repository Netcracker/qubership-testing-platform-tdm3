---
name: tdm-select-context
description: Use before any TDM3 data operation (find, reserve, insert) to resolve and confirm the project, environment, and system to work against, or when the user wants to switch environment or system. Reads defaults from the config file, verifies them against TDM3, or lists the live options for the user to pick from.
---

# Select the TDM3 working context

Resolves project, environment, and system, in that order — each step's list is scoped to the previous step's
choice, so they can't be resolved independently. See [the package README](../../../README.md#configuration) for the
config file's location and the full key list; this skill is what writes `PROJECT_ID`, `PROJECT_NAME`,
`ENV_ID_DEFAULT`, `ENV_NAME_DEFAULT`, `SYSTEM_ID_DEFAULT`, and `SYSTEM_NAME_DEFAULT` into it.

Resolve `TDM3_BASE_URL` first (see the README) — every request below is `GET <TDM3_BASE_URL>/api/tdm/...`.

## 1. Project

Convention says one TDM3 install serves one project, but `GET /api/tdm/projects/lazy` only lists the projects named
in the service's own `PROJECTS_INFO` setting — it's a configured allowlist, not a query of every project that
actually has data. A project with real environments and systems can be missing from it, and a project it does list
can have nothing under it. Don't infer which project to use from the size of this list; resolve it the same way as
environment and system, below:

- The user names a project in their current request → verify it: call `GET /api/tdm/projects/lazy`:

  ```json
  [{ "id": "b0c1fd9e-19a7-4156-90e0-f04028a58720", "name": "MyProject" }]
  ```

  Match the name (or ID) against the list. No match → tell the user the name isn't in `PROJECTS_INFO`, and show the
  list instead of guessing.
- The user asks for the list of projects → call the same endpoint and show `name` for each; let them pick.
- Neither of the above, and `PROJECT_ID` / `PROJECT_NAME` are already in the config file → use them. Skip the
  network call; they were verified when they were saved.
- None of the above → ask the user to name a project or request the list. Never auto-pick a project just because
  the list currently has one entry.

Once resolved, write `PROJECT_ID` and `PROJECT_NAME` to the config file, replacing any previous value.

## 2. Environment

- The user names an environment in their current request → verify it: call
  `GET /api/tdm/projects/{PROJECT_ID}/environments/lazy`:

  ```json
  [{
    "id": "5f2c1e2a-1234-4a5b-8c9d-abcdef012345",
    "name": "STAGE",
    "projectId": "b0c1fd9e-19a7-4156-90e0-f04028a58720",
    "systems": ["BillingDB", "CustomerDB"]
  }]
  ```

  Match the name case-insensitively. No match → tell the user the name isn't a known environment of this project,
  and show the list instead of guessing.
- The user asks for the list of environments → call the same endpoint and show `name` (and `description`, if
  present) for each; let them pick.
- Neither of the above, and `ENV_ID_DEFAULT` / `ENV_NAME_DEFAULT` are already in the config file → use them as the
  current environment. Skip the network call; they were verified when they were saved.
- None of the above → ask the user to name an environment or request the list. Never default to "the first one in
  the list" silently.

Once an environment is resolved (verified by name, or picked from the list), write `ENV_ID_DEFAULT` and
`ENV_NAME_DEFAULT` to the config file, replacing any previous value — the user can switch environments at any time
by naming a different one, which re-runs this step.

## 3. System

Same algorithm as environment, scoped to the resolved `ENV_ID_DEFAULT`: `GET
/api/tdm/environments/{ENV_ID_DEFAULT}/systems/lazy`:

```json
[{
  "id": "9a8b7c6d-4321-4b5a-9c8d-fedcba987654",
  "name": "BillingDB",
  "environmentIds": ["5f2c1e2a-1234-4a5b-8c9d-abcdef012345"],
  "connections": ["JDBC"]
}]
```

Verify a user-named system against this list, list it on request, or fall back to `SYSTEM_ID_DEFAULT` /
`SYSTEM_NAME_DEFAULT` from the config file when the user named neither a system nor asked for the list. Write the
resolved `SYSTEM_ID_DEFAULT` and `SYSTEM_NAME_DEFAULT` back to the config file the same way as the environment.

## When a list comes back empty

`/environments/lazy` or `/systems/lazy` can return `[]` — a project can genuinely have no environments registered,
or an environment no systems. Don't treat this the same as "no match for the name I was given": say plainly that
this project (or environment) has nothing registered yet, and ask the user to confirm they picked the right one a
level up before concluding that environments or systems need to be created. A project selected by mistake is a more
common cause than a genuinely empty TDM3.

## Common pitfalls

- Re-running the project (or environment, or system) lookup on every call once it's already in the config file: all
  three were verified when they were saved, so re-verifying on every subsequent request is wasted round trips.
- Silently falling back to "the first project", "the first environment", or "the first system" when nothing is
  configured and the user didn't ask for the list — including when the list happens to have exactly one entry. Ask
  instead — picking the wrong project or environment can reserve or insert test data against the wrong server.
- Selecting a system before an environment is resolved, or an environment before a project is resolved: each list
  endpoint takes the previous level's ID as a path parameter, so the steps can't run out of order.
