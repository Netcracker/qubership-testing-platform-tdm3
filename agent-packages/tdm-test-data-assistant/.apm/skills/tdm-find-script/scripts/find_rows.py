#!/usr/bin/env python3
"""Read the rows of a TDM3 table of the current system that match Column=value filters.

Finds the database name of the table in the catalog of the current system, reads it with POST /api/tdm/table, and
prints one JSON object on stdout (the output rules are in tdm_common). Nothing is reserved or changed.

  find_rows.py --table Agents [--return a,b] [--filter Column=value ...] [--occupied]

Success:
  {"ok": true, "table": {"name": "Agents", "id": "TDM_..."}, "occupied": false,
   "records": 12, "shown": 12, "truncated": false, "columns": ["agent_code", ...],
   "rows": [{"row_id": "<uuid>", "values": ["A001", ...]}], "warnings": []}

Other error codes: column_not_found (with "columns" and, when one column differs only in case, "suggestion"),
bad_argument.
"""
import argparse
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # keep __pycache__ out of the skill folder
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tdm-common" / "scripts"))
import tdm_common as tdm  # noqa: E402

PAGE_SIZE = 50
FIRST_COLUMNS = ["OCCUPIED_BY", "OCCUPIED_DATE"]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--table", required=True, help="table title")
    parser.add_argument("--return", dest="ret", help="comma-separated columns to show; default: every column")
    parser.add_argument("--filter", action="append", default=[], help="Column=value; repeat to combine with AND")
    parser.add_argument("--occupied", action="store_true", help="read the occupied rows instead of the available ones")
    args = parser.parse_args()

    filters = tdm.parse_filters(args.filter)
    wanted = args.ret.split(",") if args.ret else None
    if wanted is not None and not all(wanted):
        tdm.fail("bad_argument", "--return has an empty column name.")

    config = tdm.load_config()
    table = tdm.find_table(config, args.table)

    # The first read has no filters and one row: only its header is used, to check the column names
    # before TDM3 meets them (a wrong-case name makes TDM3 answer with a raw HTTP 500).
    header = tdm.header_of(tdm.read_page(config, table["id"], args.occupied, [], 1))
    for column, _ in filters:
        tdm.check_column(column, header, "filter")
    for column in wanted or []:
        tdm.check_column(column, header, "return")

    page = tdm.read_page(config, table["id"], args.occupied, filters, PAGE_SIZE)
    header = tdm.header_of(page)
    body_columns = wanted if wanted else header
    shown = [c for c in FIRST_COLUMNS if c in header] + [c for c in body_columns if c not in FIRST_COLUMNS]
    index = {name: i for i, name in enumerate(header)}

    rows = []
    for row in page["data"]["body"]["rows"]:
        cells = row["columns"][1:]
        values = []
        for name in shown:
            value = (cells[index[name]].get("contentModel") or {}).get("value")
            values.append(value.rstrip(" ") if isinstance(value, str) else value)
        rows.append({"row_id": row["id"], "values": values})

    records = page.get("records", len(rows))
    tdm.succeed(table=table, occupied=args.occupied, records=records, shown=len(rows),
                truncated=records > len(rows), columns=shown, rows=rows,
                warnings=tdm.invisible_warnings(filters))


if __name__ == "__main__":
    tdm.use_utf8_output()
    main()
