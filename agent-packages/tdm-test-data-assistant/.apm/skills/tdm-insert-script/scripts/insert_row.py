"""Insert one row into a TDM3 table of the current system.

Prints one JSON object on stdout (the output rules are in tdm_common).

  insert_row.py --table Agents --value Column=value [--value ...] [--new]
                [--confirm-invisible]

  --new                Create the table when it does not exist. Without it the table
                       must exist, because TDM3 creates a table for any unknown
                       title, and a mistyped title would create a new table. With it
                       the table must not exist.
  --confirm-invisible  The user confirmed values that hold invisible characters
                       (TAB, a line break, a non-breaking space). TDM3 would store
                       them, so without the flag the script refuses.

Success:
  {"ok": true, "table": {...}, "created": false,
   "inserted": {"agent_code": "A100"}, "message": "<TDM3 answer>"}

Other error codes: table_not_found, table_exists, column_not_found,
invisible_characters, rejected, bad_argument.
"""

import importlib
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # keep __pycache__ out of the skill folder
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tdm-common" / "scripts"))
tdm = importlib.import_module("tdm_common")


def parse_arguments():
    """Return the parsed command line."""
    parser = tdm.new_parser(__doc__)
    tdm.add_table(parser)
    tdm.add_pairs(parser, "--value", "Column=value; repeat for more columns")
    tdm.add_switch(parser, "--new", "create the table when it does not exist")
    tdm.add_switch(parser, "--confirm-invisible", "invisible characters are confirmed")
    tdm.add_print_arguments(parser)
    return parser.parse_args()


def print_request(args, pairs):
    """Print the insert request instead of sending it."""
    config = tdm.load_config(tdm.CONFIG_FULL, optional=tdm.placeholders_on())
    body = tdm.addressing(config, args.table)
    body["insert-records"] = [{c: tdm.param(c, v) for c, v in pairs}]
    notes = ["TDM3 creates the table when no table has this title."]
    path = "/api/tdm/rest/insert-records"
    tdm.show_request(config, "POST", path, "Insert a row", body=body, notes=notes)


def main():
    """Insert the row and print the result."""
    args = parse_arguments()
    pairs = tdm.parse_filters(args.value)
    if not pairs:
        tdm.fail("bad_argument", "At least one --value Column=value is required.")
    names = [c for c, _ in pairs]
    if len(set(names)) != len(names):
        tdm.fail("bad_argument", "A column is given twice.")

    if tdm.configure_print(args):
        print_request(args, pairs)

    config = tdm.load_config(tdm.CONFIG_FULL)
    table, catalog = tdm.lookup_table(config, args.table)
    if table is None and not args.new:
        tdm.fail(
            "table_not_found",
            f"The current system has no table '{args.table}'. Pass --new to create it.",
            tables=tdm.list_of_tables(catalog),
        )
    if table is not None and args.new:
        tdm.fail(
            "table_exists",
            f"The table '{args.table}' already exists. Run without --new.",
            table=table,
        )

    if table is not None:
        header = tdm.header_of(tdm.read_page(config, table["id"], False, [], 1))
        for column in names:
            tdm.check_column(column, header, "insert")
    tdm.require_confirmed_values(pairs, args.confirm_invisible)

    body = tdm.addressing(config, args.table)
    body["insert-records"] = [dict(pairs)]
    response = tdm.call(config, "/api/tdm/rest/insert-records", body=body)
    if response.get("type") != "SUCCESS":
        tdm.fail("rejected", response.get("content") or "TDM3 refused the insert.")
    if table is None:
        table = tdm.lookup_table(config, args.table)[0]
    table = table or {"name": args.table, "id": None}
    tdm.succeed(
        table=table,
        created=args.new,
        inserted=dict(pairs),
        message=response.get("content"),
    )


if __name__ == "__main__":
    tdm.use_utf8_output()
    main()
