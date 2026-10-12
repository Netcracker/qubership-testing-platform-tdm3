#!/usr/bin/env python3
"""Run the scripts of the tdm-test-data-assistant skills against a temporary table in a running TDM3.

The scripts are the ones in .apm/skills/*/scripts. The run creates a table named ZZ_selftest_<timestamp> in the
current system of .tdm-assistant/config.json (searched from the working directory upwards), exercises every script
and its error codes on that table, and drops the table at the end, also after a failure. It touches no other table.
The tests do not cover the agent: how it parses a command or words a reply is checked by hand.

  python tests/selftest.py [--allow-remote]

The run refuses a TDM3 server that is not on localhost, unless --allow-remote is given. The exit code is 0 when every
check passes and 1 otherwise. Python 3.8+, standard library only.
"""
import argparse
import getpass
import json
import os
import subprocess
import sys
import tempfile
import threading
import urllib.parse
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.dont_write_bytecode = True  # keep __pycache__ out of the repository

SKILLS = Path(__file__).resolve().parents[1] / ".apm" / "skills"
sys.path.insert(0, str(SKILLS / "tdm-common" / "scripts"))
import tdm_common as tdm  # noqa: E402

PREFIX = "ZZ_selftest_"
LOCAL_HOSTS = ("localhost", "127.0.0.1", "[::1]")
TAB = chr(9)

results = []


def script(name):
    skill, file = {
        "list": ("tdm-list-tables-script", "list_tables.py"),
        "find": ("tdm-find-script", "find_rows.py"),
        "reserve": ("tdm-reserve-script", "reserve_rows.py"),
        "release": ("tdm-release-script", "release_rows.py"),
        "insert": ("tdm-insert-script", "insert_row.py"),
        "update": ("tdm-update-script", "update_rows.py"),
        "generate": ("tdm-generate-test-data", "generate_test_data.py"),
    }[name]
    return str(SKILLS / skill / "scripts" / file)


def run(name, *args):
    """Run a script and return (exit code, decoded JSON, or None when the output is not JSON)."""
    done = subprocess.run([sys.executable, script(name), *args], capture_output=True)
    try:
        return done.returncode, json.loads(done.stdout.decode("utf-8"))
    except ValueError:
        return done.returncode, None


def check(label, condition, detail=""):
    results.append((label, bool(condition)))
    print(("PASS  " if condition else "FAIL  ") + label + ("" if condition else f"   {detail}"))


def expect_error(label, name, code, *args, **fields):
    """Check that a script exits with 1 and the given error code, and that extra fields hold."""
    status, out = run(name, *args)
    good = status == 1 and out and out.get("ok") is False and out.get("error") == code
    good = good and all(out.get(k) == v for k, v in fields.items())
    check(label, good, json.dumps(out, ensure_ascii=False)[:300])
    return out


def expect_ok(label, name, *args, **fields):
    """Check that a script exits with 0 and ok true, and that extra fields hold."""
    status, out = run(name, *args)
    good = status == 0 and out and out.get("ok") is True and all(out.get(k) == v for k, v in fields.items())
    check(label, good, json.dumps(out, ensure_ascii=False)[:300])
    return out


def records(table, *args):
    status, out = run("find", "--table", table, "--return", "code", *args)
    return out["records"] if status == 0 and out else None


def drop_table(config, table_id):
    tdm.call(config, "/api/tdm/table", query={"tableName": table_id}, method="DELETE")


def drop_selftest_tables(config):
    """Drop the tables this run or an earlier one created; only names with the selftest prefix qualify."""
    _, catalog = tdm.lookup_table(config, PREFIX)
    for entry in tdm.list_of_tables(catalog):
        if entry["name"].startswith(PREFIX):
            print(f"      dropping {entry['name']}")
            drop_table(config, entry["id"])


