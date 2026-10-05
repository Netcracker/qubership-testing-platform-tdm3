---
name: tdm-list-tables
description: Use to discover which test data tables exist in TDM3 for a project (or environment), and to resolve a table title the user mentions loosely to its exact title and database table name. Calls the TDM3 REST API directly.
---

# List TDM3 tables

Four read-only `test-data-controller` endpoints answer "what tables are there," each scoped differently. Use this
skill to turn a vague table reference (a business-entity name, a partial title) into the exact `title-table` value
the [`atp-action-controller` skills](../tdm-find-test-data/SKILL.md) need, the same way
[tdm-select-context](../tdm-select-context/SKILL.md) resolves project/environment/system — list, then let the user
pick.

## Required inputs

Run [tdm-select-context](../tdm-select-context/SKILL.md) first if `PROJECT_ID`, `ENV_ID_DEFAULT`, or
`SYSTEM_ID_DEFAULT` isn't resolved yet — every endpoint here takes `projectId`, `environmentId`, or `systemId` (UUIDs
from the config file or from [tdm-select-context](../tdm-select-context/SKILL.md)'s own calls), not names.

## Which table the user means

Resolve the table before any operation that needs a `title-table` or a `tableName`, against the tables of the
**current system** (the catalog request below), in this order:

1. The user explicitly names the table: work with that table.
2. The user doesn't name it, but the request makes clear which table it is about (for example, "reserve a SIM card"
   is about the table of SIM cards), and the current system has a matching table, even if its title is spelled
   differently (`Sim-cards` for "SIM card"): work with that table, and name it in the reply so the user can correct
   the choice.
3. The request makes clear which table it is about, but the current system has nothing like it: say so, and ask the
   user to name another table or to switch the system. Don't fall back to the only table of the system, or to any
   other table, because it happens to exist. "Delete the customer with code X" must not touch an `Orders` table.

## List the tables of the current system, with detail

```bash
curl "<TDM3_BASE_URL>/api/tdm/tables/catalog?projectId=<PROJECT_ID>&systemId=<SYSTEM_ID_DEFAULT>"
```

Always send `systemId`: this package works within the current project, environment, and system. Omit it only when the
user explicitly asks for the tables of the whole project; the omission applies to that request only.

```json
[{
  "tableName": "TDM_547cdede996b44f4be273c444d4f7287",
  "tableTitle": "SkillVerification",
  "projectId": "c9858f9f-f87b-3eae-aa86-bfa92856d55c",
  "environmentId": null,
  "systemId": null,
  "importQuery": null,
  "queryTimeout": null,
  "cleanupConfigId": null,
  "refreshConfigId": null,
  "lastUsage": "2026-09-28T20:00:00.000+00:00"
}]
```

Verified live. `environmentId`/`systemId` are `null` for a table inserted without them (see
[tdm-insert-test-data](../tdm-insert-test-data/SKILL.md)'s pitfall). Match `tableTitle` against what the user said
(contains/starts-with, case-insensitive) and present the matches as a pick list when more than one fits.

## List just the database table names

```bash
curl "<TDM3_BASE_URL>/api/tdm/tables/list?projectId=<PROJECT_ID>"
```

```json
{ "projectId": "c9858f9f-f87b-3eae-aa86-bfa92856d55c", "tableIdsList": ["TDM_547cdede996b44f4be273c444d4f7287"] }
```

Verified live. No titles here — use `/tables/catalog` instead when the user needs to recognize a table by name.

## List tables of one environment

```bash
curl "<TDM3_BASE_URL>/api/tdm/environment/tables/list?projectId=<PROJECT_ID>&envId=<ENV_ID_DEFAULT>"
```

Returns a map of database table name to title, for that project and environment only.

## List which environments have a given table title

```bash
curl "<TDM3_BASE_URL>/api/tdm/table/environments?projectId=<PROJECT_ID>&tableTitle=<title>"
```

```json
{ "items": ["<environmentId>", ...] }
```

Verified live — including the edge case: a table with no environment association (see above) shows up as a `null`
entry in `items`, not an omission. Filter `null` out before presenting the list to a user.

## Common pitfalls

- Passing table titles or names instead of UUIDs: every parameter here is a `projectId`/`environmentId`/`systemId`
  UUID, unlike the `atp-action-controller` skills, which take names.
- Treating a `null` entry in `/table/environments`' `items` as a real environment ID.
