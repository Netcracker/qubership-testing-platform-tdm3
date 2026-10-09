---
name: tdm-reserve
description: Use only when the user types /tdm-reserve. Reserves test data rows in TDM3 for an explicit table, explicit Column=value filters, and an explicit column to return.
disable-model-invocation: true
argument-hint: "table=<title> return=<col> [Column=value ...] [all] | help"
---

# /tdm-reserve

A command front end for [tdm-reserve-test-data](../tdm-reserve-test-data/SKILL.md). It reads the user's arguments
literally and sends the reservation that skill describes; the request shape, the response handling, and the "reserve
every matching row" pattern stay there. For an ordinary request in plain language ("reserve a SIM card"), follow
tdm-reserve-test-data directly instead.

## Arguments

```text
/tdm-reserve table=<title> return=<column> [<Column>=<value> ...] [all]
```

| Argument            | Meaning                                                                                       |
|---------------------|-----------------------------------------------------------------------------------------------|
| `table=<title>`     | The table to reserve in. Required.                                                            |
| `return=<column>`   | The column whose value identifies each reserved row. Required.                                |
| `<Column>=<value>`  | A filter: the column equals the value, ignoring case. Repeat it to combine filters with AND.  |
| `all`               | Reserve every available row that matches, not just one.                                       |

Put a name or a value with spaces in double quotes: `"Operator ID"=2502`, `Assignment="QA 1"`. The command supports
equality only. For another comparison (`Contains`, a range), the user asks in plain language.

## Behavior

- When the only argument is `help`, `--help`, `-h`, or `?`, send no request. Print the usage from the "Arguments"
  section above, the syntax line and the table of arguments, and nothing else.
- Never infer a missing `table=` or `return=`. Ask for it. This command exists so that the user names the table
  explicitly, unlike a plain-language request.
- When an argument can't be parsed unambiguously, send nothing. This covers a word with no `=`, a name with spaces
  and no quotes, and any extra token. Reply with the corrected command, for example
  `/tdm-reserve table=SimCatalog return=sim "Partner ID"=5` for `Partner ID=5`, and let the user send it or change it.
  Don't pick a reading yourself: a wrong guess reserves the wrong row.
- With no `<Column>=<value>` filter, ask whether the user wants any available row. An empty filter reserves the first
  available row, so don't assume that is what a forgotten filter meant.
- Work in the current project, environment, and system. Resolve them with
  [tdm-select-context](../tdm-select-context/SKILL.md) when they aren't set, and don't accept a per-command override.
- Without `all`, reserve one row. With `all`, count the matching rows first and send one entry per row, as
  tdm-reserve-test-data describes under "Reserve every row matching a filter".
- When TDM3 answers that the table was not found, say so and list the tables of the current system with
  [tdm-list-tables](../tdm-list-tables/SKILL.md). Don't retry with a similar title.
- An unreachable server and a column the table doesn't have are handled as the "Common pitfalls" of
  tdm-reserve-test-data describe: report them, reserve nothing, and don't retry with a guessed address or name.
- Reply with the table, the value of the `return=` column for each reserved row, and the number of rows reserved. Report
  "no available row matched" in plain language when TDM3 returns that result.
