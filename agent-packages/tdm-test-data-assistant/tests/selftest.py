"""Run the scripts of the tdm-test-data-assistant skills against a running TDM3.

The scripts are the ones in .apm/skills/*/scripts. The run creates a table named
ZZ_selftest_<timestamp> in the current system of .tdm-assistant/config.json
(searched from the working directory upwards), exercises every script and its
error codes on that table, and drops the table at the end, also after a failure.
It touches no other table. The create-env API behind tdm-manage-dynamic-environment is
exercised on an environment named ZZ_selftest_env_<timestamp> in the project of the
config, and that environment is deleted at the end too. The tests do not cover the
agent: how it parses a command or words a reply is checked by hand.

  python tests/selftest.py [--allow-remote]

The run refuses a TDM3 server that is not on localhost, unless --allow-remote is
given. The exit code is 0 when every check passes and 1 otherwise. Python 3.9+,
standard library only.
"""

# The selftest keeps every scenario in one runnable file.
# pylint: disable=too-many-lines

import argparse
import getpass
import importlib
import json
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True  # keep __pycache__ out of the repository

SKILLS = Path(__file__).resolve().parents[1] / ".apm" / "skills"
sys.path.insert(0, str(SKILLS / "tdm-common" / "scripts"))
tdm = importlib.import_module("tdm_common")

PREFIX = "ZZ_selftest_"
ENV_PREFIX = PREFIX + "env_"
ENV_PATH = "/api/tdm/rest/create-env"
HTTP_CONNECTION = {"type": "HTTP", "parameters": {"url": "http://example.com"}}
DB_CONNECTION = {
    "type": "db",
    "name": "ignored",
    "parameters": {
        "jdbc_url": "jdbc:postgresql://h:5432/d",
        "db_login": "u",
        "db_password": "secret1",
        "db_type": "postgresql",
        "author": "me",
        "basic_auth": "zzz",
    },
}
LOCAL_HOSTS = ("localhost", "127.0.0.1", "[::1]")
TAB = chr(9)
SCRIPTS = {
    "list": ("tdm-list-tables-script", "list_tables.py"),
    "find": ("tdm-find-script", "find_rows.py"),
    "reserve": ("tdm-reserve-script", "reserve_rows.py"),
    "release": ("tdm-release-script", "release_rows.py"),
    "insert": ("tdm-insert-script", "insert_row.py"),
    "update": ("tdm-update-script", "update_rows.py"),
}

results: list[tuple[str, bool]] = []


def script(name):
    """Return the path of the script with this short name."""
    skill, file = SCRIPTS[name]
    return str(SKILLS / skill / "scripts" / file)


def run(name, *args, cwd=None):
    """Run a script and return (exit code, decoded JSON or None)."""
    done = subprocess.run(
        [sys.executable, script(name), *args],
        capture_output=True,
        check=False,
        cwd=cwd,
    )
    try:
        return done.returncode, json.loads(done.stdout.decode("utf-8"))
    except ValueError:
        return done.returncode, None


def check(label, condition, detail=""):
    """Record and print one check."""
    results.append((label, bool(condition)))
    mark = "PASS  " if condition else "FAIL  "
    print(mark + label + ("" if condition else f"   {detail}"))


def shorten(out):
    """Return the JSON of a script result, cut for a failure message."""
    return json.dumps(out, ensure_ascii=False)[:300]


def expect_error(label, name, code, *args, **fields):
    """Check that a script exits with 1 and the given error code and fields."""
    status, out = run(name, *args)
    good = status == 1 and out and out.get("ok") is False
    good = good and out.get("error") == code
    good = good and all(out.get(k) == v for k, v in fields.items())
    check(label, good, shorten(out))
    return out


def expect_ok(label, name, *args, **fields):
    """Check that a script exits with 0 and ok true, and that the fields hold."""
    status, out = run(name, *args)
    good = status == 0 and out and out.get("ok") is True
    good = good and all(out.get(k) == v for k, v in fields.items())
    check(label, good, shorten(out))
    return out


def records(table, *args):
    """Return the number of rows that find reports, or None."""
    status, out = run("find", "--table", table, "--return", "code", *args)
    return out["records"] if status == 0 and out else None


def drop_selftest_tables(config):
    """Drop the tables of this run or an earlier one; only the prefix qualifies."""
    _, catalog = tdm.lookup_table(config, PREFIX)
    for entry in tdm.list_of_tables(catalog):
        if entry["name"].startswith(PREFIX):
            print(f"      dropping {entry['name']}")
            tdm.call(
                config,
                "/api/tdm/table",
                query={"tableName": entry["id"]},
                method="DELETE",
            )


