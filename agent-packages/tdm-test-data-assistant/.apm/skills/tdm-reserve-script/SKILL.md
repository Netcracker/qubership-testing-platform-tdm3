---
name: tdm-reserve-script
description: Use only when the user types /tdm-reserve-script. Pilot of a script-based variant of /tdm-reserve; reserves rows of an explicit TDM3 table that match explicit Column=value filters by running a script.
disable-model-invocation: true
argument-hint: "table=<title> return=<col> [Column=value ...] [all] | help"
metadata:
  opencode/slash: "true"
  opencode/autoinvoke: "false"
---

# /tdm-reserve-script

A pilot. It takes the same arguments as [/tdm-reserve](../tdm-reserve/SKILL.md) and reserves the same rows, but a
script finds the table, checks the column names, works out `occupiedBy`, and sends the request. The agent builds no
`curl` command.

## Arguments

The syntax, the quoting rules, and the `help` form are those of the "Arguments" section of
[/tdm-reserve](../tdm-reserve/SKILL.md). Parse the user's text the way that section says. Never infer a missing
`table=` or `return=`. When an argument can't be parsed unambiguously, send nothing, reply with the corrected command,
and let the user send it or change it. With no filter, ask whether the user wants any available row, and run the script
with `--any` only after a yes.

## Run

Run the script from the project folder, so that it finds `.tdm-assistant/config.json`. Pass one `--filter` per
`<Column>=<value>` pair, quoting each value for the shell, and add `--all` when the user wrote `all`.

```bash
python <skill folder>/scripts/reserve_rows.py --table "<title>" --return "<col>" [--filter "<Column>=<value>" ...] [--all | --any]
```

If `python` isn't found, try `python3` or `py -3`.

## Behavior

- The script prints one JSON object. On success, `reserved` holds the value of the `return=` column for each reserved
  row, and `count` is their number. Reply with the table, those values, and the count. Mention `occupied_by`, the name
  the rows are recorded under.
- `no_row_matched` true means TDM3 has no available row for these criteria. Say so in plain language: the pool is
  exhausted and new test data is needed. This is not a failure of the request.
- `capped` true means the script stopped at its limit of reserved rows. Say that more rows may match.
- A non-empty `errors` list carries a message TDM3 returned for the request. Report it as it is.
- `warnings` carry invisible characters in a filter value. Pass them on: the value was sent as typed, and the
  character may be the reason for an empty result.
- `ok: false` is an error, and nothing was reserved. Report `message` in plain language and don't retry with a guessed
  name:
  - `column_not_found`: name the column and show `columns`. When `suggestion` is present, say that column names are
    case-sensitive and that the table has `suggestion`; don't rerun with it unless the user agrees.
  - `table_not_found`: show the tables from `tables` as Name and ID.
  - `filter_required`: ask the user whether they want any available row.
  - `config_missing` or `config_incomplete`: resolve the context with
    [tdm-select-context](../tdm-select-context/SKILL.md), then run the script again.
  - `server_unreachable`: the server in the config doesn't answer. Say so.
  - `http_error`: show the status.
