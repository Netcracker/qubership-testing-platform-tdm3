---
name: tdm-cleanup-test-data-table
description: Use when the user wants to delete rows from a TDM3 table — every row at once, or by the table's configured cleanup rule. Calls the TDM3 REST API directly. Both operations are destructive and permanent.
---

# Delete rows of a TDM3 test data table

Call `POST /api/tdm/rest/truncate-table` to delete every row of a table, or `POST /api/tdm/rest/run-cleanup-table`
to run the cleanup rule already configured for it (age-based or SQL-based — configured through TestDataController,
not this package). Neither call can be undone. **Confirm with the user before calling either one**, and say plainly
that the rows will be gone, not just "cleaned up."

## Required inputs

Run [tdm-select-context](../tdm-select-context/SKILL.md) first if `PROJECT_ID`, `ENV_NAME_DEFAULT`, or
`SYSTEM_NAME_DEFAULT` aren't resolved yet. Beyond that:

- `title-table` — the table to delete rows from.

**`projectName` here is the project ID (a UUID), not the project name** — the one exception in this whole
controller. Every other skill in this package sends `PROJECT_NAME` from the config file; these two calls need
`PROJECT_ID` instead. Verified: sending the project name gives back a bare-text `400 Bad Request`, body
`Invalid UUID string: <name>` — not the usual `{"type": "ERROR", ...}` shape, so code written to check `type` first
will throw trying to parse it as JSON.

**`envName` and `systemName` are optional in the request schema but required in practice for both operations —
verified live.** The rest of `atp-action-controller` falls back to "the first table with this title in the
project" when they're omitted; these two don't. Omitting them fails table resolution outright, with a different,
unrelated-looking error for each operation (`truncate-table`: `"Tables with title: <title> was not found under
project with id: <id>"`; `run-cleanup-table`: `"Table \"<title>\" hasn't been found. Could you please check
provided data."`) — neither message mentions `envName`/`systemName`, so nothing points at the actual fix. Always
send both, resolved the normal way (`ENV_NAME_DEFAULT`/`SYSTEM_NAME_DEFAULT`), even though the request schema
doesn't require them.

## Truncate (delete every row)

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/rest/truncate-table' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<PROJECT_ID>",
    "envName": "<env>",
    "systemName": "<system>",
    "title-table": "<table title>"
  }'
```

```json
[{ "type": "SUCCESS", "content": "Table TDM_<...> has been truncated.", "contentObject": null, "link": "" }]
```

## Run the configured cleanup

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/rest/run-cleanup-table' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<PROJECT_ID>",
    "envName": "<env>",
    "systemName": "<system>",
    "title-table": "<table title>"
  }'
```

`type: "ERROR"` with `content: "Cleanup hasn't been configured for table \"<title>\"."` (verified, with `envName`/
`systemName` sent) means the table has no cleanup configuration yet — nothing was deleted. On success, `content`
gives the number of rows removed.

## Common pitfalls

- Sending `PROJECT_NAME` (the name) instead of `PROJECT_ID`: see above — this is the one place in the controller
  where the usual addressing doesn't apply, and the failure mode (bare-text 400) doesn't look like this
  controller's other errors.
- Omitting `envName`/`systemName` because the rest of the controller tolerates it: here it breaks table
  resolution, with an error that never mentions the fields actually missing.
- Calling `truncate-table` when the user meant "remove the rows I'm done with": that's `run-cleanup-table` (rule-
  based) or [tdm-reserve-test-data](../tdm-reserve-test-data/SKILL.md)'s release (frees a row without deleting it).
  `truncate-table` removes every row, reserved or not.
- Proceeding without confirmation: both calls are irreversible, and `run-cleanup-table` deletes by whatever rule was
  configured earlier — the caller doesn't see the rule from this request alone, so confirm the user understands
  that before running it.