def insert_scenarios(base):
    """Check the insert script."""
    print("\n-- insert")
    expect_error(
        "insert without --new into an unknown table",
        "insert",
        "table_not_found",
        *base,
        "--value",
        "code=P1",
    )
    out = expect_ok(
        "insert --new creates the table",
        "insert",
        *base,
        "--value",
        "code=P1",
        "--value",
        "grp=A",
        "--value",
        "note=first",
        "--new",
        created=True,
    )
    check(
        "insert --new returns the table name and id",
        out and out["table"]["id"].startswith("TDM_"),
    )
    expect_error(
        "insert --new into an existing table",
        "insert",
        "table_exists",
        *base,
        "--value",
        "code=P9",
        "--new",
    )
    for code, group, note in (("P2", "A", "second"), ("P3", "B", "third")):
        expect_ok(
            f"insert the row {code}",
            "insert",
            *base,
            "--value",
            f"code={code}",
            "--value",
            f"grp={group}",
            "--value",
            f"note={note}",
        )
    out = expect_error(
        "insert with a wrong-case column",
        "insert",
        "column_not_found",
        *base,
        "--value",
        "Code=P4",
    )
    check(
        "the wrong-case column comes with a suggestion",
        out and out.get("suggestion") == "code",
    )
    expect_error(
        "insert with a TAB in a value needs a confirmation",
        "insert",
        "invisible_characters",
        *base,
        "--value",
        f"code=P5{TAB}x",
    )
    expect_error("insert with no values", "insert", "bad_argument", *base)
    expect_ok(
        "insert a TAB value once confirmed",
        "insert",
        *base,
        "--value",
        f"code=P5{TAB}x",
        "--value",
        "grp=C",
        "--confirm-invisible",
    )


def find_scenarios(base, table):
    """Check the find script."""
    print("\n-- find")
    check("find counts all four rows", records(table) == 4)
    check("find filters by an exact value", records(table, "--filter", "grp=A") == 2)
    check(
        "Equals is case-sensitive (behavior pin)",
        records(table, "--filter", "code=p1") == 0,
    )
    status, out = run("find", *base, "--return", "note,code", "--filter", "code=P1")
    good = status == 0 and out["columns"] == ["note", "code"]
    good = good and out["rows"][0]["values"] == ["first", "P1"]
    check("find returns the columns asked for, in order", good, shorten(out))
    check(
        "find returns the row id",
        status == 0 and len(out["rows"][0]["row_id"]) == 36,
    )
    out = expect_error(
        "find with a wrong-case filter column",
        "find",
        "column_not_found",
        *base,
        "--filter",
        "Grp=A",
    )
    check(
        "the wrong-case filter column comes with a suggestion",
        out and out.get("suggestion") == "grp",
    )
    expect_error(
        "find with an unknown table",
        "find",
        "table_not_found",
        "--table",
        PREFIX + "none",
    )
    status, out = run("find", *base, "--filter", f"code=a{TAB}b")
    check(
        "find warns about a TAB in a filter value",
        status == 0 and "TAB" in "".join(out["warnings"]),
    )
    check("find shows no reserved row yet", records(table, "--occupied") == 0)


def update_scenarios(base):
    """Check the update script on available rows."""
    print("\n-- update")
    expect_ok(
        "update with no match sends nothing",
        "update",
        *base,
        "--where",
        "code=none",
        "--set",
        "note=x",
        no_row_matched=True,
        updated=0,
    )
    expect_error(
        "update of several rows needs --all",
        "update",
        "multiple_matches",
        *base,
        "--where",
        "grp=A",
        "--set",
        "note=x",
        matched=2,
    )
    expect_error(
        "update with a wrong-case column in --set",
        "update",
        "column_not_found",
        *base,
        "--where",
        "code=P1",
        "--set",
        "Note=x",
    )
    expect_error(
        "update with no --where",
        "update",
        "bad_argument",
        *base,
        "--set",
        "note=x",
    )
    expect_ok(
        "update one row",
        "update",
        *base,
        "--where",
        "code=P1",
        "--set",
        "note=changed",
        updated=1,
    )
    expect_ok(
        "update several rows with --all",
        "update",
        *base,
        "--where",
        "grp=A",
        "--set",
        "note=bulk",
        "--all",
        updated=2,
    )
    status, out = run("find", *base, "--return", "note", "--filter", "grp=A")
    check(
        "the updated values are stored",
        status == 0 and [r["values"][0] for r in out["rows"]] == ["bulk"] * 2,
    )
    expect_error(
        "update to a TAB value needs a confirmation",
        "update",
        "invisible_characters",
        *base,
        "--where",
        "code=P3",
        "--set",
        f"note=a{TAB}b",
    )


