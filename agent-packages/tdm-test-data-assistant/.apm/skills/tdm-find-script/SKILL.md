---
name: tdm-find-script
description: Use only when the user types /tdm-find-script. Pilot of a script-based variant of /tdm-find; shows the rows of an explicit TDM3 table that match explicit Column=value filters by running a script.
disable-model-invocation: true
argument-hint: "table=<title> [return=<cols>] [Column=value ...] [occupied] | help"
---

# /tdm-find-script

A pilot. It takes the same arguments as [/tdm-find](../tdm-find/SKILL.md) and shows the same rows, but a script
finds the table, checks the column names, sends the request, and reads the response. The agent builds no `curl`
command. The command never reserves or changes a row.

## Arguments

The syntax, the quoting rules, and the `help` form are those of the "Arguments" section of
[/tdm-find](../tdm-find/SKILL.md). Parse the user's text the way that section says. When an argument can't be parsed
unambiguously, send nothing, reply with the corrected command, and let the user send it or change it. Never infer a
missing `table=`.

## Run

Run the script from the project folder, so that it finds `.tdm-assistant/config.json`. Pass one `--filter` per
`<Column>=<value>` pair, quoting each value for the shell, and `--return` as a comma-separated list.

```bash
python <skill folder>/scripts/find_rows.py --table "<title>" [--return "<col>,<col>"] [--filter "<Column>=<value>" ...] [--occupied]
```

If `python` isn't found, try `python3` or `py -3`.

## Behavior

- The script prints one JSON object. On success, `columns` names the columns and each entry of `rows` has `values` in
  that order. Show a table of those columns, in that order, without `row_id`, and say how many rows matched
  (`records`). When `truncated` is true, say that only the first `shown` rows are shown.
- With no rows, say that no row matched, and for which filters.
- `warnings` carry invisible characters in a filter value. Pass them on in the reply: the value was sent as typed, and
  the character may be the reason for an empty result.
- `ok: false` is an error. Report `message` in plain language and don't retry with a guessed name:
  - `column_not_found`: name the column and show `columns`. When `suggestion` is present, say that column names are
    case-sensitive and that the table has `suggestion`; don't rerun with it unless the user agrees.
  - `table_not_found`: show the tables from `tables` as Name and ID.
  - `config_missing` or `config_incomplete`: resolve the context with
    [tdm-select-context](../tdm-select-context/SKILL.md), then run the script again.
  - `server_unreachable`: the server in the config doesn't answer. Say so.
  - `http_error`: show the status.
