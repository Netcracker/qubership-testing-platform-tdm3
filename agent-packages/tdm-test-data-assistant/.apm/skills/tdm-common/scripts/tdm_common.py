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


def load_config(required=("TDM3_BASE_URL", "PROJECT_ID", "SYSTEM_ID_DEFAULT"), optional=False):
    """Return the config, or fail when it is missing or lacks a required key.

    With optional=True a missing file gives an empty config, for the print mode that
    shows placeholders and needs no value from the file.

    CONFIG_DIR holds the folder of the config file, the .tdm-assistant folder.
    """
    path = find_config()
    if path is None and optional:
        return {}
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
        "projectName": param("projectName", config.get("PROJECT_NAME")),
        "envName": param("envName", config.get("ENV_NAME_DEFAULT")),
        "systemName": param("systemName", config.get("SYSTEM_NAME_DEFAULT")),
        "title-table": table_title,
    }


def equals_filter(column, value):
    """Return the paged-read filter that matches a column to a value exactly."""
    return {
        "name-column": column,
        "search-criterion": "Equals",
        "search-values": [param(column, value)],
    }


def page_body(table_id, occupied, filters, limit):
    """Return the body of the paged read with Equals filters on (column, value)."""
    return {
        "tableName": table_id,
        "offset": 0,
        "limit": limit,
        "occupied": occupied,
        "filters": [equals_filter(c, v) for c, v in filters],
    }


def read_page(config, table_id, occupied, filters, limit):
    """Read one page of the table with Equals filters, given as (column, value)."""
    body = page_body(table_id, occupied, filters, limit)
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
            "search-value": param(column, value),
            "caseSensitive": case_sensitive,
        }
        for column, value in pairs
    ]


PRINT = {"on": False, "format": "curl", "values": False}


def add_print_arguments(parser):
    """Add the arguments that show the request instead of sending it."""
    parser.add_argument(
        "--print-request",
        choices=("curl", "bruno"),
        help="show the request in this format, and send nothing",
    )
    add_switch(parser, "--values", "with --print-request: real values, no placeholders")


def configure_print(args):
    """Switch the print mode on or off from the arguments; return whether it is on."""
    PRINT["on"] = bool(args.print_request)
    PRINT["format"] = args.print_request or "curl"
    PRINT["values"] = args.values
    return PRINT["on"]


def placeholders_on():
    """Return whether the requests shown carry placeholders instead of values."""
    return PRINT["on"] and not PRINT["values"]


def placeholder(name):
    """Return the placeholder called name, in the format that is printed."""
    if PRINT["format"] == "bruno":
        return "{{" + name + "}}"
    return "<" + name + ">"


def param(name, value):
    """Return value, or the placeholder called name when placeholders are on."""
    return placeholder(name) if placeholders_on() else value


def query_text(query):
    """Return the query string; placeholders keep their brackets."""
    if not query:
        return ""
    pairs = [f"{k}={urllib.parse.quote(str(v), safe='<>{}')}" for k, v in query.items()]
    return "?" + "&".join(pairs)


def render_curl(method, url, body):
    """Return the request as a curl command."""
    lines = [f"curl -X {method} '{url}'"]
    if body is not None:
        text = json.dumps(body, ensure_ascii=False, indent=2).replace("'", "'\\''")
        lines.append("  -H 'Content-Type: application/json'")
        lines.append(f"  -d '{text}'")
    return " \\\n".join(lines)


def render_bruno(method, url, body, label):
    """Return the request as the text of a Bruno .bru file."""
    kind = "json" if body is not None else "none"
    parts = [
        "meta {\n  name: " + label + "\n  type: http\n  seq: 1\n}\n",
        method.lower() + " {\n  url: " + url + "\n  body: " + kind + "\n  auth: none\n}\n",
    ]
    if body is not None:
        dumped = json.dumps(body, ensure_ascii=False, indent=2)
        indented = "\n".join("  " + line for line in dumped.split("\n"))
        parts.append("headers {\n  Content-Type: application/json\n}\n")
        parts.append("body:json {\n" + indented + "\n}\n")
    return "\n".join(parts)


def show_request(config, method, path, label, **parts):
    """Print the request in the chosen format and exit; nothing is sent.

    parts may hold query (a dict), body (the JSON body), and notes (a list of strings).
    """
    body = parts.get("body")
    base = param("TDM3_BASE_URL", config.get("TDM3_BASE_URL", ""))
    url = base + path + query_text(parts.get("query"))
    if PRINT["format"] == "bruno":
        text = render_bruno(method, url, body, label)
    else:
        text = render_curl(method, url, body)
    succeed(
        printed=True,
        format=PRINT["format"],
        values=PRINT["values"],
        method=method,
        url=url,
        body=body,
        text=text,
        notes=list(parts.get("notes", ())),
    )


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
