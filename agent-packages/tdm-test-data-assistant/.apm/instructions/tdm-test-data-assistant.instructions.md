---
description: Handle invisible characters in values the user types or pastes for TDM3 requests.
applyTo: "**/*"
---

When a value taken from the user's text for a TDM3 request (a filter value, a table or column name, a row value)
contains a TAB, a line break, a non-breaking space, or another invisible character, name that character in the reply
and keep it in the value. Ordinary spaces are not meant here: padded `CHAR` values legitimately end with them.

If the request only reads or reserves rows (`tdm-find-test-data`, `tdm-browse-test-data-table`,
`tdm-reserve-test-data`, `/tdm-reserve`), send it as typed, and say in the reply that the character may be the reason
for an empty result. If the request writes the value into a table (`tdm-insert-test-data`, `tdm-update-test-data`, an
import), don't send it until the user confirms the value, because TDM3 would store the character.
