---
name: tdm-find
description: Use only when the user types /tdm-find. Shows the rows of an explicit TDM3 table that match explicit Column=value filters, without reserving them.
disable-model-invocation: true
---

# /tdm-find

A command front end for the paged read of [tdm-browse-test-data-table](../tdm-browse-test-data-table/SKILL.md). It
reads the user's arguments literally and shows the matching rows; the request shape and the way to read its grid
response stay in that skill. For an ordinary request in plain language ("find a SIM card with status 54"), follow
[tdm-find-test-data](../tdm-find-test-data/SKILL.md) directly instead. This command never reserves or changes a row.

## Arguments

```text
/tdm-find table=<title> [return=<column>[,<column>...]] [<Column>=<value> ...] [occupied]
```

| Argument            | Meaning                                                                                       |
|---------------------|-----------------------------------------------------------------------------------------------|
| `table=<title>`     | The table to read. Required.                                                                  |
| `return=<columns>`  | The columns to show, separated by commas. Without it, show every data column.                 |
| `<Column>=<value>`  | A filter: the column equals the value. Repeat it to combine filters with AND.                 |
| `occupied`          | Read the occupied rows instead of the available ones.                                         |

Put a name or a value with spaces in double quotes: `"Operator ID"=2502`, `return="Partner ID",sim`. The command
supports equality only. For another comparison (`Contains`, a range), the user asks in plain language.

## Behavior

- Never infer a missing `table=`. Ask for it.
- When an argument can't be parsed unambiguously, send nothing. This covers a word with no `=`, a name with spaces
  and no quotes, and any extra token. Reply with the corrected command and let the user send it or change it.
- Work in the current project, environment, and system. Resolve them with
  [tdm-select-context](../tdm-select-context/SKILL.md) when they aren't set, and don't accept a per-command override.
- Find the table's database name from the catalog of the current system, as
  [tdm-list-tables](../tdm-list-tables/SKILL.md) describes, then read it with `POST /api/tdm/table`: `offset` 0,
  `limit` 50, `occupied` as asked, and one `filters` entry per filter with `Equals`. When the title isn't in the
  catalog, say so and list the tables of the current system. Don't retry with a similar title.
- Reuse the title-to-name mapping from a catalog response already received in this session instead of requesting the
  catalog again. A table that was dropped and created again has a new name, so when the read answers with a raw
  HTTP 500 whose trace says a `findByTableName` result is `null`, the name is stale: request the catalog once, and
  repeat the read with the name found there. A second failure means the table is gone; report it.
- With no filter, show the first rows of the table. Reading is harmless, so don't ask what the user meant.
- Show the rows as a table: the columns of `return=` or every data column. The read comes first, because only its
  response has the table's column names. Match each `return=` name against that header exactly; a name that isn't in
  it is reported together with the real column names, and no row is shown. With `occupied`, also show
  `OCCUPIED_BY` and `OCCUPIED_DATE`. Strip trailing spaces from displayed values; a value padded by a `CHAR` column is
  stored with them.
- State how many rows matched (the `records` field) and, when that is more than 50, that only the first 50 are shown.
- An unreachable server and a filter column the table doesn't have are handled as the "Common pitfalls" of
  [tdm-reserve-test-data](../tdm-reserve-test-data/SKILL.md) describe: report them, and don't retry with a guessed
  address or name.