def scenarios(config, table):
    user = getpass.getuser()
    base = ["--table", table]

    print("\n-- insert")
    expect_error("insert without --new into an unknown table", "insert", "table_not_found", *base,
                 "--value", "code=P1")
    out = expect_ok("insert --new creates the table", "insert", *base, "--value", "code=P1", "--value", "grp=A",
                    "--value", "note=first", "--new", created=True)
    check("insert --new returns the table name and id", out and out["table"]["id"].startswith("TDM_"))
    expect_error("insert --new into an existing table", "insert", "table_exists", *base, "--value", "code=P9",
                 "--new")
    expect_ok("insert a second row", "insert", *base, "--value", "code=P2", "--value", "grp=A", "--value",
              "note=second")
    expect_ok("insert a third row", "insert", *base, "--value", "code=P3", "--value", "grp=B", "--value",
              "note=third")
    out = expect_error("insert with a wrong-case column", "insert", "column_not_found", *base, "--value",
                       "Code=P4")
    check("the wrong-case column comes with a suggestion", out and out.get("suggestion") == "code")
    expect_error("insert with a TAB in a value needs a confirmation", "insert", "invisible_characters", *base,
                 "--value", f"code=P5{TAB}x")
    expect_error("insert with no values", "insert", "bad_argument", *base)
    expect_ok("insert a TAB value once confirmed", "insert", *base, "--value", f"code=P5{TAB}x", "--value",
              "grp=C", "--confirm-invisible")

    print("\n-- find")
    check("find counts all four rows", records(table) == 4)
    check("find filters by an exact value", records(table, "--filter", "grp=A") == 2)
    check("Equals is case-sensitive (behavior pin)", records(table, "--filter", "code=p1") == 0)
    status, out = run("find", *base, "--return", "note,code", "--filter", "code=P1")
    check("find returns the columns asked for, in order",
          status == 0 and out["columns"] == ["note", "code"] and out["rows"][0]["values"] == ["first", "P1"],
          json.dumps(out, ensure_ascii=False)[:300])
    check("find returns the row id", status == 0 and len(out["rows"][0]["row_id"]) == 36)
    out = expect_error("find with a wrong-case filter column", "find", "column_not_found", *base, "--filter",
                       "Grp=A")
    check("the wrong-case filter column comes with a suggestion", out and out.get("suggestion") == "grp")
    expect_error("find with an unknown table", "find", "table_not_found", "--table", PREFIX + "none")
    status, out = run("find", *base, "--filter", f"code=a{TAB}b")
    check("find warns about a TAB in a filter value", status == 0 and "TAB" in "".join(out["warnings"]))
    check("find shows no reserved row yet", records(table, "--occupied") == 0)

    print("\n-- update")
    out = expect_ok("update with no match sends nothing", "update", *base, "--where", "code=none", "--set",
                    "note=x", no_row_matched=True, updated=0)
    expect_error("update of several rows needs --all", "update", "multiple_matches", *base, "--where", "grp=A",
                 "--set", "note=x", matched=2)
    out = expect_error("update with a wrong-case column in --set", "update", "column_not_found", *base,
                       "--where", "code=P1", "--set", "Note=x")
    expect_error("update with no --where", "update", "bad_argument", *base, "--set", "note=x")
    expect_ok("update one row", "update", *base, "--where", "code=P1", "--set", "note=changed", updated=1)
    expect_ok("update several rows with --all", "update", *base, "--where", "grp=A", "--set", "note=bulk",
              "--all", updated=2)
    status, out = run("find", *base, "--return", "note", "--filter", "grp=A")
    check("the updated values are stored", status == 0 and [r["values"][0] for r in out["rows"]] == ["bulk"] * 2)
    expect_error("update to a TAB value needs a confirmation", "update", "invisible_characters", *base,
                 "--where", "code=P3", "--set", f"note=a{TAB}b")

    print("\n-- reserve")
    expect_error("reserve with no filter", "reserve", "filter_required", *base, "--return", "code")
    expect_error("reserve with a wrong-case return column", "reserve", "column_not_found", *base, "--return",
                 "Code", "--filter", "grp=A")
    out = expect_ok("reserve one row", "reserve", *base, "--return", "code", "--filter", "grp=A", count=1)
    check("the reserved value is a row of the group", out and out["reserved"][0] in ("P1", "P2"))
    first = out["reserved"][0]
    rest = "P2" if first == "P1" else "P1"
    check("reserve records the operating-system user",
          out and out["occupied_by"] == user, f"{out and out.get('occupied_by')!r} != {user!r}")
    status, out = run("find", *base, "--occupied", "--return", "code")
    check("the reserved row shows the user, unchanged", status == 0 and out["rows"][0]["values"][0] == user,
          json.dumps(out, ensure_ascii=False)[:300])
    check("OCCUPIED_BY and OCCUPIED_DATE come first",
          status == 0 and out["columns"][:2] == ["OCCUPIED_BY", "OCCUPIED_DATE"])
    out = expect_ok("reserve every remaining match with --all", "reserve", *base, "--return", "code", "--filter",
                    "grp=A", "--all", count=1)
    out = expect_ok("reserve with --all when nothing is left", "reserve", *base, "--return", "code", "--filter",
                    "grp=A", "--all", count=0, no_row_matched=True)
    out = expect_ok("reserve any row with --any", "reserve", *base, "--return", "code", "--any", count=1)
    check("--any took a free row", out and out["reserved"][0] in ("P3", "P5" + TAB + "x"))
    check("three rows are reserved", records(table, "--occupied") == 3)

    print("\n-- update of a reserved row")
    expect_ok("update counts a reserved row", "update", *base, "--where", "grp=A", "--set", "note=again", "--all",
              matched=2, matched_reserved=2, updated=2)

    print("\n-- release")
    expect_error("release with no filter", "release", "filter_required", *base)
    expect_error("release with a filter and no --return", "release", "bad_argument", *base, "--filter", "grp=A")
    expect_error("release of several rows needs --all", "release", "multiple_matches", *base, "--return",
                 "code", "--filter", "grp=A")
    expect_ok("release one row", "release", *base, "--return", "code", "--filter", f"code={first}",
              released=[first])
    expect_ok("release finds no match", "release", *base, "--return", "code", "--filter", f"code={first}",
              no_row_matched=True, count=0)
    expect_ok("release the rest of the group with --all", "release", *base, "--return", "code", "--filter",
              "grp=A", "--all", released=[rest])
    check("one reserved row is left", records(table, "--occupied") == 1)
    expect_error("release --whole-table takes no filter", "release", "bad_argument", *base, "--whole-table",
                 "--filter", "grp=A")
    expect_ok("release --whole-table", "release", *base, "--whole-table")
    check("no reserved row is left", records(table, "--occupied") == 0)

    print("\n-- list")
    status, out = run("list")
    check("list shows the table of the current system",
          status == 0 and any(t["name"] == table for t in out["tables"]))
    status, out = run("list", "--whole-project")
    check("list --whole-project is not smaller than the system list",
          status == 0 and any(t["name"] == table for t in out["tables"]))


