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

Run [tdm-select-context](../tdm-select-context/SKILL.md) first if `PROJECT_NAME`, `ENV_NAME_DEFAULT`, or
`SYSTEM_NAME_DEFAULT` aren't resolved yet. Beyond that:

- `title-table` — the table to delete rows from.

**`projectName` here is the project ID (a UUID), not the project name** — the one exception in this whole
controller. Every other skill in this package sends `PROJECT_NAME` from the config file; these two calls need
`PROJECT_ID` instead. Verified: sending the project name gives back a bare-text `400 Bad Request`, body
`Invalid UUID string: <name>` — not the usual `{"type": "ERROR", ...}` shape, so code written to check `type` first
will throw trying to parse it as JSON.

## Truncate (delete every row)

```bash
curl -X POST '<TDM3_BASE_URL>/api/tdm/rest/truncate-table' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "<PROJECT_ID>",
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
    "title-table": "<table title>"
  }'
```

`type: "ERROR"` with `content: "Cleanup hasn't been configured for table \"<title>\"."` (verified) means the table
has no cleanup configuration yet — nothing was deleted. On success, `content` gives the number of rows removed.

## Common pitfalls

- Sending `PROJECT_NAME` (the name) instead of `PROJECT_ID`: see above — this is the one place in the controller
  where the usual addressing doesn't apply, and the failure mode (bare-text 400) doesn't look like this
  controller's other errors.
- Calling `truncate-table` when the user meant "remove the rows I'm done with": that's `run-cleanup-table` (rule-
  based) or [tdm-reserve-test-data](../tdm-reserve-test-data/SKILL.md)'s release (frees a row without deleting it).
  `truncate-table` removes every row, reserved or not.
- Proceeding without confirmation: both calls are irreversible, and `run-cleanup-table` deletes by whatever rule was
  configured earlier — the caller doesn't see the rule from this request alone, so confirm the user understands
  that before running it.
