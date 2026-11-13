# Copilot instructions

Follow the rules in [AGENTS.md](../AGENTS.md) at the repository root.

The main rule: a change to the REST API (an endpoint, a request parameter, or a response type) can break the skills,
scripts, instructions, and documentation in `agent-packages/tdm-test-data-assistant`. Before you change a controller
or a DTO, check that package, tell the user which parts it affects, and ask for confirmation before any change that
breaks the existing skills. `AGENTS.md` has the checklist and the table of controllers and skills.
