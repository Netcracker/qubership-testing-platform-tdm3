---
description: Handle invisible characters in values, read non-ASCII responses without corrupting them, and show rows in a fixed layout.
applyTo: "**/*"
---

When a value taken from the user's text for a TDM3 request (a filter value, a table or column name, a row value)
contains a TAB, a line break, a non-breaking space, or another invisible character, name that character in the reply
and keep it in the value. Ordinary spaces are not meant here: padded `CHAR` values legitimately end with them.

If the request only reads or reserves rows (`tdm-find-test-data`, `tdm-browse-test-data-table`,
`tdm-reserve-test-data`, `/tdm-reserve`), send it as typed, and say in the reply that the character may be the reason
for an empty result. If the request writes the value into a table (`tdm-insert-test-data`, `tdm-update-test-data`, an
import), don't send it until the user confirms the value, because TDM3 would store the character.

## Reading responses on Windows

In Windows PowerShell, don't assign the output of `curl.exe` to a variable and decode it again. PowerShell decodes the
bytes in the console code page, and any re-encoding afterwards loses non-ASCII characters, such as a Cyrillic user
name, which turn into `?` or `�`. Save the response to a file (`curl.exe -s -o response.json ...`) and read it as
UTF-8 (`Get-Content -Raw -Encoding UTF8 response.json | ConvertFrom-Json`). Never report a corrupted value as matching the
expected one: if a value looks corrupted, say so and read the response again.

## Showing rows

Every table of rows, from a command or from a plain-language request, follows one layout:

- `OCCUPIED_BY` and `OCCUPIED_DATE` come first, on the left, and only when the rows are reserved ones or the user asked
  for them. The other columns follow in the order of the table's header.
- Column headers are written the same way within one conversation: either always as TDM3 names them, or always in the
  language the user writes in. Don't switch between the two in neighboring replies, and don't show both names in one
  header.