def reserve_scenarios(base, table):
    """Check the reserve script; return the code it reserved first."""
    user = getpass.getuser()
    print("\n-- reserve")
    expect_error(
        "reserve with no filter",
        "reserve",
        "filter_required",
        *base,
        "--return",
        "code",
    )
    expect_error(
        "reserve with a wrong-case return column",
        "reserve",
        "column_not_found",
        *base,
        "--return",
        "Code",
        "--filter",
        "grp=A",
    )
    out = expect_ok(
        "reserve one row",
        "reserve",
        *base,
        "--return",
        "code",
        "--filter",
        "grp=A",
        count=1,
    )
    check(
        "the reserved value is a row of the group",
        out and out["reserved"][0] in ("P1", "P2"),
    )
    first = out["reserved"][0]
    check(
        "reserve records the operating-system user",
        out and out["occupied_by"] == user,
        f"{out and out.get('occupied_by')!r} != {user!r}",
    )
    status, out = run("find", *base, "--occupied", "--return", "code")
    check(
        "the reserved row shows the user, unchanged",
        status == 0 and out["rows"][0]["values"][0] == user,
        shorten(out),
    )
    check(
        "OCCUPIED_BY and OCCUPIED_DATE come first",
        status == 0 and out["columns"][:2] == ["OCCUPIED_BY", "OCCUPIED_DATE"],
    )
    expect_ok(
        "reserve every remaining match with --all",
        "reserve",
        *base,
        "--return",
        "code",
        "--filter",
        "grp=A",
        "--all",
        count=1,
    )
    expect_ok(
        "reserve with --all when nothing is left",
        "reserve",
        *base,
        "--return",
        "code",
        "--filter",
        "grp=A",
        "--all",
        count=0,
        no_row_matched=True,
    )
    out = expect_ok(
        "reserve any row with --any",
        "reserve",
        *base,
        "--return",
        "code",
        "--any",
        count=1,
    )
    check(
        "--any took a free row",
        out and out["reserved"][0] in ("P3", "P5" + TAB + "x"),
    )
    check("three rows are reserved", records(table, "--occupied") == 3)
    return first


def reserved_update_scenarios(base):
    """Check that the update script counts reserved rows."""
    print("\n-- update of a reserved row")
    expect_ok(
        "update counts a reserved row",
        "update",
        *base,
        "--where",
        "grp=A",
        "--set",
        "note=again",
        "--all",
        matched=2,
        matched_reserved=2,
        updated=2,
    )


def release_scenarios(base, table, first):
    """Check the release script; first is the code that reserve took first."""
    rest = "P2" if first == "P1" else "P1"
    column = ("--return", "code")
    print("\n-- release")
    expect_error("release with no filter", "release", "filter_required", *base)
    expect_error(
        "release with a filter and no --return",
        "release",
        "bad_argument",
        *base,
        "--filter",
        "grp=A",
    )
    expect_error(
        "release of several rows needs --all",
        "release",
        "multiple_matches",
        *base,
        *column,
        "--filter",
        "grp=A",
    )
    expect_ok(
        "release one row",
        "release",
        *base,
        *column,
        "--filter",
        f"code={first}",
        released=[first],
    )
    expect_ok(
        "release finds no match",
        "release",
        *base,
        *column,
        "--filter",
        f"code={first}",
        no_row_matched=True,
        count=0,
    )
    expect_ok(
        "release the rest of the group with --all",
        "release",
        *base,
        *column,
        "--filter",
        "grp=A",
        "--all",
        released=[rest],
    )
    check("one reserved row is left", records(table, "--occupied") == 1)
    expect_error(
        "release --whole-table takes no filter",
        "release",
        "bad_argument",
        *base,
        "--whole-table",
        "--filter",
        "grp=A",
    )
    expect_ok("release --whole-table", "release", *base, "--whole-table")
    check("no reserved row is left", records(table, "--occupied") == 0)


def list_scenarios(table):
    """Check the list script."""
    print("\n-- list")
    status, out = run("list")
    check(
        "list shows the table of the current system",
        status == 0 and any(t["name"] == table for t in out["tables"]),
    )
    status, out = run("list", "--whole-project")
    check(
        "list --whole-project is not smaller than the system list",
        status == 0 and any(t["name"] == table for t in out["tables"]),
    )


