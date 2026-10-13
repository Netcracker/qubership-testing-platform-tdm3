---
name: tdm-generate-test-data
description: Use when the user wants to generate test data, that is, to run the project's GitLab pipeline that executes the test cases which fill the business tables and the TDM3 tables. Starts the pipeline with parameters and waits until it ends.
user-invocable: false
---

# Generate test data

Generating test data means running a GitLab pipeline. The pipeline checks out the project's test cases and runs them,
for example "Generate domestic customers". The test cases fill the business tables of the project, and as their last
step they send requests to TDM3 that fill the tables of the rows to reserve. This skill starts the pipeline, waits for
it, and reports how it ended. It writes nothing to TDM3 itself.

## Run

A script does the work: it reads the pipeline variables from the project's JSON file, replaces the ones the user
named, starts the pipeline, and polls its status until it ends. The agent builds no request, and never sees the
GitLab token.

```bash
python <skill folder>/scripts/generate_test_data.py [--set VARIABLE=value ...]
python <skill folder>/scripts/generate_test_data.py [--set VARIABLE=value ...] --yes
```

Run the script from the project folder, so that it finds `.tdm-assistant/config.json`. If `python` isn't found, try
`python3` or `py -3`. Quote each value for the shell.

1. Collect the values the user gave and map them to variables (below). Pass only what the user said; the file holds
   the project's defaults for everything else. If the user named no test case or scope, ask for it. Don't invent a
   name, and don't check one: the pipeline knows which names exist.
2. Run the script without `--yes`. It starts nothing and prints the variables that would be sent. Show them in a table,
   mark the ones that differ from the file, and say that the run will change data in the project's databases and in
   TDM3. Ask the user to confirm.
3. After a yes, run the script with `--yes` and the same `--set` options. It runs for minutes: run it in the
   background if your tool allows it, and don't send other requests while you wait. The script prints the pipeline
   link to its progress output as soon as the pipeline starts, so tell the user the link when you have it.
4. When the script ends, report the result (below).

## Variables

| User says                              | Variable                                         |
|----------------------------------------|--------------------------------------------------|
| a test case, "run the test X"          | `EXECUTION_TYPE=test`, `EXECUTION_NAME=X`        |
| a scope, "run the scope X"             | `EXECUTION_TYPE=scope`, `EXECUTION_NAME=X`       |
| another execution type                 | `EXECUTION_TYPE=<type>`, `EXECUTION_NAME=<name>` |
| an environment                         | `PIPELINE_RUNTIME_CONFIG=<as the user wrote it>` |
| a branch of the test repository        | `ATP_TESTS_GIT_REPO_BRANCH`                      |
| a repository of the tests              | `ATP_TESTS_GIT_REPO_URL`                         |
| Jira integration on or off             | `ENABLE_JIRA_INTEGRATION=true` or `false`        |
| a Webex notification on or off         | `SEND_WEBEX_NOTIFICATION=true` or `false`        |

The environment is a project environment. It does not have to match the environment of the TDM3 context, so don't
take it from `.tdm-assistant/config.json`, and pass what the user wrote without changing it. Any other variable that
the file holds can be set the same way, by its name.

## Result

- `succeeded` true: say that the pipeline succeeded, with the link and the time it took (`waited_seconds`).
- `succeeded` false: the pipeline ended as `failed`, `canceled`, `skipped`, or `manual`. Show the status, the link
  (`pipeline.web_url`), the variables that were sent, and `failed_jobs` (name, stage, link) when it has any. Don't guess
  the cause.
- `ok: false` is an error:
  - `invalid_variables`: show `problems` and ask the user to correct the values. Nothing was started.
  - `token_missing`: nothing was started. Tell the user to set the environment variable named in the message, or to put
    the token into the file named there. Never ask the user to paste the token into the chat.
  - `timeout` or `poll_failed`: the pipeline was started and may still be running. Show `pipeline.web_url` and the last
    status, and say that this skill cannot stop it.
  - `http_error` or `server_unreachable`: GitLab refused or didn't answer. Show the message and the status. An
    HTTP 401 or 403 usually means the token is wrong or lacks the right to run pipelines.
  - `config_missing` or `config_incomplete`: the GitLab settings are missing. See the `README` for the keys.

## Common pitfalls

- Starting without the user's confirmation. The run changes data, and cannot be cancelled from here.
- Starting a second run because the first looks slow. The script has no protection against two runs at once.
- Reporting a finished pipeline as a success of the data generation. Report the pipeline status as it is; whether the
  tables are filled is checked by reading them, for example with [tdm-list-tables](../tdm-list-tables/SKILL.md).
