---
name: tdm-context
description: Use only when the user types /tdm-context. Shows the current TDM3 project, environment, and system, or lists, verifies, and switches them from explicit arguments.
---

# /tdm-context

A command front end for [tdm-select-context](../tdm-select-context/SKILL.md). It reads the user's arguments literally
and runs that skill's steps; the algorithm, the endpoints, and the config file handling stay there. For an ordinary
request in plain language ("use system CMS", "show the systems"), follow tdm-select-context directly instead.

## Arguments

| Call                           | What the agent does                                                                          |
|--------------------------------|----------------------------------------------------------------------------------------------|
| `/tdm-context`                 | Shows the current project, environment, and system from the config file, and which are unset. |
| `/tdm-context project=<name>`  | Verifies the name and makes it the current project (step 1 of tdm-select-context).           |
| `/tdm-context env=<name>`      | Verifies the name and makes it the current environment (step 2).                             |
| `/tdm-context system=<name>`   | Verifies the name and makes it the current system (step 3).                                  |
| `/tdm-context list projects`   | Lists the projects, `list envs` the environments, `list systems` the systems.                |

Put a value with spaces in double quotes: `/tdm-context env="Mobile Catalogue"`. Several arguments can share one call;
resolve them in the order project, environment, system. A `list` call only lists and never changes the context.

## Behavior

- With no arguments, don't call TDM3. Print the three names and the server URL from the config file, and name every
  level that has no value.
- A name that doesn't match is reported as tdm-select-context describes: say it isn't known, show the list, and leave
  that level of the config file as it was.
- After a switch, print the resulting context in one line: `<project> → <environment> → <system>`. A level below the
  one that changed, and not named in the same call, is unset (see "Switching a level" in tdm-select-context): print it
  as unset and ask for it, or list it.
- When `TDM3_BASE_URL` is not resolved, ask for it before anything else, as the package README says.
