"""Share the config, HTTP, and output code of the scripts of the skills.

A script imports this module after adding the folder that holds it to sys.path:

    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tdm-common" / "scripts"))
    tdm = importlib.import_module("tdm_common")

Every script prints exactly one JSON object on stdout, always UTF-8, and exits with
0 on success and 1 on any error. An error is
{"ok": false, "error": "<code>", "message": "<text>", ...}. The codes this module
raises are config_missing, config_incomplete, server_unreachable, http_error,
table_not_found, and column_not_found. Python 3.9+, standard library only.
"""

import argparse
import json
import sys
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import NoReturn

CONFIG_PATH = Path(".tdm-assistant") / "config.json"
TIMEOUT_SECONDS = 60
CONTROL_NAMES = {chr(9): "TAB", chr(10): "LINE FEED", chr(13): "CARRIAGE RETURN"}
CONFIG_FULL = (
    "TDM3_BASE_URL",
    "PROJECT_ID",
    "SYSTEM_ID_DEFAULT",
    "PROJECT_NAME",
    "ENV_NAME_DEFAULT",
    "SYSTEM_NAME_DEFAULT",
)


def fail(code, message, **extra) -> NoReturn:
    """Print an error object and exit with status 1."""
    error = {"ok": False, "error": code, "message": message, **extra}
    print(json.dumps(error, ensure_ascii=False))
    sys.exit(1)


def succeed(**fields) -> NoReturn:
    """Print a success object and exit with status 0."""
    print(json.dumps({"ok": True, **fields}, ensure_ascii=False))
    sys.exit(0)


def use_utf8_output():
    """Make stdout UTF-8 whatever the console code page is."""
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")


def find_config():
    """Return the path of config.json found from the working directory up, or None."""
    here = Path.cwd().resolve()
    for folder in [here, *here.parents]:
        candidate = folder / CONFIG_PATH
        if candidate.is_file():
            return candidate
    return None


def load_config(required=("TDM3_BASE_URL", "PROJECT_ID", "SYSTEM_ID_DEFAULT")):
    """Return the config, or fail when it is missing or lacks a required key.

    CONFIG_DIR holds the folder of the config file, the .tdm-assistant folder.
    """
    path = find_config()
    if path is None:
        fail(
            "config_missing",
            "No .tdm-assistant/config.json found. Select the context first.",
        )
    try:
        config = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        fail("config_incomplete", f"Cannot read {path}: {exc}")
    missing = [key for key in required if not config.get(key)]
    if missing:
        fail("config_incomplete", "Missing in config: " + ", ".join(missing))
    if config.get("TDM3_BASE_URL"):
        config["TDM3_BASE_URL"] = config["TDM3_BASE_URL"].rstrip("/")
    config["CONFIG_DIR"] = str(path.parent)
    return config


def call(config, path, query=None, body=None, method=None):
    """Send a request to TDM3 and return the decoded JSON; fail on any error."""
    base_url = config["TDM3_BASE_URL"]
    url = base_url + path + ("?" + urllib.parse.urlencode(query) if query else "")
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    headers = {"Content-Type": "application/json"} if data else {}
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            raw = response.read().decode("utf-8")
        return json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        snippet = exc.read().decode("utf-8", "replace")[:300]
        fail(
            "http_error",
            f"HTTP {exc.code} from {path}",
            status=exc.code,
            body=snippet,
        )
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        fail("server_unreachable", f"Cannot reach {base_url}: {exc}")
    except ValueError as exc:
        fail("http_error", f"The response of {path} is not JSON: {exc}")


def lookup_table(config, title):
    """Return ({"name", "id"} or None, catalog) for a table of the current system."""
    catalog = call(
        config,
        "/api/tdm/tables/catalog",
        query={
            "projectId": config["PROJECT_ID"],
            "systemId": config["SYSTEM_ID_DEFAULT"],
        },
    )
    matches = [t for t in catalog if t.get("tableTitle") == title]
    table = None
    if matches:
        table = {"name": matches[0]["tableTitle"], "id": matches[0]["tableName"]}
    return table, catalog


def list_of_tables(catalog):
    """Return the catalog as a list of {"name", "id"} entries."""
    return [{"name": t.get("tableTitle"), "id": t.get("tableName")} for t in catalog]


