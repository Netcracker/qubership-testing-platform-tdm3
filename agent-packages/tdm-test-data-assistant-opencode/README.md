# tdm-test-data-assistant-opencode

The slash commands of [tdm-test-data-assistant](../tdm-test-data-assistant/README.md) for OpenCode. OpenCode lists
commands in its `/` menu but not skills, so the skills of that package alone give no `/tdm-reserve` there. This package
adds one command file for each command skill, and it depends on `tdm-test-data-assistant`, so one install brings both.

## Install

Run `apm install` in your project folder:

```bash
apm install --target opencode Netcracker/qubership-testing-platform-tdm3/agent-packages/tdm-test-data-assistant-opencode
```

The command installs from the default branch. To install a release, add `#<tag>` to the end of the command. The main
package then comes from the same tag.

APM writes the commands to `.opencode/commands/` and the skills to `.agents/skills/`. Run `apm compile` once, so that
the instruction of the main package reaches `AGENTS.md`. Restart OpenCode, then type `/tdm-context help` to check the
installation. The command prints its syntax and sends no request to TDM3.

Use this package for OpenCode only. For Claude Code, Codex, and Cursor, install `tdm-test-data-assistant` itself: the
commands of this package would appear next to the skills with the same names.

## Commands

Each command file loads the skill of the same name and passes it the text after the command name as `$ARGUMENTS`. The
rules, the arguments, and the safety checks stay in the skill. The package has no logic of its own.

The package ships fifteen commands:

- Nine commands: `/tdm-context`, `/tdm-tables`, `/tdm-find`, `/tdm-reserve`, `/tdm-release`, `/tdm-insert`,
  `/tdm-update`, `/tdm-delete`, and `/tdm-load`.
- Six script pilots: `/tdm-find-script`, `/tdm-insert-script`, `/tdm-list-tables-script`, `/tdm-release-script`,
  `/tdm-reserve-script`, and `/tdm-update-script`.

The arguments and examples are in the
[Commands section](../tdm-test-data-assistant/README.md#commands) of the main package.

## Keeping it in sync

The command files are generated from the `argument-hint` of each skill in `tdm-test-data-assistant`. When a command
skill is added, renamed, or gets another `argument-hint`, change the matching file in `.apm/prompts/` too.
