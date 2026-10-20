---
name: tdm-release-script
description: Use only when the user types /tdm-release-script. Pilot of a script-based variant of /tdm-release; releases reserved rows of an explicit TDM3 table by running a script.
disable-model-invocation: true
argument-hint: "table=<title> return=<col> Column=value ... [all] | table=<title> all | help"
metadata:
  opencode/slash: "true"
  opencode/autoinvoke: "false"
---

# /tdm-release-script

A pilot. It takes the same arguments as [/tdm-release](../tdm-release/SKILL.md), but a script finds the table, checks
the column names, and sends the requests. The agent builds no `curl` command.

## Arguments

The syntax, the quoting rules, and the `help` form are those of the "Arguments" section of
[/tdm-release](../tdm-release/SKILL.md). Parse the user's text the way that section says. Never infer a missing
`table=`, and never infer a missing `return=` when there is a filter. With no filter and no `all`, send nothing and
say what the command needs. When an argument can't be parsed unambiguously, send nothing, reply with the corrected
command, and let the user send it or change it.

## Run

Run the script from the project folder, so that it finds `.tdm-assistant/config.json`. Pass one `--filter` per
`<Column>=<value>` pair, quoting each value for the shell.

```bash
python <skill folder>/scripts/release_rows.py --table "<title>" --return "<col>" --filter "<Column>=<value>" [--filter ...] [--all]
python <skill folder>/scripts/release_rows.py --table "<title>" --whole-table
```

`--whole-table` frees every reserved row of the table, including rows reserved by others. Before using it, ask the
user to confirm, and run it only after a yes. If `python` isn't found, try `python3` or `py -3`.

## Behavior

- The script prints one JSON object. On success, `released` holds the value of the `return=` column for each released
  row, and `count` is their number. For `--whole-table`, show `message`. Reply with the table, those values, and the
  count.
- `no_row_matched` true means no reserved row matched. Say so in plain language.
- `capped` true means the script stopped at its limit of rows. Say that more rows may match.
- A non-empty `errors` list carries a message TDM3 returned. Report it as it is.
- `ok: false` is an error, and nothing was released. Report `message` in plain language and don't retry with a guessed
  name:
  - `multiple_matches`: more than one reserved row matches. Ask for a more specific filter, or for `all`.
  - `column_not_found`: name the column and show `columns`. When `suggestion` is present, say that column names are
    case-sensitive and that the table has `suggestion`; don't rerun with it unless the user agrees.
  - `table_not_found`: show the tables from `tables` as Name and ID.
  - `filter_required`: say what the command needs: a filter, or `all` for the whole table.
  - `config_missing` or `config_incomplete`: resolve the context with
    [tdm-select-context](../tdm-select-context/SKILL.md), then run the script again.
  - `server_unreachable`: the server in the config doesn't answer. Say so.
  - `http_error`: show the status.
