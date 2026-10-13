"""Run the scripts of the tdm-test-data-assistant skills against a running TDM3.

The scripts are the ones in .apm/skills/*/scripts. The run creates a table named
ZZ_selftest_<timestamp> in the current system of .tdm-assistant/config.json
(searched from the working directory upwards), exercises every script and its
error codes on that table, and drops the table at the end, also after a failure.
It touches no other table. The generate script is run against a fake GitLab in this
process. The tests do not cover the agent: how it parses a command or words a
reply is checked by hand.

  python tests/selftest.py [--allow-remote]

The run refuses a TDM3 server that is not on localhost, unless --allow-remote is
given. The exit code is 0 when every check passes and 1 otherwise. Python 3.9+,
standard library only.
"""

import argparse
import getpass
import importlib
import json
import os
import subprocess
import sys
import tempfile
import threading
import urllib.parse
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.dont_write_bytecode = True  # keep __pycache__ out of the repository

SKILLS = Path(__file__).resolve().parents[1] / ".apm" / "skills"
sys.path.insert(0, str(SKILLS / "tdm-common" / "scripts"))
tdm = importlib.import_module("tdm_common")

PREFIX = "ZZ_selftest_"
LOCAL_HOSTS = ("localhost", "127.0.0.1", "[::1]")
TAB = chr(9)
SCRIPTS = {
    "list": ("tdm-list-tables-script", "list_tables.py"),
    "find": ("tdm-find-script", "find_rows.py"),
    "reserve": ("tdm-reserve-script", "reserve_rows.py"),
    "release": ("tdm-release-script", "release_rows.py"),
    "insert": ("tdm-insert-script", "insert_row.py"),
    "update": ("tdm-update-script", "update_rows.py"),
    "generate": ("tdm-generate-test-data", "generate_test_data.py"),
}

GITLAB_TOKEN = "tok-SECRET-123"
PIPELINE_VARIABLES = {
    "PIPELINE_RUNTIME_CONFIG": "environments/x.yaml",
    "ATP_TESTS_GIT_REPO_URL": "http://g/r.git",
    "ATP_TESTS_GIT_REPO_BRANCH": "main",
    "EXECUTION_TYPE": "scope",
    "EXECUTION_NAME": "regression",
    "ENABLE_JIRA_INTEGRATION": "false",
    "SEND_WEBEX_NOTIFICATION": "true",
}

results: list[tuple[str, bool]] = []


def script(name):
    """Return the path of the script with this short name."""
    skill, file = SCRIPTS[name]
    return str(SKILLS / skill / "scripts" / file)


def run(name, *args):
    """Run a script and return (exit code, decoded JSON or None)."""
    done = subprocess.run(
        [sys.executable, script(name), *args],
        capture_output=True,
        check=False,
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


def table_scenarios(table):
    """Run the scenarios of the scripts that use the temporary table."""
    base = ["--table", table]
    insert_scenarios(base)
    find_scenarios(base, table)
    update_scenarios(base)
    first = reserve_scenarios(base, table)
    reserved_update_scenarios(base)
    release_scenarios(base, table, first)
    list_scenarios(table)


class FakeGitLab(ThreadingHTTPServer):
    """Answer the GitLab requests of the generate script, and record them."""

    def __init__(self):
        super().__init__(("127.0.0.1", 0), FakeGitLabHandler)
        self.statuses = ["success"]
        self.jobs = []
        self.post_status = 201
        self.requests = []

    def start(self):
        """Serve in a thread and return the base URL."""
        threading.Thread(target=self.serve_forever, daemon=True).start()
        return f"http://127.0.0.1:{self.server_address[1]}"

    def reset(self, statuses, jobs=(), post_status=201):
        """Set the answers of the next run and forget the recorded requests."""
        self.statuses, self.jobs, self.post_status = (
            list(statuses),
            list(jobs),
            post_status,
        )
        self.requests.clear()


class FakeGitLabHandler(BaseHTTPRequestHandler):
    """Handle one request of the generate script."""

    def log_message(self, *args):
        """Keep the console quiet."""

    def reply(self, status, payload):
        """Send a JSON answer."""
        data = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):  # pylint: disable=invalid-name
        """Answer the start of a pipeline."""
        length = int(self.headers.get("Content-Length") or 0)
        body = json.loads(self.rfile.read(length) or b"null")
        token = self.headers.get("PRIVATE-TOKEN")
        self.server.requests.append(("POST", self.path, token, body))
        if self.server.post_status >= 400:
            self.reply(self.server.post_status, {"message": "refused"})
        else:
            pipeline = {
                "id": 42,
                "status": "pending",
                "web_url": "http://gitlab.test/p/42",
            }
            self.reply(self.server.post_status, pipeline)

    def do_GET(self):  # pylint: disable=invalid-name
        """Answer a status or a jobs request."""
        token = self.headers.get("PRIVATE-TOKEN")
        self.server.requests.append(("GET", self.path, token, None))
        statuses = self.server.statuses
        if "/jobs" in self.path:
            self.reply(200, self.server.jobs)
        else:
            status = statuses.pop(0) if len(statuses) > 1 else statuses[0]
            self.reply(200, {"id": 42, "status": status})