def check_printed(label, args, needles, forbidden=(), cwd=None):
    """Check that a script prints a request that holds the needles and not the forbidden."""
    status, out = run(*args, cwd=cwd)
    shown = (out or {}).get("text", "")
    good = status == 0 and out is not None and out.get("printed") is True
    good = good and all(needle in shown for needle in needles)
    good = good and not any(item in shown for item in forbidden)
    check(label, good, shorten(out))


def print_scenarios(base, table):
    """Check that the scripts show the requests and send nothing."""
    print("\n-- print the request")
    config = tdm.load_config(tdm.CONFIG_FULL)
    group = ["--filter", "grp=A"]
    reserve = ["reserve", *base, "--return", "code", *group]
    real = [config["TDM3_BASE_URL"], config["PROJECT_NAME"]]
    holders = ["<TDM3_BASE_URL>", "<projectName>", "<grp>"]
    check_printed(
        "reserve shows a curl request with placeholders",
        [*reserve, "--print-request", "curl"],
        ["curl -X POST", "occupiedBy=<occupiedBy>", *holders],
        forbidden=real,
    )
    check_printed(
        "reserve shows a Bruno request with placeholders",
        [*reserve, "--print-request", "bruno"],
        ["meta {", "post {", "{{TDM3_BASE_URL}}", "{{grp}}"],
        forbidden=real,
    )
    check_printed(
        "reserve shows real values with --values",
        [*reserve, "--print-request", "curl", "--values"],
        real,
        forbidden=holders,
    )
    check_printed(
        "insert shows its row as placeholders",
        ["insert", *base, "--value", "code=P7", "--print-request", "curl"],
        ["insert-records", "<code>"],
        forbidden=["P7"],
    )
    check_printed(
        "update shows the new value as its own placeholder",
        ["update", *base, "--where", "code=P1", "--set", "note=x", "--print-request", "curl"],
        ["update-records", "<code>", "<note_new>"],
    )
    release = ["release", *base, "--return", "code", *group]
    check_printed(
        "release shows one row",
        [*release, "--print-request", "curl"],
        ["release-records", "<grp>"],
    )
    check_printed(
        "release --all shows the release by ids",
        [*release, "--all", "--print-request", "curl"],
        ["PUT", "tableName=<tableName>", "<ROW_ID>"],
    )
    check_printed(
        "release --whole-table shows the bulk request",
        ["release", *base, "--whole-table", "--print-request", "curl"],
        ["release-records/bulk"],
    )
    check_printed(
        "find shows the paged read",
        ["find", *base, *group, "--print-request", "curl"],
        ["/api/tdm/table", "<tableName>", "<grp>"],
    )
    with tempfile.TemporaryDirectory() as folder:
        check_printed(
            "placeholders need no config file",
            ["insert", *base, "--value", "code=P7", "--print-request", "curl"],
            ["<projectName>", "<code>"],
            cwd=folder,
        )
    check(
        "printing sent nothing",
        records(table) == 4 and records(table, "--occupied") == 0,
    )


def table_scenarios(table):
    """Run the scenarios of the scripts that use the temporary table."""
    base = ["--table", table]
    insert_scenarios(base)
    find_scenarios(base, table)
    print_scenarios(base, table)
    update_scenarios(base)
    first = reserve_scenarios(base, table)
    reserved_update_scenarios(base)
    release_scenarios(base, table, first)
    list_scenarios(table)


def env_request(config, method, query=None, body=None):
    """Send a create-env request and return (HTTP status, decoded JSON or None)."""
    url = config["TDM3_BASE_URL"] + ENV_PATH
    if query:
        url += "?" + urllib.parse.urlencode(query)
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Content-Type": "application/json"} if data else {}
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=tdm.TIMEOUT_SECONDS) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as exc:
        status, raw = exc.code, exc.read()
    try:
        return status, json.loads(raw.decode("utf-8"))
    except ValueError:
        return status, None


def expect_env(label, config, outcome, query=None, body=None, **fields):
    """Check (method, status) and the top-level JSON fields of a create-env request."""
    method, status = outcome
    got, out = env_request(config, method, query, body)
    good = got == status and isinstance(out, dict)
    good = good and all(out.get(k) == v for k, v in fields.items())
    check(label, good, f"HTTP {got} {shorten(out)}")
    return out


