# Pull request description

<!-- Fill in each section, and delete a section that does not apply. -->

## Why

<!-- The problem this change solves and the symptom a user sees. -->

## What

<!-- The behavior that changes, and why this approach. -->

## Verification

<!-- What the tests establish, and what you checked by hand. -->

## Test Data Assistant

<!-- Required when the change touches a REST controller, a request or response model, or a status code.
     Rules: AGENTS.md, "Keep Test Data Assistant in sync with the REST API". -->

- [ ] The REST API did not change (no endpoint, parameter, or response type), so the package needs no change.
- [ ] The REST API changed, and `agent-packages/tdm-test-data-assistant` is updated: skills, scripts, instructions,
  documentation, selftest, and the `version` in `apm.yml`.
- [ ] The REST API changed, and the package needs no change. Reason and what was searched:
- [ ] The change breaks existing skills, and the user confirmed it. The commit uses `!` or a `BREAKING CHANGE:` footer.

<!-- If the package changed: what changed in it and why. If the selftest was not run, say so. -->
