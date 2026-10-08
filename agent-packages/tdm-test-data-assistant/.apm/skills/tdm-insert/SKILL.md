---
name: tdm-insert
description: Use only when the user types /tdm-insert. Inserts one row with explicit Column=value pairs into an existing TDM3 table, or creates the table with that row when new is given.
---

# /tdm-insert

A command front end for [tdm-insert-test-data](../tdm-insert-test-data/SKILL.md). It reads the user's arguments
literally and sends the insert that skill describes; the request shape and the response stay there. For an ordinary
request in plain language ("add this row to Agents"), follow tdm-insert-test-data directly instead.

## Arguments

```text
/tdm-insert table=<title> <Column>=<value> [<Column>=<value> ...] [new]
```

| Argument            | Meaning                                                                                       |
|---------------------|-----------------------------------------------------------------------------------------------|
| `table=<title>`     | The table to insert into. Required.                                                           |
| `<Column>=<value>`  | A column and the value for the new row. At least one is required.                             |
| `new`               | Create the table when it doesn't exist. Without it, the table must already exist.             |

The command inserts one row per call. For several rows, send the command several times, or use `/tdm-load`. Put a name
or a value with spaces in double quotes: `agent_name="Ravi Kumar"`.

## Behavior

- Never infer a missing `table=`, and ask for at least one `<Column>=<value>`.
- When an argument can't be parsed unambiguously, send nothing. Reply with the corrected command and let the user send
  it or change it.
- Work in the current project, environment, and system. Resolve them with
  [tdm-select-context](../tdm-select-context/SKILL.md) when they aren't set, and send the resolved `envName` and
  `systemName`, as tdm-insert-test-data requires.
- `insert-records` creates a table when the title doesn't exist, so a mistyped title would create a new table. Check
  the catalog of the current system first, as [tdm-list-tables](../tdm-list-tables/SKILL.md) describes, and reuse the
  title-to-name mapping from the session as [tdm-find](../tdm-find/SKILL.md) describes.
- If the table doesn't exist and `new` is not given, send nothing: say that the table wasn't found, list the tables of
  the current system, and mention `new`. If it exists and `new` is given, send nothing and say so.
- If the table exists, read one row of it with the paged read of
  [tdm-browse-test-data-table](../tdm-browse-test-data-table/SKILL.md) (`limit` 1) to get the column names of the
  header. Every column in the command must match a name there exactly. Otherwise send nothing: name the unknown
  columns and show the real ones.
- A value with an invisible character is handled as the package instruction describes: confirm it with the user before
  sending, because the character would be stored.
- Reply with the table, whether it was created or already existed, and the values inserted. The response of
  `insert-records` is a single object, not an array; read its `type` and `content`.
- An unreachable server is handled as the "Common pitfalls" of
  [tdm-reserve-test-data](../tdm-reserve-test-data/SKILL.md) describe: report it, insert nothing, and don't retry with a
  guessed address.