def env_body(base, system=None, connections=None, **more):
    """Return a create-env request body for the environment in base."""
    body = dict(base)
    if system:
        body["systemName"] = system
    if connections is not None:
        body["connections"] = connections
    return {**body, **more}


def env_systems(config, base, system=None):
    """Return {system: {connection type: parameters}} as GET reports it, or None."""
    query = dict(base)
    if system:
        query["systemName"] = system
    status, out = env_request(config, "GET", query)
    if status != 200 or not isinstance(out, dict):
        return None
    systems = {}
    for item in out["systems"]:
        systems[item["systemName"]] = {c["type"]: c["parameters"] for c in item["connections"]}
    return systems


def drop_selftest_environments(config, base):
    """Delete the environment of this run and the ones that earlier runs left."""
    listing = tdm.call(config, f"/api/tdm/projects/{config['PROJECT_ID']}/environments/lazy")
    names = {item["name"] for item in listing or []}
    names = {name for name in names if name.startswith(ENV_PREFIX)}
    for name in sorted(names | {base["envName"]}):
        print(f"      deleting environment {name}")
        env_request(config, "DELETE", body={**base, "envName": name})


def env_create_scenarios(config, base):
    """Check POST and the GET that reads it back."""
    print("\n-- create-env: create")
    expect_env(
        "POST creates an environment with a system",
        config,
        ("POST", 200),
        body=env_body(base, "s1", [HTTP_CONNECTION]),
        type="SUCCESS",
    )
    systems = env_systems(config, base)
    check(
        "GET shows the system and its HTTP connection",
        systems == {"s1": {"HTTP": HTTP_CONNECTION["parameters"]}},
        str(systems),
    )
    old_form = env_body(base, "s2", connection={"name": "HTTP", **HTTP_CONNECTION})
    git_form = env_body(base, "s2", [{"type": "GIT", "parameters": {"url": "x"}}])
    refused = {
        "POST of an existing system": env_body(base, "s1", [HTTP_CONNECTION]),
        "POST with the old connection object": old_form,
        "POST with a GIT connection": git_form,
        "POST with two connections of one type": env_body(
            base, "s2", [HTTP_CONNECTION, {"type": "http", "parameters": {"url": "b"}}]
        ),
    }
    for label, body in refused.items():
        expect_env(
            label + " is refused with a ResponseMessage",
            config,
            ("POST", 400),
            body=body,
            type="ERROR",
        )
    listed = set(env_systems(config, base))
    check("refused requests created no system", listed == {"s1"}, str(listed))


def env_secret_scenarios(config, base):
    """Check the nested request form, the type case, and the masking of GET."""
    print("\n-- create-env: secrets")
    expect_env(
        "POST accepts the nested form and a lowercase type",
        config,
        ("POST", 200),
        body={"environment": env_body(base, "s2", [DB_CONNECTION])},
        type="SUCCESS",
    )
    one = env_systems(config, base, "s2")
    params = (one or {}).get("s2", {}).get("DB", {})
    check("GET with systemName lists that system only", set(one or {}) == {"s2"})
    check("GET reports the lowercase type as DB", bool(params), str(one))
    check(
        "GET masks the secret parameters",
        params.get("db_password") == "***" and params.get("basic_auth") == "***",
        str(params),
    )
    check(
        "GET leaves the other parameters, also author, which only contains auth",
        params.get("db_login") == "u" and params.get("author") == "me",
        str(params),
    )


def env_update_scenarios(config, base):
    """Check the upsert and the rename of PUT."""
    print("\n-- create-env: update")
    expect_env(
        "PUT adds a connection of another type",
        config,
        ("PUT", 200),
        body=env_body(base, "s2", [HTTP_CONNECTION]),
        type="SUCCESS",
    )
    kept = env_systems(config, base, "s2")
    check(
        "PUT keeps the connection it was not sent",
        set((kept or {}).get("s2", {})) == {"DB", "HTTP"},
        str(kept),
    )
    expect_env(
        "PUT of a rename without a connection is refused",
        config,
        ("PUT", 400),
        body=env_body(base, "s2", newSystemName="s3"),
        type="ERROR",
    )
    out = expect_env(
        "PUT of an unknown system is a 404 ResponseMessage",
        config,
        ("PUT", 404),
        body=env_body(base, "nope", [HTTP_CONNECTION]),
        type="ERROR",
    )
    where = f"project [{base['projectName']}], environment [{base['envName']}]"
    expected = f"System [nope] not found in {where}."
    check(
        "the message names project and environment under their own labels",
        (out or {}).get("content") == expected,
        shorten(out),
    )
    expect_env(
        "PUT renames a system together with a connection",
        config,
        ("PUT", 200),
        body=env_body(base, "s2", [HTTP_CONNECTION], newSystemName="s3"),
        type="SUCCESS",
    )
    listed = set(env_systems(config, base))
    check("the renamed system is listed", listed == {"s1", "s3"}, str(listed))