class FakeGitLab(ThreadingHTTPServer):
    """Answer the three GitLab requests of the generate script, and record what arrived."""

    def __init__(self):
        super().__init__(("127.0.0.1", 0), FakeGitLabHandler)
        self.statuses = ["success"]
        self.jobs = []
        self.post_status = 201
        self.requests = []

    def start(self):
        threading.Thread(target=self.serve_forever, daemon=True).start()
        return f"http://127.0.0.1:{self.server_address[1]}"


class FakeGitLabHandler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, status, payload):
        data = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = json.loads(self.rfile.read(length) or b"null")
        self.server.requests.append(("POST", self.path, self.headers.get("PRIVATE-TOKEN"), body))
        if self.server.post_status >= 400:
            return self.reply(self.server.post_status, {"message": "refused"})
        self.reply(self.server.post_status, {"id": 42, "status": "pending", "web_url": "http://gitlab.test/p/42"})

    def do_GET(self):
        self.server.requests.append(("GET", self.path, self.headers.get("PRIVATE-TOKEN"), None))
        if "/jobs" in self.path:
            return self.reply(200, self.server.jobs)
        statuses = self.server.statuses
        self.reply(200, {"id": 42, "status": statuses.pop(0) if len(statuses) > 1 else statuses[0]})


def generate_scenarios():
    """Run the generate script against a fake GitLab; it needs no TDM3."""
    token = "tok-SECRET-123"
    variables = {"variables": {"PIPELINE_RUNTIME_CONFIG": "environments/x.yaml", "ATP_TESTS_GIT_REPO_URL": "http://g/r.git",
                               "ATP_TESTS_GIT_REPO_BRANCH": "main", "EXECUTION_TYPE": "scope",
                               "EXECUTION_NAME": "regression", "ENABLE_JIRA_INTEGRATION": "false",
                               "SEND_WEBEX_NOTIFICATION": "true"}}
    server = FakeGitLab()
    url = server.start()
    with tempfile.TemporaryDirectory() as folder:
        def configure(gitlab_url):
            (Path(folder) / ".tdm-assistant").mkdir(exist_ok=True)
            (Path(folder) / ".tdm-assistant" / "config.json").write_text(json.dumps({
                "GITLAB_URL": gitlab_url, "GITLAB_PROJECT_ID": "7", "GITLAB_REF": "main",
                "GENERATE_VARIABLES_FILE": "vars.json"}), encoding="utf-8")
        (Path(folder) / "vars.json").write_text(json.dumps(variables), encoding="utf-8")
        configure(url)

        def generate(*args, with_token=True):
            env = {k: v for k, v in os.environ.items() if k != "GITLAB_TOKEN"}
            if with_token:
                env["GITLAB_TOKEN"] = token
            done = subprocess.run([sys.executable, script("generate"), *args], capture_output=True, cwd=folder, env=env)
            out, err = done.stdout.decode("utf-8"), done.stderr.decode("utf-8")
            try:
                return done.returncode, json.loads(out), out + err
            except ValueError:
                return done.returncode, None, out + err

        def reset(statuses, jobs=(), post_status=201):
            server.statuses, server.jobs, server.post_status = list(statuses), list(jobs), post_status
            server.requests.clear()

        print("\n-- generate (fake GitLab)")
        reset(["success"])
        status, out, _ = generate("--set", "EXECUTION_TYPE=test", "--set", "EXECUTION_NAME=Gen domestic")
        check("generate without --yes only shows the variables",
              status == 0 and out["started"] is False and not server.requests
              and out["variables"]["EXECUTION_NAME"] == "Gen domestic"
              and sorted(out["changed"]) == ["EXECUTION_NAME", "EXECUTION_TYPE"], json.dumps(out)[:300])
        status, out, _ = generate("--set", "ENABLE_JIRA_INTEGRATION=TRUE")
        check("a boolean is normalized to lowercase", status == 0 and out["variables"]["ENABLE_JIRA_INTEGRATION"] == "true")
        for label, argument in (("an unknown variable", "NOPE=1"), ("a boolean that is not true or false",
                                "SEND_WEBEX_NOTIFICATION=yes"), ("an empty EXECUTION_NAME", "EXECUTION_NAME=")):
            status, out, _ = generate("--set", argument)
            check(f"generate rejects {label}", status == 1 and out and out["error"] == "invalid_variables", str(out))
        status, out, _ = generate("--yes", with_token=False)
        check("generate --yes needs a token", status == 1 and out and out["error"] == "token_missing" and not server.requests)

        reset(["pending", "running", "success"])
        status, out, text = generate("--yes", "--interval", "0.05", "--set", "EXECUTION_NAME=Gen domestic")
        post = next((r for r in server.requests if r[0] == "POST"), None)
        check("generate waits for the pipeline and reports success",
              status == 0 and out["started"] and out["succeeded"] and out["pipeline"]["id"] == 42, text[:300])
        check("generate sends the ref and the variables",
              post and post[1] == "/api/v4/projects/7/pipeline" and post[3]["ref"] == "main"
              and {"key": "EXECUTION_NAME", "value": "Gen domestic"} in post[3]["variables"], str(post))
        check("generate sends the token in PRIVATE-TOKEN and nowhere else",
              post and post[2] == token and token not in text, text[:300])

        reset(["running", "failed"], [{"name": "generate", "stage": "test", "web_url": "http://gitlab.test/j/1"}])
        status, out, _ = generate("--yes", "--interval", "0.05")
        check("generate reports a failed pipeline with its failed jobs",
              status == 0 and out["succeeded"] is False and out["pipeline"]["status"] == "failed"
              and out["failed_jobs"][0]["name"] == "generate" and out["pipeline"]["web_url"], str(out))

        reset(["running"])
        status, out, _ = generate("--yes", "--interval", "0.1", "--timeout", "0.5")
        check("generate reports a timeout and the pipeline", status == 1 and out and out["error"] == "timeout"
              and out["pipeline"]["id"] == 42, str(out))

        reset(["success"], post_status=401)
        status, out, text = generate("--yes")
        check("generate reports HTTP 401 without the token", status == 1 and out and out["error"] == "http_error"
              and out["status"] == 401 and token not in text, text[:300])

        configure("http://127.0.0.1:1")
        status, out, _ = generate("--yes")
        check("generate reports an unreachable GitLab", status == 1 and out and out["error"] == "server_unreachable", str(out))
    server.shutdown()


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--allow-remote", action="store_true", help="allow a TDM3 server that is not on localhost")
    args = parser.parse_args()

    config = tdm.load_config(tdm.CONFIG_FULL)
    host = urllib.parse.urlparse(config["TDM3_BASE_URL"]).netloc.rsplit(":", 1)[0]
    if host not in LOCAL_HOSTS and not args.allow_remote:
        print(f"Refusing to run against {config['TDM3_BASE_URL']}: not localhost. Pass --allow-remote to override.")
        return 1

    table = PREFIX + datetime.now().strftime("%Y%m%d%H%M%S")
    print(f"TDM3: {config['TDM3_BASE_URL']}  system: {config['SYSTEM_NAME_DEFAULT']}  table: {table}")
    try:
        drop_selftest_tables(config)
        scenarios(config, table)
        generate_scenarios()
    finally:
        print("\n-- cleanup")
        drop_selftest_tables(config)
        left = [t for t in tdm.list_of_tables(tdm.lookup_table(config, PREFIX)[1]) if t["name"].startswith(PREFIX)]
        check("no selftest table is left", not left, str(left))

    failed = [label for label, ok in results if not ok]
    print(f"\n{len(results) - len(failed)} of {len(results)} checks passed")
    for label in failed:
        print(f"  FAILED: {label}")
    return 1 if failed else 0


if __name__ == "__main__":
    tdm.use_utf8_output()
    sys.exit(main())
