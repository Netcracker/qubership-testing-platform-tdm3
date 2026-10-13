"""Reserve rows of a TDM3 table that match Column=value filters.

Finds the table in the catalog of the current system, checks the column names
against the header of the table, and calls POST /api/tdm/rest/occupy-records with
occupiedBy set to the login name of the operating-system account. Prints one JSON
object on stdout (the output rules are in tdm_common).

  reserve_rows.py --table SimCatalog --return sim [--filter Column=value ...]
                  [--all | --any]

  --all   reserve one entry per matching row counted before the request, up to 200
  --any   with no filter, allow the first available row of the table to be reserved

Success:
  {"ok": true, "table": {...}, "occupied_by": "Name", "requested": "one" | "all",
   "reserved": ["<value>", ...], "count": 1, "no_row_matched": false,
   "capped": false, "errors": [], "warnings": []}

"reserved" holds the value of the --return column of each reserved row.
"no_row_matched" is true when TDM3 had no available row left for the criteria.
Other error codes: column_not_found, filter_required, bad_argument.
"""

import argparse
import getpass
import importlib
import socket
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # keep __pycache__ out of the skill folder
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tdm-common" / "scripts"))
tdm = importlib.import_module("tdm_common")

NO_DATA_PREFIX = "No test data available"
MAX_ROWS = 200


def occupied_by():
    """Return the login name of the operating-system account, or the IP address."""
    try:
        name = getpass.getuser()
    except (ImportError, KeyError, OSError):
        name = ""
    if name:
        return name
    try:
        return socket.gethostbyname(socket.gethostname())
    except OSError:
        return tdm.fail(
            "bad_argument",
            "Cannot determine the user name or the IP address for occupiedBy.",
        )


def entry(filters, response_column):
    """Return one occupy-row-requests entry."""
    return {
        "search-row-parameters-set": [
            {
                "name-column": c,
                "search-criterion": "Equals",
                "search-value": v,
                "caseSensitive": False,
            }
            for c, v in filters
        ],
        "name-column-response": response_column,
    }


def occupy(config, table_title, user, entries):
    """Send the entries in one request and return the ResponseMessage list."""
    body = tdm.addressing(config, table_title)
    body["occupy-row-requests"] = entries
    return tdm.call(
        config, "/api/tdm/rest/occupy-records", query={"occupiedBy": user}, body=body
    )


def parse_arguments():
    """Return the parsed command line."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--table", required=True, help="table title")
    parser.add_argument(
        "--return",
        dest="ret",
        required=True,
        help="column whose value identifies a reserved row",
    )
    parser.add_argument(
        "--filter",
        action="append",
        default=[],
        help="Column=value; repeat to combine with AND",
    )
    parser.add_argument(
        "--all", action="store_true", help="reserve every available row that matches"
    )
    parser.add_argument("--any", action="store_true", help="allow an empty filter")
    return parser.parse_args()


def entries_wanted(config, table, args, filters):
    """Return (entries to send, whether the count was cut at the limit)."""
    if not args.all:
        return 1, False
    # The count only sizes the request. Rows can change before the request arrives,
    # so the result lists what was reserved, which may differ from the count.
    count = tdm.read_page(config, table["id"], False, filters, 1).get("records", 0)
    return min(count, MAX_ROWS), count > MAX_ROWS


def collect(responses):
    """Return (reserved values, other messages, whether TDM3 ran out of rows)."""
    reserved, errors, exhausted = [], [], False
    for response in responses:
        content = response.get("content")
        if response.get("type") == "SUCCESS":
            reserved.append(content)
        elif isinstance(content, str) and content.startswith(NO_DATA_PREFIX):
            exhausted = True
        else:
            errors.append(content)
    return reserved, errors, exhausted


def main():
    """Reserve the rows and print the result."""
    args = parse_arguments()
    filters = tdm.parse_filters(args.filter)
    if not filters and not args.any:
        tdm.fail(
            "filter_required",
            "No filter given. Pass --any to reserve the first available row.",
        )

    config = tdm.load_config(tdm.CONFIG_FULL)
    table = tdm.find_table(config, args.table)
    header = tdm.header_of(tdm.read_page(config, table["id"], False, [], 1))
    for column, _ in filters:
        tdm.check_column(column, header, "filter")
    tdm.check_column(args.ret, header, "return")

    user = occupied_by()
    wanted, capped = entries_wanted(config, table, args, filters)
    reserved, errors, exhausted = [], [], wanted == 0
    if wanted:
        entries = [entry(filters, args.ret)] * wanted
        reserved, errors, exhausted = collect(
            occupy(config, table["name"], user, entries)
        )

    tdm.succeed(
        table=table,
        occupied_by=user,
        requested="all" if args.all else "one",
        reserved=reserved,
        count=len(reserved),
        no_row_matched=exhausted and not reserved,
        capped=capped,
        errors=errors,
        warnings=tdm.invisible_warnings(filters),
    )


if __name__ == "__main__":
    tdm.use_utf8_output()
    main()
