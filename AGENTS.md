# Instructions for AI agents

This file applies to every AI coding agent that changes this repository. `CLAUDE.md` and
`.github/copilot-instructions.md` point here, so the rules live in one place.

## Keep Test Data Assistant in sync with the REST API

`agent-packages/tdm-test-data-assistant` is an APM package whose skills and scripts call the TDM3 REST API directly.
The package has no compile-time link to the backend, so a backend change that nobody carries into the package breaks
users of the package without any build failing.

### When this applies

A change triggers the rules below when it does any of these to the REST API:

- adds, removes, or renames an endpoint, or changes its HTTP method or path;
- adds, removes, renames, or changes the meaning, type, or required status of a request parameter, header, or body
  field;
- changes the type, shape, or meaning of a response, including the HTTP status codes and the error bodies that an
  endpoint returns.

The API is defined by the controllers, not by the OpenAPI files. Read the controller and the DTOs it uses. Do not edit
`qubership-atp-tdm-rest-openapi-specifications` for this purpose: those specifications are frozen.

### What to check

Find the endpoint path in the package and read every file that mentions it:

```bash
grep -rn "<endpoint path>" agent-packages/tdm-test-data-assistant
```

| Controller | Path prefix | Package skills that call it |
| --- | --- | --- |
| `AtpActionController` | `/api/tdm/rest` | `tdm-find-test-data`, `tdm-reserve-test-data`, `tdm-insert-test-data`, `tdm-update-test-data`, `tdm-cleanup-test-data-table`, `tdm-refresh-test-data-table`, `tdm-resolve-table-name`, and the `*-script` skills |
| `AtpEnvController` | `/api/tdm/rest/create-env` | `tdm-manage-dynamic-environment` |
| `TestDataController` | `/api/tdm` | `tdm-browse-test-data-table`, `tdm-list-tables`, `tdm-import-test-data`, `tdm-export-test-data-table`, `tdm-manage-test-data-table`, `tdm-occupy-test-data-rows-by-id`, `tdm-configure-column-links`, `tdm-table-utilities`, `tdm-run-legacy-migrations` |
| `TestDataControllerV2` | `/api/tdm/v2` | `tdm-import-test-data-v2` |
| `StatisticsController` | `/api/tdm/statistics` | `tdm-view-test-data-statistics`, `tdm-manage-statistics-schedule`, `tdm-manage-available-data-by-column-stats` |
| `DataCleanupController` | `/api/tdm/cleanup` | `tdm-manage-cleanup-config` |
| `EnvironmentsController` (env configurator) | `/api/tdm/projects`, `/api/tdm/environments` | `tdm-select-context`, `tdm-generate-test-data` |
| `DataRefreshController`, `VersionsController`, `ChartController` | | no skill calls them today |

The table is a starting point, not a guarantee. A skill can call an endpoint that is not in its row, and the grep is
the authority. A skill folder name that ends in `-script` has a `scripts` folder with Python code that builds the
request, so check the script as well as `SKILL.md`. The shared request code is in `tdm-common/scripts/tdm_common.py`.

For each match, check four places:

1. `SKILL.md` and the scripts of the skill: the path, the parameters, and the response fields that it reads.
2. `agent-packages/tdm-test-data-assistant/.apm/instructions`, and the skills that a changed skill refers to.
3. `agent-packages/tdm-test-data-assistant/README.md` and the pages in `agent-packages/tdm-test-data-assistant/docs`.
4. `agent-packages/tdm-test-data-assistant/tests/selftest.py`, which exercises the scripts against a running TDM3.

If nothing in the package mentions the endpoint, the package needs no change. Say so in the pull request, and say what
you searched for.

### Procedure

1. **Tell the user first.** Before editing, list the skills, scripts, instructions, and pages that the change affects,
   or say that none are affected and what you searched for. If you cannot tell whether the package is affected, say that
   and do not assume it is not.
2. **Ask before a breaking change.** A change is breaking for the package when a skill that works today stops working
   or starts returning a different result: a removed or renamed endpoint, a removed or renamed parameter, a new
   required parameter, a changed response type or shape, or a changed status code that a script checks. Describe what
   breaks and for whom, then wait for the user to confirm before you change the backend or the skills. Mark the commit
   and the pull request as breaking (`feat!:` or `fix!:`, or a `BREAKING CHANGE:` footer).
3. **Change the package with the backend,** in the same pull request: skills, scripts, instructions, documentation, and
   the selftest. Raise `version` in `agent-packages/tdm-test-data-assistant/apm.yml`: a patch for a correction that
   makes the package match the API, a minor for new or changed behavior, a major for a breaking change.
4. **Verify.** Run `python agent-packages/tdm-test-data-assistant/tests/selftest.py` against a local TDM3 when one is
   available, and pass the Python linters that CI runs on the package (pylint and ruff format, see
   `.github/super-linter.env`). If you could not run the selftest, say so in the pull request.
5. **Check the controller table.** When the change adds, removes, or renames a controller or a skill, or makes a skill
   start or stop calling an endpoint of another controller, redo the table in this file as the last edit before the
   commit, in the same commit. List the `@RequestMapping` prefixes of the controllers, run the `grep` above for each
   prefix, and correct every row that the result does not confirm. Add a row for a controller that the table lacks.
6. **Report it.** The commit messages name the package change, and the pull request description has a "Test Data
   Assistant" section: what changed in the package and why, or why nothing did. Where a change is breaking, say so in
   the first line of that section.

## Commits and pull requests

- Write commit messages and pull request titles in Conventional Commits format, because the repository checks them.
- Do not commit, push, or open a pull request unless the user asks.
- A change to a file under `agent-packages` uses the scope `agent-packages` unless a narrower one fits, for example
  `fix(env):` for a backend fix that also needs a package change.
