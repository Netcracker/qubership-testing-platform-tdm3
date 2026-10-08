---
name: tdm-load
description: Use only when the user types /tdm-load. Loads an Excel file or the result of a SELECT query into an explicit table of the current TDM3 system.
disable-model-invocation: true
---

# /tdm-load

A command front end for [tdm-import-test-data](../tdm-import-test-data/SKILL.md). It reads the user's arguments
literally and sends the import that skill describes; the request shapes and the response fields stay there. For an
ordinary request in plain language ("load this Excel file into Orders"), follow tdm-import-test-data directly instead.

## Arguments

```text
/tdm-load table=<title> file=<path> [new]
/tdm-load table=<title> query=<SELECT statement> [timeout=<seconds>] [new]
```

| Argument            | Meaning                                                                                       |
|---------------------|-----------------------------------------------------------------------------------------------|
| `table=<title>`     | The table to load into. Required.                                                             |
| `file=<path>`       | An `.xlsx` file to load. Give `file=` or `query=`, not both.                                  |
| `query=<statement>` | A `SELECT` statement run on the current system's database. Give `file=` or `query=`, not both. |
| `timeout=<seconds>` | The query timeout. The default is 1800.                                                       |
| `new`               | The table must not exist yet. If it does, load nothing.                                       |

Put a path or a statement in double quotes when it contains spaces: `file="C:\data\my file.xlsx"`.

## Behavior

- Never infer a missing `table=`, and require exactly one of `file=` and `query=`. Ask for what is missing.
- When an argument can't be parsed unambiguously, send nothing. Reply with the corrected command and let the user send
  it or change it.
- For `file=`, check that the file exists and ends in `.xlsx`. A CSV or `.xls` file is not sent; say that only `.xlsx`
  is read, as tdm-import-test-data describes.
- For `query=`, send only a single statement that starts with `SELECT`. The statement runs on the external database
  of the current system, so don't send anything else, and say why. Don't change the statement.
- Work in the current project, environment, and system. Resolve them with
  [tdm-select-context](../tdm-select-context/SKILL.md) when they aren't set, and don't accept a per-command override.
- Check the catalog of the current system for the title first, as [tdm-list-tables](../tdm-list-tables/SKILL.md)
  describes. If the table exists and `new` is given, load nothing and say so. If it exists and `new` is not given, ask
  the user to confirm loading into the existing table, and send the import only after a yes. If it doesn't exist,
  the import creates it.
- A value with an invisible character is handled as the package instruction describes; a statement or a path that
  contains one is not sent before the user confirms it.
- Read every entry of the response: `processedRows` is the number of rows loaded, and a non-null `error` on an entry
  means that entry failed, even though the HTTP status is 200.
- An unreachable server is handled as the "Common pitfalls" of
  [tdm-reserve-test-data](../tdm-reserve-test-data/SKILL.md) describe: report it, load nothing, and don't retry with a
  guessed address.
- Reply with the table, the number of rows loaded, and whether the table was created or already existed. For a query
  load, say that TDM3 saved the statement with the table.
