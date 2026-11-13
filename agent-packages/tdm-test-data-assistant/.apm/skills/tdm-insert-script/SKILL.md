---
name: tdm-insert-script
description: Use only when the user types /tdm-insert-script. Pilot of a script-based variant of /tdm-insert; inserts one row into an existing TDM3 table, or creates the table with that row when new is given, by running a script.
disable-model-invocation: true
argument-hint: "table=<title> Column=value ... [new] | help"
metadata:
  opencode/slash: "true"
  opencode/autoinvoke: "false"
---

# /tdm-insert-script

A pilot. It takes the same arguments as [/tdm-insert](../tdm-insert/SKILL.md), but a script checks the catalog and the
column names and sends the request. The agent builds no `curl` command.

## Arguments

The syntax, the quoting rules, and the `help` form are those of the "Arguments" section of
[/tdm-insert](../tdm-insert/SKILL.md). Parse the user's text the way that section says. Never infer a missing
`table=`, and ask for at least one `<Column>=<value>`. When an argument can't be parsed unambiguously, send nothing,
reply with the corrected command, and let the user send it or change it.

## Run

Run the script from the project folder, so that it finds `.tdm-assistant/config.json`. Pass one `--value` per
`<Column>=<value>` pair, quoting each value for the shell, and `--new` when the user wrote `new`.

```bash
python <skill folder>/scripts/insert_row.py --table "<title>" --value "<Column>=<value>" [--value ...] [--new]
```

If `python` isn't found, try `python3` or `py -3`.

## Behavior

- On success, reply with the table, whether it was created (`created`) or already existed, and the values in
  `inserted`.
- `ok: false` is an error, and nothing was inserted. Report `message` in plain language:
  - `invisible_characters`: name the characters from `values` and ask the user to confirm the value, because TDM3 would
    store the character. Run the script again with `--confirm-invisible` only after a yes.
  - `table_not_found`: say that the table wasn't found, show the tables from `tables` as Name and ID, and mention
    `new`. Don't retry with a similar title.
  - `table_exists`: the user wrote `new` for a table that exists. Say so.
  - `column_not_found`: name the column and show `columns`. When `suggestion` is present, say that column names are
    case-sensitive and that the table has `suggestion`; don't rerun with it unless the user agrees.
  - `rejected`: TDM3 refused the insert. Report `message`.
  - `config_missing` or `config_incomplete`: resolve the context with
    [tdm-select-context](../tdm-select-context/SKILL.md), then run the script again.
  - `server_unreachable`: the server in the config doesn't answer. Say so.
  - `http_error`: show the status.
