"""Release reserved rows of a TDM3 table of the current system.

Prints one JSON object on stdout (the output rules are in tdm_common).

  release_rows.py --table SimCatalog --return sim --filter Column=value
                  [--filter ...] [--all]
  release_rows.py --table SimCatalog --whole-table

  --return       the column whose value identifies each released row; required
                 with a filter
  --all          with filters: release every reserved row that matches, not one
  --whole-table  release every reserved row of the table, including rows reserved
                 by others; no filters allowed. The caller must have the
                 confirmation of the user first.

Without --all, one reserved row must match: TDM3 refuses when more than one does,
and nothing is released (error multiple_matches). With --all, the script reads
the matching reserved rows and releases them by ROW_ID.

Success:
  {"ok": true, "table": {...}, "mode": "one" | "all" | "whole-table",
   "released": ["<value>", ...], "count": 1, "no_row_matched": false,
   "capped": false, "errors": [], "warnings": []}
For --whole-table, "released" is empty and "message" holds the answer of TDM3.

Other error codes: column_not_found, filter_required, multiple_matches,
bad_argument.
"""

import argparse
import importlib
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # keep __pycache__ out of the skill folder
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tdm-common" / "scripts"))
tdm = importlib.import_module("tdm_common")

MAX_ROWS = 200
NO_DATA_PREFIX = "No test data available"
MANY_PREFIX = "More than one value"


def parse_arguments():
    """Return the parsed command line."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--table", required=True, help="table title")
    parser.add_argument("--return", dest="ret", help="column that identifies a row")
    parser.add_argument(
        "--filter",
        action="append",
        default=[],
        help="Column=value; repeat to combine with AND",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="release every reserved row that matches the filters",
    )
    parser.add_argument(
        "--whole-table",
        action="store_true",
        help="release every reserved row of the table",
    )
    return parser.parse_args()


def check_arguments(args, filters):
    """Fail on a combination of arguments that the script does not accept."""
    if args.whole_table:
        if filters or args.all or args.ret:
            tdm.fail(
                "bad_argument", "--whole-table takes no filters, --all, or --return."
            )
        return
    if not filters:
        tdm.fail(
            "filter_required",
            "No filter given. Pass --whole-table to release every reserved row.",
        )
    if not args.ret:
        tdm.fail("bad_argument", "--return is required with a filter.")


def release_whole_table(config, table, base):
    """Release every reserved row of the table with the bulk request."""
    responses = tdm.call(
        config,
        "/api/tdm/rest/release-records/bulk",
        body=tdm.addressing(config, table["name"]),
    )
    errors = [r.get("content") for r in responses if r.get("type") != "SUCCESS"]
    message = next(
        (r.get("content") for r in responses if r.get("type") == "SUCCESS"), None
    )
    tdm.succeed(
        **base,
        mode="whole-table",
        released=[],
        count=0,
        no_row_matched=False,
        capped=False,
        errors=errors,
        message=message,
    )


def release_one(config, table, args, filters):
    """Release the single reserved row that matches; return (values, errors, none)."""
    body = tdm.addressing(config, table["name"])
    body["release-row-requests"] = [
        {
            "search-row-parameters-set": [
                {
                    "name-column": c,
                    "search-criterion": "Equals",
                    "search-value": v,
                    "caseSensitive": True,
                }
                for c, v in filters
            ],
            "name-column-response": args.ret,
        }
    ]
    released, errors, no_row = [], [], False
    for response in tdm.call(config, "/api/tdm/rest/release-records", body=body):
        content = response.get("content")
        if response.get("type") == "SUCCESS":
            released.append(content)
        elif isinstance(content, str) and content.startswith(MANY_PREFIX):
            tdm.fail(
                "multiple_matches",
                "More than one reserved row matches. Nothing was released. "
                "Narrow the filters or use --all.",
            )
        elif isinstance(content, str) and content.startswith(NO_DATA_PREFIX):
            no_row = True
        else:
            errors.append(content)
    return released, errors, no_row


def release_matching(config, table, args, filters):
    """Release every reserved row that matches; return (values, capped)."""
    page = tdm.read_page(config, table["id"], True, filters, MAX_ROWS)
    column_index = tdm.header_of(page).index(args.ret)
    rows = page["data"]["body"]["rows"]
    if not rows:
        return [], False
    ids = [row["id"] for row in rows]
    tdm.call(
        config,
        "/api/tdm/release",
        query={"tableName": table["id"]},
        body=ids,
        method="PUT",
    )
    released = []
    for row in rows:
        cell = row["columns"][1:][column_index]
        value = (cell.get("contentModel") or {}).get("value")
        released.append(value.rstrip(" ") if isinstance(value, str) else value)
    return released, page.get("records", len(rows)) > len(rows)


def main():
    """Release the rows and print the result."""
    args = parse_arguments()
    filters = tdm.parse_filters(args.filter)
    check_arguments(args, filters)

    config = tdm.load_config(tdm.CONFIG_FULL)
    table = tdm.find_table(config, args.table)
    base = {"table": table, "warnings": tdm.invisible_warnings(filters)}
    if args.whole_table:
        release_whole_table(config, table, base)

    header = tdm.header_of(tdm.read_page(config, table["id"], True, [], 1))
    for column, _ in filters:
        tdm.check_column(column, header, "filter")
    tdm.check_column(args.ret, header, "return")

    errors, capped = [], False
    if args.all:
        released, capped = release_matching(config, table, args, filters)
        no_row = not released
    else:
        released, errors, no_row = release_one(config, table, args, filters)

    tdm.succeed(
        **base,
        mode="all" if args.all else "one",
        released=released,
        count=len(released),
        no_row_matched=no_row,
        capped=capped,
        errors=errors,
    )


if __name__ == "__main__":
    tdm.use_utf8_output()
    main()
