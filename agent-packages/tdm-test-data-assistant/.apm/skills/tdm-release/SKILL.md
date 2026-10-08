---
name: tdm-release
description: Use only when the user types /tdm-release. Releases reserved rows of an explicit TDM3 table that match explicit Column=value filters, or every reserved row of the table after a confirmation.
disable-model-invocation: true
---

# /tdm-release

A command front end for the release part of [tdm-reserve-test-data](../tdm-reserve-test-data/SKILL.md). It reads the
user's arguments literally and sends the release that skill describes; the request shapes and the response handling
stay there. For an ordinary request in plain language ("release the SIM card I reserved"), follow
tdm-reserve-test-data directly instead.

## Arguments

```text
/tdm-release table=<title> return=<column> <Column>=<value> [<Column>=<value> ...] [all]
/tdm-release table=<title> all
```

| Argument            | Meaning                                                                                       |
|---------------------|-----------------------------------------------------------------------------------------------|
| `table=<title>`     | The table to release rows in. Required.                                                       |
| `return=<column>`   | The column whose value identifies each released row. Required when there is a filter.         |
| `<Column>=<value>`  | A filter: the column equals the value, ignoring case. Repeat it to combine filters with AND.  |
| `all`               | With filters: every reserved row that matches. Without filters: every reserved row of the table. |

Put a name or a value with spaces in double quotes: `"Operator ID"=2502`, `Assignment="QA 1"`. The command supports
equality only.

## Behavior

- Never infer a missing `table=`, and never infer a missing `return=` when there is a filter. Ask for it.
- When an argument can't be parsed unambiguously, send nothing. This covers a word with no `=`, a name with spaces
  and no quotes, and any extra token. Reply with the corrected command and let the user send it or change it.
- With no filter and no `all`, send nothing and say what the command needs: a filter, or `all` for the whole table.
  An empty filter would match every reserved row.
- Work in the current project, environment, and system. Resolve them with
  [tdm-select-context](../tdm-select-context/SKILL.md) when they aren't set, and don't accept a per-command override.
- With filters and no `all`, send one `release-records` entry. When TDM3 reports that more than one reserved row
  matches, release nothing: say so and ask the user for a more specific filter or for `all`.
- With filters and `all`, read the reserved rows that match with the paged read of
  [tdm-browse-test-data-table](../tdm-browse-test-data-table/SKILL.md) (`occupied` true, one `Equals` filter each),
  (the title-to-name mapping and its refresh are described in [tdm-find](../tdm-find/SKILL.md)),
  take their `ROW_ID` values, and release them by ID with
  [tdm-occupy-test-data-rows-by-id](../tdm-occupy-test-data-rows-by-id/SKILL.md). That lookup is needed because
  `release-records` fails on more than one match.
- With `all` and no filter, first ask the user to confirm: this frees every reserved row of the table, including rows
  reserved by others. After a yes, call `release-records/bulk`.
- When TDM3 answers that the table was not found, say so and list the tables of the current system with
  [tdm-list-tables](../tdm-list-tables/SKILL.md). Don't retry with a similar title.
- An unreachable server and a column the table doesn't have are handled as the "Common pitfalls" of
  tdm-reserve-test-data describe: report them, release nothing, and don't retry with a guessed address or name.
- Reply with the table, the value of the `return=` column for each released row (or, for the whole table, the number
  of rows released), and "no reserved row matched" in plain language when nothing matched.