class GenerateHarness:
    """Run the generate script in a temporary project against a fake GitLab."""

    def __init__(self, folder, server):
        self.folder, self.server = folder, server
        (Path(folder) / "vars.json").write_text(
            json.dumps({"variables": PIPELINE_VARIABLES}),
            encoding="utf-8",
        )

    def configure(self, gitlab_url):
        """Write the config of the temporary project."""
        config_dir = Path(self.folder) / ".tdm-assistant"
        config_dir.mkdir(exist_ok=True)
        config = {
            "GITLAB_URL": gitlab_url,
            "GITLAB_PROJECT_ID": "7",
            "GITLAB_REF": "main",
            "GENERATE_VARIABLES_FILE": "vars.json",
        }
        (config_dir / "config.json").write_text(json.dumps(config), encoding="utf-8")

    def generate(self, *args, with_token=True):
        """Run the script; return (exit code, JSON or None, stdout and stderr)."""
        env = {k: v for k, v in os.environ.items() if k != "GITLAB_TOKEN"}
        if with_token:
            env["GITLAB_TOKEN"] = GITLAB_TOKEN
        done = subprocess.run(
            [sys.executable, script("generate"), *args],
            capture_output=True,
            cwd=self.folder,
            env=env,
            check=False,
        )
        text = done.stdout.decode("utf-8") + done.stderr.decode("utf-8")
        try:
            return done.returncode, json.loads(done.stdout.decode("utf-8")), text
        except ValueError:
            return done.returncode, None, text

    def error(self, label, code, status, out):
        """Check an error result with the given code."""
        check(
            label,
            status == 1 and out and out["error"] == code,
            shorten(out),
        )

    def validation_scenarios(self):
        """Check the plan, the validation, and the missing token."""
        self.server.reset(["success"])
        status, out, _ = self.generate(
            "--set",
            "EXECUTION_TYPE=test",
            "--set",
            "EXECUTION_NAME=Gen domestic",
        )
        check(
            "generate without --yes only shows the variables",
            status == 0
            and out["started"] is False
            and not self.server.requests
            and out["variables"]["EXECUTION_NAME"] == "Gen domestic"
            and sorted(out["changed"]) == ["EXECUTION_NAME", "EXECUTION_TYPE"],
            shorten(out),
        )
        status, out, _ = self.generate("--set", "ENABLE_JIRA_INTEGRATION=TRUE")
        check(
            "a boolean is normalized to lowercase",
            status == 0 and out["variables"]["ENABLE_JIRA_INTEGRATION"] == "true",
        )
        for label, argument in (
            ("an unknown variable", "NOPE=1"),
            ("a boolean that is not true or false", "SEND_WEBEX_NOTIFICATION=yes"),
            ("an empty EXECUTION_NAME", "EXECUTION_NAME="),
        ):
            status, out, _ = self.generate("--set", argument)
            self.error(f"generate rejects {label}", "invalid_variables", status, out)
        status, out, _ = self.generate("--yes", with_token=False)
        good = status == 1 and out and out["error"] == "token_missing"
        check("generate --yes needs a token", good and not self.server.requests)

    def success_scenarios(self):
        """Check a pipeline that succeeds."""
        self.server.reset(["pending", "running", "success"])
        status, out, text = self.generate(
            "--yes",
            "--interval",
            "0.05",
            "--set",
            "EXECUTION_NAME=Gen domestic",
        )
        post = next((r for r in self.server.requests if r[0] == "POST"), None)
        good = status == 0 and out["started"] and out["succeeded"]
        good = good and out["pipeline"]["id"] == 42
        check("generate waits for the pipeline and reports success", good, text[:300])
        sent = {"key": "EXECUTION_NAME", "value": "Gen domestic"}
        check(
            "generate sends the ref and the variables",
            post
            and post[1] == "/api/v4/projects/7/pipeline"
            and post[3]["ref"] == "main"
            and sent in post[3]["variables"],
            str(post),
        )
        check(
            "generate sends the token in PRIVATE-TOKEN and nowhere else",
            post and post[2] == GITLAB_TOKEN and GITLAB_TOKEN not in text,
            text[:300],
        )

    def failure_scenarios(self):
        """Check a failed pipeline, a timeout, a refusal, and an unreachable GitLab."""
        job = {"name": "generate", "stage": "test", "web_url": "http://gitlab.test/j/1"}
        self.server.reset(["running", "failed"], [job])
        status, out, _ = self.generate("--yes", "--interval", "0.05")
        check(
            "generate reports a failed pipeline with its failed jobs",
            status == 0
            and out["succeeded"] is False
            and out["pipeline"]["status"] == "failed"
            and out["failed_jobs"][0]["name"] == "generate"
            and out["pipeline"]["web_url"],
            str(out),
        )
        self.server.reset(["running"])
        status, out, _ = self.generate("--yes", "--interval", "0.1", "--timeout", "0.5")
        good = status == 1 and out and out["error"] == "timeout"
        good = good and out["pipeline"]["id"] == 42
        check("generate reports a timeout and the pipeline", good, str(out))
        self.server.reset(["success"], post_status=401)
        status, out, text = self.generate("--yes")
        good = status == 1 and out and out["error"] == "http_error"
        good = good and out["status"] == 401 and GITLAB_TOKEN not in text
        check("generate reports HTTP 401 without the token", good, text[:300])
        self.configure("http://127.0.0.1:1")
        status, out, _ = self.generate("--yes")
        self.error(
            "generate reports an unreachable GitLab",
            "server_unreachable",
            status,
            out,
        )


