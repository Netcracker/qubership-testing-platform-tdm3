"""Read the rows of a TDM3 table that match Column=value filters.

Finds the database name of the table in the catalog of the current system, reads
it with POST /api/tdm/table, and prints one JSON object on stdout (the output
rules are in tdm_common). Nothing is reserved or changed.

  find_rows.py --table Agents [--return a,b] [--filter Column=value ...]
               [--occupied]

Success:
  {"ok": true, "table": {"name": "Agents", "id": "TDM_..."}, "occupied": false,
   "records": 12, "shown": 12, "truncated": false, "columns": ["agent_code", ...],
   "rows": [{"row_id": "<uuid>", "values": ["A001", ...]}], "warnings": []}

Other error codes: column_not_found (with "columns" and, when one column differs
only in case, "suggestion"), bad_argument.
"""

import importlib
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # keep __pycache__ out of the skill folder
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tdm-common" / "scripts"))
tdm = importlib.import_module("tdm_common")

PAGE_SIZE = 50
FIRST_COLUMNS = ["OCCUPIED_BY", "OCCUPIED_DATE"]


def parse_arguments():
    """Return the parsed command line."""
    parser = tdm.new_parser(__doc__)
    tdm.add_table(parser)
    parser.add_argument(
        "--return",
        dest="ret",
        help="comma-separated columns to show; default: every column",
    )
    tdm.add_pairs(parser, "--filter", "Column=value; repeat to combine with AND")
    tdm.add_switch(parser, "--occupied", "read the occupied rows, not the free ones")
    tdm.add_print_arguments(parser)
    return parser.parse_args()


def check_columns(config, table, args, filters, wanted):
    """Fail on a column that the table does not have; return nothing."""
    # A read with no filters and one row gives the header. Checking the names first
    # avoids the raw HTTP 500 that TDM3 returns for a wrong-case column name.
    header = tdm.header_of(tdm.read_page(config, table["id"], args.occupied, [], 1))
    for column, _ in filters:
        tdm.check_column(column, header, "filter")
    for column in wanted or []:
        tdm.check_column(column, header, "return")


def shown_columns(header, wanted):
    """Return the columns to show, with the occupation columns first."""
    body_columns = wanted if wanted else header
    first = [c for c in FIRST_COLUMNS if c in header]
    return first + [c for c in body_columns if c not in FIRST_COLUMNS]


def cell_value(cell):
    """Return the text of a grid cell without the padding of a CHAR column."""
    value = (cell.get("contentModel") or {}).get("value")
    return value.rstrip(" ") if isinstance(value, str) else value


def print_request(args, filters):
    """Print the paged-read request instead of sending it."""
    config = tdm.load_config(optional=tdm.placeholders_on())
    table_id = tdm.param("tableName", None)
    if not tdm.placeholders_on():
        table_id = tdm.find_table(config, args.table)["id"]
    notes = ["tableName is the database name of the table, from GET /api/tdm/tables/catalog."]
    if args.ret:
        notes.append("The response lists every column; use " + args.ret + ".")
    body = tdm.page_body(table_id, args.occupied, filters, PAGE_SIZE)
    tdm.show_request(config, "POST", "/api/tdm/table", "Read rows", body=body, notes=notes)


def main():
    """Read the rows and print them."""
    args = parse_arguments()
    filters = tdm.parse_filters(args.filter)
    wanted = args.ret.split(",") if args.ret else None
    if wanted is not None and not all(wanted):
        tdm.fail("bad_argument", "--return has an empty column name.")

    if tdm.configure_print(args):
        print_request(args, filters)

    config = tdm.load_config()
    table = tdm.find_table(config, args.table)
    check_columns(config, table, args, filters, wanted)

    page = tdm.read_page(config, table["id"], args.occupied, filters, PAGE_SIZE)
    header = tdm.header_of(page)
    shown = shown_columns(header, wanted)
    index = {name: i for i, name in enumerate(header)}

    rows = []
    for row in page["data"]["body"]["rows"]:
        cells = row["columns"][1:]
        values = [cell_value(cells[index[name]]) for name in shown]
        rows.append({"row_id": row["id"], "values": values})

    records = page.get("records", len(rows))
    tdm.succeed(
        table=table,
        occupied=args.occupied,
        records=records,
        shown=len(rows),
        truncated=records > len(rows),
        columns=shown,
        rows=rows,
        warnings=tdm.invisible_warnings(filters),
    )


if __name__ == "__main__":
    tdm.use_utf8_output()
    main()
