---
name: tdm-show-request
description: Use when the user wants the REST request of a data operation (find, reserve, release, insert, update) instead of its result, for example to paste it into a test case as a step. Shows the request as curl or as a Bruno request, with placeholders or with real values, and sends nothing.
user-invocable: false
---

# Show the request of a data operation

Some users need the request, not its result: they paste it into a test case or a Bruno collection. This skill
produces the request, in the format the user asks for, and sends nothing to TDM3.

## Run

A script of each operation builds the request, so the agent writes none by hand. Pass the same arguments as the
operation takes, and add `--print-request curl` or `--print-request bruno`:

| Operation                    | Script                                                  |
|------------------------------|---------------------------------------------------------|
| Find rows                    | `../tdm-find-script/scripts/find_rows.py`               |
| Reserve rows                 | `../tdm-reserve-script/scripts/reserve_rows.py`         |
| Release rows                 | `../tdm-release-script/scripts/release_rows.py`         |
| Insert a row                 | `../tdm-insert-script/scripts/insert_row.py`            |
| Update rows                  | `../tdm-update-script/scripts/update_rows.py`           |

```bash
python <skill folder>/../tdm-reserve-script/scripts/reserve_rows.py --table "<title>" --return "<col>" --filter "<Column>=<value>" --print-request curl
```

Each script takes the arguments that its command describes, for example `--table`, `--return`, and `--filter` for
reserving; see the skill of that script. If `python` isn't found, try `python3` or `py -3`.

- **Format.** Use `curl` unless the user asks for Bruno, and then use `bruno`.
- **Placeholders or values.** The default is placeholders. Add `--values` only when the user asks for real values or
  for the actual data. With placeholders the script needs no config file and no running TDM3. With `--values` it
  reads `.tdm-assistant/config.json` from the project folder, and for finding rows and for releasing all matching
  rows it asks TDM3 for the database name of the table, so TDM3 has to be running.
- **Nothing is sent.** The script prints the request and exits. It does not check the table or the column names.
  Say so, and don't treat the request as checked.

## Placeholders

Context comes as fixed placeholders: `TDM3_BASE_URL`, `projectName`, `envName`, `systemName`, and `occupiedBy`.
A value that the user gave becomes a placeholder named after its column: `--filter status=54` shows `status`, and an
inserted value shows the column name. The new value of an update has the suffix `_new`, so that `where status` and
`set status` stay apart (`status` and `status_new`). The table title and the column names stay as they are. Other
placeholders are `tableName` (the database name of the table, from `GET /api/tdm/tables/catalog`) and `ROW_ID` (the
IDs from a paged read of the occupied rows, for releasing by ID).

curl shows a placeholder as `<name>`, and Bruno as `{{name}}`, which is the Bruno variable syntax.

## Reply

Show the `text` field in a code block, tagged `bash` for curl and `text` for Bruno. For Bruno say that it is the
content of a `.bru` file. List the placeholders that the request holds and what each one stands for, and show the
`notes` of the script when it has any. Say that the request was not sent.

When the user wants the Bruno file in a collection, tell them to save the text as `<name>.bru` there, and don't write
files in their collection yourself.

## Other operations

Deleting rows and the rest of the operations have no script yet. When the user asks for the request of one, take
the request from the "Request" section of the skill of that operation, for example
[tdm-occupy-test-data-rows-by-id](../tdm-occupy-test-data-rows-by-id/SKILL.md) for deleting by ID, replace the values
with placeholders named as above, and say that this request was written by hand from the skill and not produced by a
script.

## Common pitfalls

- Running the operation instead of showing it. Without `--print-request` a script reserves, changes, or releases rows.
- Showing real context in the request by default. A project, environment, and user name in a test case tie it to one
  setup, so use `--values` only when asked.
- Reading `ROW_ID` as a value to fill in. The request that releases by ID needs the IDs from a read of the occupied
  rows; the placeholder says that they come from there.