def generate_scenarios():
    """Run the generate script against a fake GitLab; it needs no TDM3."""
    print("\n-- generate (fake GitLab)")
    server = FakeGitLab()
    url = server.start()
    with tempfile.TemporaryDirectory() as folder:
        harness = GenerateHarness(folder, server)
        harness.configure(url)
        harness.validation_scenarios()
        harness.success_scenarios()
        harness.failure_scenarios()
    server.shutdown()


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
    system = config["SYSTEM_NAME_DEFAULT"]
    print(f"TDM3: {config['TDM3_BASE_URL']}  system: {system}  table: {table}")
    try:
        drop_selftest_tables(config)
        table_scenarios(table)
        generate_scenarios()
    finally:
        print("\n-- cleanup")
        drop_selftest_tables(config)
        tables = tdm.list_of_tables(tdm.lookup_table(config, PREFIX)[1])
        left = [t for t in tables if t["name"].startswith(PREFIX)]
        check("no selftest table is left", not left, str(left))

    failed = [label for label, ok in results if not ok]
    print(f"\n{len(results) - len(failed)} of {len(results)} checks passed")
    for label in failed:
        print(f"  FAILED: {label}")
    return 1 if failed else 0


if __name__ == "__main__":
    tdm.use_utf8_output()
    sys.exit(main())