def env_read_error_scenarios(config, base):
    """Check the errors of GET."""
    print("\n-- create-env: read errors")
    known = dict(base)
    cases = [
        ("GET of an unknown system", 404, {**known, "systemName": "nope"}),
        ("GET of an unknown environment", 404, {**known, "envName": "NoSuchEnv"}),
        ("GET of an unknown project", 400, {**known, "projectName": "NoSuchProject"}),
        ("GET without envName", 400, {"projectName": known["projectName"]}),
        ("GET without projectName", 400, {"envName": known["envName"]}),
        ("GET without parameters", 400, {}),
    ]
    for label, status, query in cases:
        expect_env(
            label + " is a ResponseMessage",
            config,
            ("GET", status),
            query=query,
            type="ERROR",
        )
    out = expect_env(
        "GET without parameters names the required ones",
        config,
        ("GET", 400),
        query={},
        content="projectName and envName are required.",
    )
    traced = "trace" in (out or {})
    check("a GET error carries no stack trace", not traced, shorten(out))


def env_delete_scenarios(config, base):
    """Check DELETE of a system and of the environment."""
    print("\n-- create-env: delete")
    expect_env(
        "DELETE with systemDeleteName removes that system",
        config,
        ("DELETE", 200),
        body=env_body(base, systemDeleteName="s3"),
        type="SUCCESS",
    )
    check("only the other system is left", set(env_systems(config, base)) == {"s1"})
    expect_env(
        "DELETE removes the environment",
        config,
        ("DELETE", 200),
        body=env_body(base),
        type="SUCCESS",
    )
    expect_env(
        "DELETE of the same environment again is a 404 ResponseMessage",
        config,
        ("DELETE", 404),
        body=env_body(base),
        type="ERROR",
    )
    expect_env(
        "GET of the deleted environment is a 404 ResponseMessage",
        config,
        ("GET", 404),
        query=base,
        type="ERROR",
    )


def env_scenarios(config, env):
    """Run the scenarios of the create-env API on a temporary environment."""
    base = {"projectName": config["PROJECT_NAME"], "envName": env}
    env_create_scenarios(config, base)
    env_secret_scenarios(config, base)
    env_update_scenarios(config, base)
    env_read_error_scenarios(config, base)
    env_delete_scenarios(config, base)


def main():
    """Run all scenarios and return the exit code."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--allow-remote",
        action="store_true",
        help="allow a TDM3 server that is not on localhost",
    )
    args = parser.parse_args()

    config = tdm.load_config(tdm.CONFIG_FULL)
    host = urllib.parse.urlparse(config["TDM3_BASE_URL"]).netloc.rsplit(":", 1)[0]
    if host not in LOCAL_HOSTS and not args.allow_remote:
        url = config["TDM3_BASE_URL"]
        print(f"Refusing {url}: not localhost. Pass --allow-remote to override.")
        return 1

    stamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
    table = PREFIX + stamp
    env = ENV_PREFIX + stamp
    base = {"projectName": config["PROJECT_NAME"], "envName": env}
    system = config["SYSTEM_NAME_DEFAULT"]
    print(f"TDM3: {config['TDM3_BASE_URL']}  system: {system}  table: {table}")
    print(f"      project: {base['projectName']}  environment: {env}")
    try:
        drop_selftest_tables(config)
        drop_selftest_environments(config, base)
        table_scenarios(table)
        env_scenarios(config, env)
    finally:
        print("\n-- cleanup")
        drop_selftest_tables(config)
        drop_selftest_environments(config, base)
        tables = tdm.list_of_tables(tdm.lookup_table(config, PREFIX)[1])
        left = [t for t in tables if t["name"].startswith(PREFIX)]
        check("no selftest table is left", not left, str(left))
        status, _ = env_request(config, "GET", base)
        check("no selftest environment is left", status == 404, f"HTTP {status}")

    failed = [label for label, ok in results if not ok]
    print(f"\n{len(results) - len(failed)} of {len(results)} checks passed")
    for label in failed:
        print(f"  FAILED: {label}")
    return 1 if failed else 0


if __name__ == "__main__":
    tdm.use_utf8_output()
    sys.exit(main())
