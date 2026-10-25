# TDM Test Data Assistant user guide

The `tdm-test-data-assistant` package gives your coding agent skills to find, reserve, change, and load test data in
TDM3. Follow the sections in order the first time: install the package with APM, set the working context, and work with
the data. Later, [update the package](#update-the-package) with two commands.

For the full list of skills and the settings of each one, see the [package `README`](../README.md).

## Before you start

You need:

- A running TDM3 service that you can reach from your machine, and its base URL, for example
  `http://localhost:8080`. The skills send no credentials: they expect TDM3 to be reachable from inside your network.
- A coding agent that APM can deploy to, such as Claude Code, Codex, Cursor, or OpenCode.
- The APM command-line tool, `apm`, on your `PATH`. Run `apm --version` to check. The steps below were checked with APM
  0.29.1.
- Access to `github.com`, because APM downloads the package from the repository.
- Python 3.9 or later, available as `python`, `python3`, or `py -3`. Some skills run a Python script instead of
  building a request by hand, for example `/tdm-generate` and the requests of
  [tdm-show-request](#show-a-request-instead-of-sending-it).

## Choose a folder

Pick the folder in which you start your agent. The skills look for their settings in the folder the session works in,
so everything below happens there. After the steps in this guide the folder holds:

| Item                         | Created by            | Purpose                                                              |
|------------------------------|-----------------------|----------------------------------------------------------------------|
| `apm.yml`, `apm.lock.yaml`   | `apm install`         | The package you depend on, and the exact version that was installed. |
| The agent's folder           | `apm install`         | The deployed skills, for example `.claude/skills/` for Claude Code.  |
| `.tdm-assistant/config.json` | The agent, on request | Your TDM3 address, project, environment, and system.                 |

A folder keeps its own context. Use a separate folder for each TDM3 server or project that you work with, so that a
command never runs against the wrong one.

Choose a folder that is not part of a clone of any Git repository, for example `C:\tdm-assistant` or
`~/tdm-assistant`. The install and the agent write the files in the table above into the folder, and inside a clone
those files can reach a commit by accident.

If you choose a folder inside a clone anyway, keep the files out of the commits. `.tdm-assistant/config.json` is
personal, so add this line to the `.gitignore` of the clone:

```text
/.tdm-assistant/
```

The other files of the table show up in the clone as untracked files. Do not add them to a commit unless the owners of
the repository want them there.

## Install the package

Run `apm install` in the folder, with the package and the agent that you use:

```bash
cd <your folder>
apm install --target claude Netcracker/qubership-testing-platform-tdm3/agent-packages/tdm-test-data-assistant#main
```

The part after `#` is the branch or tag of the repository to follow. Use a tag to stay on one version until you decide
to move.

`--target` names the agent. The values include `claude`, `codex`, `copilot`, `cursor`, `gemini`, and `opencode`; join
several with commas, as in `--target claude,codex`. Without `--target`, APM picks the agent from the folder it finds,
for example a `.claude` folder, and stops with a message when it finds none. APM saves the choice in the `targets` list
of `apm.yml`, so you give it once.

The command does four things:

1. Creates `apm.yml` if the folder has none, and adds the package to its dependencies.
2. Downloads the package and records the commit it resolved to in `apm.lock.yaml`.
3. Deploys the skills and the instruction of the package into the folder of your agent.
4. Adds `apm_modules/` to `.gitignore`.

Codex and OpenCode do not read the instruction of the package from the deployed files. For them, run `apm compile`
once after the install, which writes the instruction into `AGENTS.md`.

Start your agent in the folder. Restart a session that was already running, so that it loads the new skills. To check
the installation without a TDM3 server, type this in the agent:

```text
/tdm-context help
```

The agent prints the syntax of the command and sends no request. In Codex, start a skill by typing `$` and its name,
or pick it from `/skills`.

To give a colleague the same setup, send them `apm.yml` and `apm.lock.yaml`. They put the files in their own folder and
run `apm install` there, which installs the version that the lockfile records.

## Update the package

Check whether the repository has moved ahead of your lockfile:

```bash
apm outdated
```

Preview an update, then apply it:

```bash
apm update --dry-run
apm update
```

`apm update` shows what changes and asks for confirmation. Add `--yes` to skip the question, for example in a script.
It rewrites `apm.lock.yaml` and deploys the new skills. Restart the agent session afterwards.

There is no `apm upgrade` command. These commands are the ones to use:

| Goal                                    | Command                                                                           |
|-----------------------------------------|-----------------------------------------------------------------------------------|
| Move to the newest commit of the branch | `apm update`                                                                      |
| Move to another tag or branch           | Change the part after `#` in the dependency line of `apm.yml`, then `apm install` |
| Update the `apm` tool itself            | `apm self-update`                                                                 |

Your `.tdm-assistant/config.json` stays as it is across an update.

## Set the working context

Every data command works in one context: a TDM3 project, an environment of that project, and a system of that
environment. You set the context once and the skills reuse it. A command never guesses it, and it never takes a
different one for a single call.

Set the context with `/tdm-context`:

1. Type `/tdm-context list projects`. The first time, the agent asks for the base URL of TDM3, because no config file
   exists yet. Give the address as you reach TDM3 from your machine, for example `http://localhost:8080`.
2. The agent shows the projects as a table of **Name** and **ID**. The list holds the projects that the service names
   in its own `PROJECTS_INFO` setting. A project that the setting does not name is missing from the list, even when
   it has data.
3. Choose the project: `/tdm-context project="My Project"`. The agent checks the name against TDM3.
4. Choose the environment and the system the same way. Use `list envs` and `list systems` to see the options.

   ```text
   /tdm-context env=STAGE
   /tdm-context system=BillingDB
   ```

5. The agent asks you to confirm, then saves the result in `.tdm-assistant/config.json`.

Several arguments can share one call, and the agent resolves them in the order project, environment, system. Put a
value with spaces in double quotes. When a name is not known, the agent says so and shows the list instead of choosing
for you.

`/tdm-context` without arguments shows what is set now and which levels are still empty. It sends no request.

You can also ask in plain language, for example "use system CMS" or "show the environments". The data commands start
the same steps when the config file lacks a level, so you can begin with a data command and answer the questions.

### Switch the context

A choice is valid under the level above it. When you change the project, the environment and the system are cleared.
When you change the environment, the system is cleared. Choose each cleared level again; the agent does not carry the
old name over, even when the new parent has a level with the same name. After a switch it prints the result in one
line, `<project> → <environment> → <system>`.

## The config file

Once the context is set, `.tdm-assistant/config.json` in the folder looks like this:

```json
{
  "TDM3_BASE_URL": "http://localhost:8080",
  "PROJECT_ID": "b0c1fd9e-19a7-4156-90e0-f04028a58720",
  "PROJECT_NAME": "MyProject",
  "ENV_ID_DEFAULT": "5f2c1e2a-1234-4a5b-8c9d-abcdef012345",
  "ENV_NAME_DEFAULT": "STAGE",
  "SYSTEM_ID_DEFAULT": "9a8b7c6d-4321-4b5a-9c8d-fedcba987654",
  "SYSTEM_NAME_DEFAULT": "BillingDB"
}
```

| Key                                        | Meaning                                      |
|--------------------------------------------|----------------------------------------------|
| `TDM3_BASE_URL`                            | Base URL of the TDM3 service.                |
| `PROJECT_ID`, `PROJECT_NAME`               | The project in scope.                        |
| `ENV_ID_DEFAULT`, `ENV_NAME_DEFAULT`       | The environment in scope.                    |
| `SYSTEM_ID_DEFAULT`, `SYSTEM_NAME_DEFAULT` | The system in scope, within the environment. |

The agent reads exactly two files, in this order: `.tdm-assistant/config.json` in the folder of the session, then
`~/.tdm-assistant/config.json` in your home folder. The home file is an optional personal default for every folder. The
agent writes only to the file in the session folder. It never stores a password or a token in either file.

For each setting, the first of these that has a value wins:

1. A value in your current request.
2. An environment variable with the name of the key, for example `TDM3_BASE_URL`.
3. The config file.
4. A question to you.

The skills trust the file after it is written: they do not check the project, environment, and system again on every
call. If someone renames or deletes one of them in TDM3, run `/tdm-context` with the name again to refresh the file.
To start over, delete `.tdm-assistant/config.json` and set the context again.

## Work with the data

Type a request in plain language, or use one of the commands. A plain request such as "reserve a SIM card with status
54" lets the agent choose the skill and infer details. A command takes explicit arguments and infers nothing: it asks
for a missing table name, and it sends nothing when it cannot read an argument unambiguously. Use commands when a wrong
guess would cost you data.

Every command answers `help`, `--help`, `-h`, or `?` with its syntax and sends no request.

| Command         | What it does                                                           | Example                                                                      |
|-----------------|------------------------------------------------------------------------|------------------------------------------------------------------------------|
| `/tdm-context`  | Shows, checks, and switches the project, environment, and system.      | `/tdm-context system=PostgresDB`                                             |
| `/tdm-tables`   | Lists the tables of the current system.                                | `/tdm-tables`                                                                |
| `/tdm-find`     | Shows the rows that match the filters, without reserving them.         | `/tdm-find table=Orders return=ord_num,ord_amount cust_code=C00025`          |
| `/tdm-reserve`  | Reserves one matching row, or every matching row with `all`.           | `/tdm-reserve table=Orders return=ord_num cust_code=C00025 all`              |
| `/tdm-release`  | Releases reserved rows.                                                | `/tdm-release table=Orders return=ord_num cust_code=C00025 all`              |
| `/tdm-insert`   | Adds one row; `new` creates the table.                                 | `/tdm-insert table=Agents agent_code=A013 agent_name=Ivan`                   |
| `/tdm-update`   | Changes column values of the rows that match `where`.                  | `/tdm-update table=Agents where agent_code=A013 set country=IT`              |
| `/tdm-delete`   | Deletes the matching rows after you confirm.                           | `/tdm-delete table=Agents agent_code=A013`                                   |
| `/tdm-load`     | Loads rows from an `.xlsx` file or a `SELECT` query.                   | `/tdm-load table=Agents file="C:\data\agents.xlsx" new`                      |
| `/tdm-generate` | Starts the GitLab pipeline that generates test data, and waits for it. | `/tdm-generate test="Generate domestic customers" env=environments/dev.yaml` |

Arguments are named, so their order does not matter. Put a name or a value with spaces in double quotes, for example
`"Operator ID"=2502`. The filters compare for equality only. For another comparison, such as `Contains` or a range, ask
in plain language.

### Find, reserve, and release rows

Start with the table names: `/tdm-tables`. Then read the rows you want:

```text
/tdm-find table=Orders return=ord_num,ord_amount cust_code=C00025
```

The agent shows the matching rows as a table, states how many rows matched, and says so when it shows only the first
50. Add `occupied` to read the reserved rows instead of the available ones.

To reserve, name the table, the column that identifies each row, and the filters:

```text
/tdm-reserve table=Orders return=ord_num cust_code=C00025
```

Without `all`, the agent reserves one row. A reservation is recorded under the name of your operating-system account.
Without a filter, the agent asks whether you want any available row, because an empty filter reserves the first one.

Release the rows when the test run ends:

```text
/tdm-release table=Orders return=ord_num cust_code=C00025
```

If more than one reserved row matches, the agent releases nothing and asks you to narrow the filter or to add `all`.
`/tdm-release table=Orders all` frees every reserved row of the table, including rows reserved by others, so the agent
asks you to confirm first.

### Add, change, and delete rows

`/tdm-insert` adds one row per call. It checks the table name first, because TDM3 creates a table when it gets an
unknown title. If the table does not exist and you did not give `new`, the agent sends nothing and lists the tables.

`/tdm-update` counts the rows that match `where` before it changes anything. It sends nothing when no row matches, and
when several do, it asks you to narrow `where` or to add `all`.

`/tdm-delete` shows the rows it would delete, marks the reserved ones with the user who holds them, and waits for a
clear yes. A deletion is permanent.

When a value you type or paste contains an invisible character, such as a TAB or a non-breaking space, the agent names
the character. For a reading it sends the value as typed and tells you the character may explain an empty result. For a
writing it waits for your confirmation first, because TDM3 would store the character.

### Load rows from a file or a query

```text
/tdm-load table=Agents file="C:\data\agents.xlsx" new
/tdm-load table=Agents query="SELECT * FROM agents" timeout=600
```

Give `file=` or `query=`, not both. Only `.xlsx` files are read. The query must be a single `SELECT` statement, and it
runs on the external database of the current system. `new` means that the table must not exist yet; without it, the
agent asks you to confirm before it loads into an existing table. The agent reports the number of rows loaded, and
reports an entry that failed even when TDM3 answered with HTTP 200.

### Generate test data

`/tdm-generate` starts the GitLab pipeline of your project, which runs test cases that fill TDM3 tables. It needs the
GitLab settings in the config file and a token in the `GITLAB_TOKEN` environment variable. See
[Generating test data](../README.md#generating-test-data) in the `README` for the keys. The agent shows the pipeline
variables and asks you to confirm before it starts the pipeline, then waits up to 30 minutes for the result.

### Show a request instead of sending it

To paste a request into a test case, ask for the request, not its result: "show the cURL request for reserving rows of
Orders". The agent prints the request as cURL or as a Bruno file and sends nothing. The context and your values come as
placeholders, `<name>` in cURL and `{{name}}` in Bruno. Ask for real values when you want them. The request is not
checked against the table.

### Other tasks in plain language

Some tasks have no command and are done by asking: statistics and their email schedules, cleanup rules, exporting a
table to Excel or CSV, changing the title of a table, and creating or changing dynamic environments. The
[`README`](../README.md#skills) lists each skill with its scope.

## Troubleshooting

| Symptom                                    | Cause and action                                                                                             |
|--------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| The agent does not know `/tdm-context`     | The agent has not loaded the skills. Check that `apm install` finished, then restart the session.            |
| `apm install` stops and asks for a target  | APM found no agent in the folder. Add `--target <agent>`.                                                    |
| The agent says the server is unreachable   | TDM3 does not answer at `TDM3_BASE_URL`. Start the service or correct the address; the agent tries no other. |
| A project is missing from the list         | The service lists only the projects named in its `PROJECTS_INFO` setting.                                    |
| A list of environments or systems is empty | Check that you chose the right project or environment before you assume that nothing is registered.          |
| "Table not found"                          | The title differs from the one in TDM3. Run `/tdm-tables` and use a title from the list.                     |
| A command works on the wrong data          | The context is not the one you think. Run `/tdm-context` and compare it with the config file.                |
| Codex or OpenCode ignores the instruction  | Run `apm compile` in the folder.                                                                             |
