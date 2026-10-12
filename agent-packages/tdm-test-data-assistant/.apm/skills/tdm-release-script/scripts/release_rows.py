#!/usr/bin/env python3
"""Release reserved rows of a TDM3 table of the current system.

Prints one JSON object on stdout (the output rules are in tdm_common).

  release_rows.py --table SimCatalog --return sim --filter Column=value [--filter ...] [--all]
  release_rows.py --table SimCatalog --whole-table

  --return       the column whose value identifies each released row; required with a filter
  --all          with filters: release every reserved row that matches, not just one
  --whole-table  release every reserved row of the table, including rows reserved by others; no filters allowed.
                 The caller must have the user's confirmation first.

Without --all, one reserved row must match: TDM3 refuses when more than one does, and nothing is released
(error multiple_matches). With --all, the script reads the matching reserved rows and releases them by ROW_ID.

Success:
  {"ok": true, "table": {...}, "mode": "one" | "all" | "whole-table", "released": ["<value>", ...], "count": 1,
   "no_row_matched": false, "capped": false, "errors": [], "warnings": []}
For --whole-table, "released" is empty and "message" holds the answer of TDM3.

Other error codes: column_not_found, filter_required, multiple_matches, bad_argument.
"""
import argparse
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # keep __pycache__ out of the skill folder
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tdm-common" / "scripts"))
import tdm_common as tdm  # noqa: E402

MAX_ROWS = 200
NO_DATA_PREFIX = "No test data available"
MANY_PREFIX = "More than one value"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--table", required=True, help="table title")
    parser.add_argument("--return", dest="ret", help="column whose value identifies a released row")
    parser.add_argument("--filter", action="append", default=[], help="Column=value; repeat to combine with AND")
    parser.add_argument("--all", action="store_true", help="release every reserved row that matches the filters")
    parser.add_argument("--whole-table", action="store_true", help="release every reserved row of the table")
    args = parser.parse_args()

    filters = tdm.parse_filters(args.filter)
    if args.whole_table and (filters or args.all or args.ret):
        tdm.fail("bad_argument", "--whole-table takes no filters, --all, or --return.")
    if not args.whole_table:
        if not filters:
            tdm.fail("filter_required", "No filter given. Pass --whole-table to release every reserved row of the table.")
        if not args.ret:
            tdm.fail("bad_argument", "--return is required with a filter.")

    config = tdm.load_config(tdm.CONFIG_FULL)
    table = tdm.find_table(config, args.table)
    base = {"table": table, "warnings": tdm.invisible_warnings(filters)}

    if args.whole_table:
        responses = tdm.call(config, "/api/tdm/rest/release-records/bulk", body=tdm.addressing(config, table["name"]))
        errors = [r.get("content") for r in responses if r.get("type") != "SUCCESS"]
        message = next((r.get("content") for r in responses if r.get("type") == "SUCCESS"), None)
        tdm.succeed(**base, mode="whole-table", released=[], count=0, no_row_matched=False, capped=False,
                    errors=errors, message=message)

    header = tdm.header_of(tdm.read_page(config, table["id"], True, [], 1))
    for column, _ in filters:
        tdm.check_column(column, header, "filter")
    tdm.check_column(args.ret, header, "return")

    released, errors, capped, no_row = [], [], False, False

    if not args.all:
        body = tdm.addressing(config, table["name"])
        body["release-row-requests"] = [{
            "search-row-parameters-set": [{"name-column": c, "search-criterion": "Equals", "search-value": v,
                                           "caseSensitive": True} for c, v in filters],
            "name-column-response": args.ret}]
        for response in tdm.call(config, "/api/tdm/rest/release-records", body=body):
            content = response.get("content")
            if response.get("type") == "SUCCESS":
                released.append(content)
            elif isinstance(content, str) and content.startswith(MANY_PREFIX):
                tdm.fail("multiple_matches", "More than one reserved row matches. Nothing was released. "
                         "Narrow the filters or use --all.")
            elif isinstance(content, str) and content.startswith(NO_DATA_PREFIX):
                no_row = True
            else:
                errors.append(content)
    else:
        page = tdm.read_page(config, table["id"], True, filters, MAX_ROWS)
        header = tdm.header_of(page)
        column_index = header.index(args.ret)
        rows = page["data"]["body"]["rows"]
        records = page.get("records", len(rows))
        capped = records > len(rows)
        if not rows:
            no_row = True
        else:
            ids = [row["id"] for row in rows]
            tdm.call(config, "/api/tdm/release", query={"tableName": table["id"]}, body=ids, method="PUT")
            for row in rows:
                value = (row["columns"][1:][column_index].get("contentModel") or {}).get("value")
                released.append(value.rstrip(" ") if isinstance(value, str) else value)

    tdm.succeed(**base, mode="all" if args.all else "one", released=released, count=len(released),
                no_row_matched=no_row, capped=capped, errors=errors)


if __name__ == "__main__":
    tdm.use_utf8_output()
    main()
