---
name: tdm-tables
description: Use only when the user types /tdm-tables. Lists the test data tables of the current TDM3 system with their database names.
disable-model-invocation: true
---

# /tdm-tables

A command front end for [tdm-list-tables](../tdm-list-tables/SKILL.md). It takes no arguments and lists the tables of
the current system. For a plain-language request ("show the tables"), follow tdm-list-tables directly instead.

## Arguments

```text
/tdm-tables
```

## Behavior

- Any argument is an error. Send no request, say that `/tdm-tables` takes none, and suggest the plain-language
  request "show the tables of the whole project" if that is what the user wanted.
- Work in the current project, environment, and system. Resolve them with
  [tdm-select-context](../tdm-select-context/SKILL.md) when they aren't set.
- Send the catalog request that tdm-list-tables describes, with `projectId` and `systemId` from the config file.
- Show a table with the title, the database name, and the last-usage date of each table. Show only these tables;
  don't mention tables of other systems, and don't send extra requests to find them.
- When the list is empty, say that the current system has no tables yet, name the system, and don't suggest that the
  context is wrong unless the user asks.
- An unreachable server is handled as the "Common pitfalls" of
  [tdm-reserve-test-data](../tdm-reserve-test-data/SKILL.md) describe: report it, and don't retry with a guessed
  address.
