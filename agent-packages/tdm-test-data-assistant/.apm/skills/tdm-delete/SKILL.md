---
name: tdm-delete
description: Use only when the user types /tdm-delete. Deletes the rows of an explicit TDM3 table that match explicit Column=value filters, after showing them and getting a confirmation.
---

# /tdm-delete

A command front end for the delete-by-ID part of
[tdm-occupy-test-data-rows-by-id](../tdm-occupy-test-data-rows-by-id/SKILL.md). It reads the user's arguments
literally, finds the matching rows, and deletes them after a confirmation. A deletion is permanent. For an ordinary
request in plain language, follow that skill directly instead.

## Arguments

```text
/tdm-delete table=<title> <Column>=<value> [<Column>=<value> ...] [all]
```

| Argument            | Meaning                                                                                       |
|---------------------|-----------------------------------------------------------------------------------------------|
| `table=<title>`     | The table to delete from. Required.                                                           |
| `<Column>=<value>`  | A filter: the column equals the value. At least one is required.                              |
| `all`               | Allow the command to delete more than one row.                                                |

Put a name or a value with spaces in double quotes: `agent_code="A013  "`. To delete every row of a table, the user
asks in plain language; this command never does.

## Behavior

- Never infer a missing `table=`. Require at least one filter. With none, send nothing and say that this command needs
  a filter.
- When an argument can't be parsed unambiguously, send nothing. Reply with the corrected command and let the user send
  it or change it.
- Work in the current project, environment, and system. Resolve them with
  [tdm-select-context](../tdm-select-context/SKILL.md) when they aren't set, and don't accept a per-command override.
- Find the rows first. Read the table twice with the paged read of
  [tdm-browse-test-data-table](../tdm-browse-test-data-table/SKILL.md) and the filters as `Equals`, once with
  `occupied` false and once with true. Take the `ROW_ID` values from the rows. When `records` is more than 50, page
  on (`offset` 50, 100, ...) until all of them are collected. Use the title-to-name mapping as
  [tdm-find](../tdm-find/SKILL.md) describes.
- No matching row: send nothing and say so. More than one matching row and no `all`: send nothing, say how many rows
  match, and ask the user to narrow the filters or add `all`.
- Show the rows that would be deleted (all data columns, trailing spaces stripped) and their number, and mark each
  reserved row with its `OCCUPIED_BY`. Ask the user to confirm. Send the deletion only after a clear yes.
- After a yes, call `PUT /api/tdm/delete/rows` with the `ROW_ID` values, as tdm-occupy-test-data-rows-by-id
  describes. It returns HTTP 200 with an empty body, so a 200 means the request was accepted.
- Reply with the table and the number of rows deleted.
- An unreachable server and a filter column the table doesn't have are handled as the "Common pitfalls" of
  [tdm-reserve-test-data](../tdm-reserve-test-data/SKILL.md) describe: report them, delete nothing, and don't retry
  with a guessed address or name.
