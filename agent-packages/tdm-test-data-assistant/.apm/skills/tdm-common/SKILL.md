---
name: tdm-common
description: Support files for the scripts of the other tdm skills. Not a task; never invoke it.
user-invocable: false
disable-model-invocation: true
metadata:
  opencode/autoinvoke: "false"
---

# tdm-common

This folder holds `scripts/tdm_common.py`, the code that the scripts of the other skills share: reading
`.tdm-assistant/config.json`, calling TDM3, finding a table by title, checking column names, and printing the result as
JSON. A script imports it from the sibling folder `../../tdm-common/scripts`. The folder is a skill only because that
is the unit that `apm install` copies.
