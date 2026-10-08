---
name: tdm-update
description: Use only when the user types /tdm-update. Replaces column values on the rows of an explicit TDM3 table that match explicit where conditions.
disable-model-invocation: true
---

# /tdm-update

A command front end for [tdm-update-test-data](../tdm-update-test-data/SKILL.md). It reads the user's arguments
literally and sends the update that skill describes; the request shape stays there. For an ordinary request in plain
language ("change the commission of agent A007"), follow tdm-update-test-data directly instead.

## Arguments

```text
/tdm-update table=<title> where <Column>=<value> [...] set <Column>=<value> [...] [all]
```

| Argument            | Meaning                                                                                       |
|---------------------|-----------------------------------------------------------------------------------------------|
| `table=<title>`     | The table to update. Required.                                                                |
| `where` ...         | The columns that select the rows: each column equals the value. At least one is required.     |
| `set` ...           | The columns to change and their new values. At least one is required.                         |
| `all`               | Allow the update to change more than one row.                                                 |

`where` and `set` are keywords that start each list; the pairs after them are `<Column>=<value>`. Put a name or a
value with spaces in double quotes: `where agent_code="A007  " set commission=0.20`. The command replaces values; to
append to a column, the user asks in plain language.

## Behavior

- Never infer a missing `table=`, and require at least one pair in `where` and in `set`. Ask for what is missing. A
  command without `where` would change every row, so it is never sent.
- When an argument can't be parsed unambiguously, send nothing. Reply with the corrected command and let the user send
  it or change it.
- Work in the current project, environment, and system. Resolve them with
  [tdm-select-context](../tdm-select-context/SKILL.md) when they aren't set, and don't accept a per-command override.
- `update-records` changes every row that matches, reserved or not, and a request that matches nothing still looks
  like a normal answer, so count the matching rows first. Read the table twice with the paged read of
  [tdm-browse-test-data-table](../tdm-browse-test-data-table/SKILL.md) and the `where` pairs as `Equals` filters, once
  with `occupied` false and once with true. The sum of the two `records` values is the number of rows the update would
  change. Use the title-to-name mapping as [tdm-find](../tdm-find/SKILL.md) describes.
- Match each `set` column against the header of that response exactly. If one isn't there, send nothing: name it and
  show the real columns.
- No matching row: send nothing and say so. More than one matching row and no `all`: send nothing, say how many rows
  match, and ask the user to narrow `where` or add `all`. Otherwise send one `update-row-requests` entry.
- A value with an invisible character is handled as the package instruction describes: confirm it with the user before
  sending.
- Reply with the table, the number of rows changed, and the columns set. When some of the matching rows were reserved,
  say how many.
- An unreachable server and a `where` column the table doesn't have are handled as the "Common pitfalls" of
  [tdm-reserve-test-data](../tdm-reserve-test-data/SKILL.md) describe: report them, change nothing, and don't retry
  with a guessed address or name.
