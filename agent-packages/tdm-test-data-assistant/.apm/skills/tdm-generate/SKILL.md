---
name: tdm-generate
description: Use only when the user types /tdm-generate. Starts the project's GitLab pipeline that generates test data, with an explicit test case or scope and environment, and waits until it ends.
disable-model-invocation: true
argument-hint: "test=<name> | scope=<name> [env=<environment>] [branch=<branch>] [VARIABLE=value ...] | help"
metadata:
  opencode/slash: "true"
  opencode/autoinvoke: "false"
---

# /tdm-generate

A command frontend for [tdm-generate-test-data](../tdm-generate-test-data/SKILL.md). It reads the user's arguments
literally and runs the script that skill describes; the confirmation, the way of waiting, and the handling of the
result stay there. For an ordinary request in plain language ("generate domestic customers"), follow
tdm-generate-test-data directly instead.

## Arguments

```text
/tdm-generate test=<name> | scope=<name> | type=<type> name=<name>  [env=<environment>] [branch=<branch>]
              [repo=<url>] [jira=true|false] [webex=true|false] [<VARIABLE>=<value> ...]
```

| Argument            | Meaning                                                                                     |
|---------------------|---------------------------------------------------------------------------------------------|
| `test=<name>`       | Run the test case with this name: `EXECUTION_TYPE=test`, `EXECUTION_NAME=<name>`.           |
| `scope=<name>`      | Run the scope with this name: `EXECUTION_TYPE=scope`, `EXECUTION_NAME=<name>`.              |
| `type=` and `name=` | Another execution type and its name. Use both together.                                     |
| `env=<environment>` | The project environment, sent as `PIPELINE_RUNTIME_CONFIG` exactly as written.              |
| `branch=<branch>`   | The branch of the test repository: `ATP_TESTS_GIT_REPO_BRANCH`.                             |
| `repo=<url>`        | The test repository: `ATP_TESTS_GIT_REPO_URL`.                                              |
| `jira=`, `webex=`   | `ENABLE_JIRA_INTEGRATION` and `SEND_WEBEX_NOTIFICATION`; `true` or `false`.                 |
| `<VARIABLE>=<value>`| Any other variable of the project's variables file, by its name.                            |

Put a name or a value with spaces in double quotes: `test="Generate domestic customers"`. Everything not given keeps
the value from the project's variables file.

## Behavior

- When the only argument is `help`, `--help`, `-h`, or `?`, send no request. Print the usage from the "Arguments"
  section above, the syntax line and the table of arguments, and nothing else.
- Give exactly one of `test=`, `scope=`, or `type=` with `name=`. With none of them, ask for the test case or the scope.
  Never infer the name.
- When an argument can't be parsed unambiguously, send nothing. Reply with the corrected command and let the user send
  it or change it.
- Map the arguments to `--set` options as the table says, run the script without `--yes`, show the variables, and ask
  for the confirmation, as tdm-generate-test-data describes. Start the pipeline only after a yes.
- Report the result, the failures, and the errors as tdm-generate-test-data describes.