def find_table(config, title):
    """Return {"name", "id"} of a table of the current system, or fail."""
    table, catalog = lookup_table(config, title)
    if table is None:
        fail(
            "table_not_found",
            f"The current system has no table '{title}'.",
            tables=list_of_tables(catalog),
        )
    return table


def addressing(config, table_title):
    """Return the fields that address a table by names in atp-action requests."""
    return {
        "projectName": config["PROJECT_NAME"],
        "envName": config["ENV_NAME_DEFAULT"],
        "systemName": config["SYSTEM_NAME_DEFAULT"],
        "title-table": table_title,
    }


def equals_filter(column, value):
    """Return the paged-read filter that matches a column to a value exactly."""
    return {
        "name-column": column,
        "search-criterion": "Equals",
        "search-values": [value],
    }


def read_page(config, table_id, occupied, filters, limit):
    """Read one page of the table with Equals filters, given as (column, value)."""
    body = {
        "tableName": table_id,
        "offset": 0,
        "limit": limit,
        "occupied": occupied,
        "filters": [equals_filter(c, v) for c, v in filters],
    }
    return call(config, "/api/tdm/table", body=body)


def header_of(page):
    """Return the column names of a paged read, without the checkbox column."""
    columns = page["data"]["header"]["rows"][0]["columns"]
    return [c.get("value") for c in columns[1:]]


def check_column(name, header, what):
    """Fail unless name is exactly one of the header columns (case-sensitive)."""
    if name in header:
        return
    extra = {"columns": header}
    lower = [h for h in header if h.lower() == name.lower()]
    if len(lower) == 1:
        extra["suggestion"] = lower[0]
    fail(
        "column_not_found",
        f"The table has no column '{name}' ({what}). Column names are case-sensitive.",
        **extra,
    )


def parse_filters(items):
    """Split Column=value strings at the first '=' into (column, value) pairs."""
    filters = []
    for item in items:
        column, sep, value = item.partition("=")
        if not sep or not column:
            fail("bad_argument", f"Filter '{item}' is not Column=value.")
        filters.append((column, value))
    return filters


def new_parser(doc):
    """Return an argument parser described by the first line of doc."""
    return argparse.ArgumentParser(description=doc.splitlines()[0])


def add_table(parser):
    """Add the required --table argument."""
    parser.add_argument("--table", required=True, help="table title")


def add_pairs(parser, flag, help_text, dest=None):
    """Add a repeatable argument whose values are Column=value pairs."""
    parser.add_argument(flag, dest=dest, action="append", default=[], help=help_text)


def add_switch(parser, flag, help_text):
    """Add an argument that is true when given."""
    parser.add_argument(flag, action="store_true", help=help_text)


def search_parameters(pairs, case_sensitive):
    """Return the search-row-parameters-set for Equals conditions on (column, value)."""
    return [
        {
            "name-column": column,
            "search-criterion": "Equals",
            "search-value": value,
            "caseSensitive": case_sensitive,
        }
        for column, value in pairs
    ]


def invisible_names(value):
    """Return the names of the invisible characters in value (not plain spaces)."""
    names = []
    for char in value:
        if char == " ":
            continue
        if char.isspace() or unicodedata.category(char) in ("Cf", "Zs", "Zl", "Zp"):
            name = CONTROL_NAMES.get(char) or unicodedata.name(
                char,
                f"U+{ord(char):04X}",
            )
            if name not in names:
                names.append(name)
    return names


def invisible_warnings(pairs):
    """Return one warning per (column, value) pair that holds an invisible character."""
    warnings = []
    for column, value in pairs:
        names = invisible_names(value)
        if names:
            listed = ", ".join(names)
            note = f"The value of '{column}' contains: {listed}. It was sent as typed."
            warnings.append(note)
    return warnings


def require_confirmed_values(pairs, confirmed):
    """Fail unless the user confirmed the values with invisible characters."""
    found = []
    for column, value in pairs:
        names = invisible_names(value)
        if names:
            found.append({"column": column, "characters": names})
    if found and not confirmed:
        fail(
            "invisible_characters",
            "A value contains invisible characters that TDM3 would store. "
            "Ask the user to confirm the value, "
            "then run again with --confirm-invisible.",
            values=found,
        )
