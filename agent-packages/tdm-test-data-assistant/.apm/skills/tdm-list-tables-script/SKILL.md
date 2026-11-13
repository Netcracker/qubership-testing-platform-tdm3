---
name: tdm-list-tables-script
description: Use only when the user types /tdm-list-tables-script. Pilot of a script-based variant of tdm-list-tables; lists the test data tables of the current TDM3 system by running a script.
disable-model-invocation: true
argument-hint: "[--whole-project]"
metadata:
  opencode/slash: "true"
  opencode/autoinvoke: "false"
---

# /tdm-list-tables-script

A pilot. It does what [tdm-list-tables](../tdm-list-tables/SKILL.md) does for the catalog of the current system, but
a script sends the request and reads the response, so the agent builds no `curl` command.

## Run

```bash
python <skill folder>/scripts/list_tables.py
python <skill folder>/scripts/list_tables.py --whole-project
```

Run the script from the project folder, so that it finds `.tdm-assistant/config.json`. Use `--whole-project` only when
the user explicitly asks for the tables of the whole project. If `python` isn't found, try `python3` or `py -3`.

## Behavior

- The script prints one JSON object. On success it has `tables`, a list of `{name, id}`. Show it as a table of two
  columns, **Name** and **ID**, and nothing else; mention the system from the `system` field once.
- An empty `tables` list means the current system has no tables yet. Say so and name the system.
- `ok: false` means an error. Report `message` in plain language and don't retry with a guessed address:
  - `config_missing` or `config_incomplete`: resolve the context with
    [tdm-select-context](../tdm-select-context/SKILL.md), then run the script again.
  - `server_unreachable`: the server in the config doesn't answer. Say so.
  - `http_error`: show the status from `message`.
- Don't mention tables of other systems and don't send extra requests to find them.
