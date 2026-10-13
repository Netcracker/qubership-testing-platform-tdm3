"""Replace column values in the rows of a TDM3 table that match conditions.

Prints one JSON object on stdout (the output rules are in tdm_common).

  update_rows.py --table Agents --where Column=value [--where ...]
                 --set Column=value [--set ...] [--all] [--confirm-invisible]

  --where              a condition: the column equals the value (exact,
                       case-sensitive); at least one is required, because an update
                       without a condition would change every row
  --set                a column and its new value; at least one is required
  --all                allow the update to change more than one row
  --confirm-invisible  The user confirmed values that hold invisible characters.
                       TDM3 would store them, so without the flag the script refuses.

TDM3 updates every matching row, reserved or not. The script therefore counts the
matches first, in the available and the reserved rows, and sends nothing when none
match, or when several match and --all is missing.

Success:
  {"ok": true, "table": {...}, "matched": 1, "matched_reserved": 0, "updated": 1,
   "set": {"commission": "0.20"}, "message": "<TDM3 answer>", "warnings": []}
When nothing matches:
  {"ok": true, ..., "matched": 0, "updated": 0, "no_row_matched": true}

Other error codes: column_not_found, multiple_matches, invisible_characters,
rejected, bad_argument.
"""

import argparse
import importlib
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # keep __pycache__ out of the skill folder
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tdm-common" / "scripts"))
tdm = importlib.import_module("tdm_common")


def parse_arguments():
    """Return the parsed command line."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--table", required=True, help="table title")
    parser.add_argument(
        "--where",
        action="append",
        default=[],
        help="Column=value; repeat to combine with AND",
    )
    parser.add_argument(
        "--set",
        dest="sets",
        action="append",
        default=[],
        help="Column=value to write",
    )
    parser.add_argument(
        "--all", action="store_true", help="allow more than one row to change"
    )
    parser.add_argument(
        "--confirm-invisible",
        action="store_true",
        help="values with invisible characters are confirmed",
    )
    return parser.parse_args()


def count_matches(config, table, where):
    """Return (matching rows, of which reserved) for the conditions."""
    available = tdm.read_page(config, table["id"], False, where, 1).get("records", 0)
    reserved = tdm.read_page(config, table["id"], True, where, 1).get("records", 0)
    return available + reserved, reserved


def send_update(config, table, where, sets):
    """Send the update and return the answer of TDM3."""
    body = tdm.addressing(config, table["name"])
    body["update-row-requests"] = [
        {
            "search-row-parameters-set": [
                {
                    "name-column": c,
                    "search-criterion": "Equals",
                    "search-value": v,
                    "caseSensitive": True,
                }
                for c, v in where
            ],
            "record-with-data-for-update": dict(sets),
        }
    ]
    responses = tdm.call(config, "/api/tdm/rest/update-records", body=body)
    response = responses[0] if responses else {}
    if response.get("type") != "SUCCESS":
        tdm.fail("rejected", response.get("content") or "TDM3 refused the update.")
    return response.get("content") or ""


def main():
    """Update the rows and print the result."""
    args = parse_arguments()
    where = tdm.parse_filters(args.where)
    sets = tdm.parse_filters(args.sets)
    if not where:
        tdm.fail("bad_argument", "At least one --where Column=value is required.")
    if not sets:
        tdm.fail("bad_argument", "At least one --set Column=value is required.")
    set_names = [c for c, _ in sets]
    if len(set(set_names)) != len(set_names):
        tdm.fail("bad_argument", "A column is given twice in --set.")

    config = tdm.load_config(tdm.CONFIG_FULL)
    table = tdm.find_table(config, args.table)

    header = tdm.header_of(tdm.read_page(config, table["id"], False, [], 1))
    for column, _ in where:
        tdm.check_column(column, header, "where")
    for column in set_names:
        tdm.check_column(column, header, "set")
    tdm.require_confirmed_values(where + sets, args.confirm_invisible)

    matched, reserved = count_matches(config, table, where)
    base = {
        "table": table,
        "matched": matched,
        "matched_reserved": reserved,
        "warnings": tdm.invisible_warnings(where + sets),
    }
    if matched == 0:
        tdm.succeed(**base, updated=0, no_row_matched=True, message=None)
    if matched > 1 and not args.all:
        tdm.fail(
            "multiple_matches",
            f"{matched} rows match. Nothing was changed. "
            "Narrow --where or add --all.",
            matched=matched,
            matched_reserved=reserved,
        )

    content = send_update(config, table, where, sets)
    found = re.search(r"\d+", content)
    tdm.succeed(
        **base,
        updated=int(found.group()) if found else None,
        no_row_matched=False,
        set=dict(sets),
        message=content,
    )


if __name__ == "__main__":
    tdm.use_utf8_output()
    main()
