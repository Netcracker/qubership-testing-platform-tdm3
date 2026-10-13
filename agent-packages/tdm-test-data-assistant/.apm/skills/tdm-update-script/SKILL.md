---
name: tdm-update-script
description: Use only when the user types /tdm-update-script. Pilot of a script-based variant of /tdm-update; replaces column values on the rows of an explicit TDM3 table that match explicit where conditions, by running a script.
disable-model-invocation: true
argument-hint: "table=<title> where Column=value ... set Column=value ... [all] | help"
---

# /tdm-update-script

A pilot. It takes the same arguments as [/tdm-update](../tdm-update/SKILL.md), but a script checks the column names,
counts the matching rows, and sends the request. The agent builds no `curl` command.

## Arguments

The syntax, the quoting rules, and the `help` form are those of the "Arguments" section of
[/tdm-update](../tdm-update/SKILL.md). Parse the user's text the way that section says. Never infer a missing
`table=`, and require at least one pair in `where` and in `set`. A command without `where` would change every row, so
it is never run. When an argument can't be parsed unambiguously, send nothing, reply with the corrected command, and
let the user send it or change it.

## Run

Run the script from the project folder, so that it finds `.tdm-assistant/config.json`. Pass one `--where` per
`where` pair and one `--set` per `set` pair, quoting each value for the shell, and `--all` when the user wrote `all`.

```bash
python <skill folder>/scripts/update_rows.py --table "<title>" --where "<Column>=<value>" [--where ...] --set "<Column>=<value>" [--set ...] [--all]
```

If `python` isn't found, try `python3` or `py -3`.

## Behavior

- On success, reply with the table, `updated` (the number of rows changed), and the columns in `set`. When
  `matched_reserved` is more than zero, say how many of the matching rows were reserved: TDM3 changes reserved rows too.
- `no_row_matched` true means no row matched and nothing was sent. Say so.
- `ok: false` is an error, and nothing was changed. Report `message` in plain language:
  - `multiple_matches`: say how many rows match (`matched`) and ask the user to narrow `where` or add `all`.
  - `invisible_characters`: name the characters from `values` and ask the user to confirm the value, because TDM3 would
    store the character. Run the script again with `--confirm-invisible` only after a yes.
  - `column_not_found`: name the column and show `columns`. When `suggestion` is present, say that column names are
    case-sensitive and that the table has `suggestion`; don't rerun with it unless the user agrees.
  - `table_not_found`: show the tables from `tables` as Name and ID.
  - `rejected`: TDM3 refused the update. Report `message`.
  - `config_missing` or `config_incomplete`: resolve the context with
    [tdm-select-context](../tdm-select-context/SKILL.md), then run the script again.
  - `server_unreachable`: the server in the config doesn't answer. Say so.
  - `http_error`: show the status.
