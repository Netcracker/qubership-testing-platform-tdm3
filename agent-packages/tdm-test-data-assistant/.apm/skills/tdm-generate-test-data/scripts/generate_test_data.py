"""Start the GitLab pipeline that generates test data, and wait until it finishes.

The pipeline runs the test cases of the project, which fill the business tables
and, as their last step, the tables of TDM3. This script writes nothing to TDM3.
It reads the pipeline variables from the JSON file named by
GENERATE_VARIABLES_FILE in .tdm-assistant/config.json, replaces the ones given
with --set, and starts the pipeline with POST /api/v4/projects/<id>/pipeline.
It then polls the pipeline until it ends. The output rules are in tdm_common, with
one addition: progress lines go to stderr, and the result is the one JSON object
on stdout.

  generate_test_data.py [--set VARIABLE=value ...]        show the variables only
  generate_test_data.py [--set VARIABLE=value ...] --yes  start and wait

Config keys: GITLAB_URL, GITLAB_PROJECT_ID, GITLAB_REF (the branch to run the
pipeline from), and GENERATE_VARIABLES_FILE (relative to the project folder, or
absolute). The token comes from the environment variable named by
GITLAB_TOKEN_ENV (default GITLAB_TOKEN) or, when that variable is empty, from the
first line of .tdm-assistant/gitlab-token. The token is never printed.

The variables are checked before anything is sent: --set names an existing
variable; ATP_TESTS_GIT_REPO_URL, EXECUTION_TYPE, and EXECUTION_NAME are not
empty; ENABLE_JIRA_INTEGRATION and SEND_WEBEX_NOTIFICATION are true or false. The
values of EXECUTION_TYPE and EXECUTION_NAME are not checked.

Without --yes:
  {"ok": true, "started": false, "ref": "main", "variables": {...},
   "changed": ["EXECUTION_NAME"]}
With --yes, when the pipeline ends:
  {"ok": true, "started": true, "succeeded": false,
   "pipeline": {"id": 7, "status": "failed", "web_url": "..."},
   "variables": {...}, "waited_seconds": 190,
   "failed_jobs": [{"name": "...", "stage": "...", "web_url": "..."}]}

Error codes: config_missing, config_incomplete, bad_argument,
invalid_variables (with "problems"), token_missing, http_error (with "status"),
server_unreachable, timeout (with "pipeline"; the pipeline keeps running),
poll_failed (with "pipeline"). Python 3.8+, standard library only.
"""

import argparse
import importlib
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

sys.dont_write_bytecode = True  # keep __pycache__ out of the skill folder
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tdm-common" / "scripts"))
tdm = importlib.import_module("tdm_common")

REQUIRED_KEYS = (
    "GITLAB_URL",
    "GITLAB_PROJECT_ID",
    "GITLAB_REF",
    "GENERATE_VARIABLES_FILE",
)
NOT_EMPTY = ("ATP_TESTS_GIT_REPO_URL", "EXECUTION_TYPE", "EXECUTION_NAME")
BOOLEANS = ("ENABLE_JIRA_INTEGRATION", "SEND_WEBEX_NOTIFICATION")
FINISHED = ("success", "failed", "canceled", "skipped", "manual")
REQUEST_TIMEOUT = 30
POLL_FAILURES_ALLOWED = 3
TOKEN_FILE = "gitlab-token"


class GitLabError(Exception):
    """A failed GitLab request, with the error code the script reports."""

    def __init__(self, code, message, **extra):
        super().__init__(message)
        self.code, self.message, self.extra = code, message, extra


def progress(line):
    """Print a progress line to stderr."""
    print(line, file=sys.stderr, flush=True)


def read_token(config):
    """Return the token from the environment variable or the token file, or fail."""
    env_name = config.get("GITLAB_TOKEN_ENV") or "GITLAB_TOKEN"
    token = (os.environ.get(env_name) or "").strip()
    token_path = Path(config["CONFIG_DIR"]) / TOKEN_FILE
    if not token and token_path.is_file():
        lines = token_path.read_text(encoding="utf-8").strip().splitlines()
        token = lines[0].strip() if lines else ""
    if not token:
        tdm.fail(
            "token_missing",
            f"No GitLab token. Set the environment variable {env_name}, or put "
            f"the token on the first line of {token_path}. "
            "Don't give it to the agent.",
        )
    return token


def gitlab(config, token, method, path, body=None):
    """Send one request to GitLab and return the decoded JSON; raise GitLabError."""
    base = f"{config['GITLAB_URL'].rstrip('/')}/api/v4/projects"
    url = f"{base}/{config['GITLAB_PROJECT_ID']}{path}"
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"PRIVATE-TOKEN": token}
    if data:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raise GitLabError(
            "http_error",
            f"HTTP {exc.code} from GitLab for {method} {path}",
            status=exc.code,
            body=exc.read().decode("utf-8", "replace")[:300],
        ) from exc
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise GitLabError(
            "server_unreachable",
            f"Cannot reach GitLab at {config['GITLAB_URL']}: {exc}",
        ) from exc
    except ValueError as exc:
        raise GitLabError(
            "http_error", f"The GitLab response is not JSON: {exc}"
        ) from exc


def as_text(value):
    """Return a JSON value as the string that GitLab receives."""
    if isinstance(value, bool):
        return "true" if value else "false"
    return value if isinstance(value, str) else str(value)


def read_variables(config):
    """Return the variables of the variables file as strings, or fail."""
    path = Path(config["GENERATE_VARIABLES_FILE"])
    if not path.is_absolute():
        path = Path(config["CONFIG_DIR"]).parent / path
    try:
        variables = json.loads(path.read_text(encoding="utf-8"))["variables"]
        return {str(k): as_text(v) for k, v in variables.items()}
    except (OSError, ValueError, KeyError, AttributeError) as exc:
        return tdm.fail(
            "config_incomplete", f"Cannot read the variables from {path}: {exc!r}"
        )


def check_variables(variables):
    """Normalize the booleans in place and return the problems found."""
    problems = []
    for name in BOOLEANS:
        if name in variables:
            lowered = variables[name].strip().lower()
            if lowered in ("true", "false"):
                variables[name] = lowered
            else:
                problems.append(
                    f"'{name}' must be true or false, not '{variables[name]}'"
                )
    for name in NOT_EMPTY:
        if not variables.get(name, "").strip():
            problems.append(f"'{name}' must not be empty")
    return problems


def load_variables(config, overrides):
    """Return (variables, changed names) with the overrides applied, or fail."""
    variables = read_variables(config)
    problems, changed = [], []
    for name, value in overrides:
        if name not in variables:
            known = ", ".join(sorted(variables))
            problems.append(f"'{name}' is not a variable of the file. Known: {known}")
            continue
        if variables[name] != value:
            changed.append(name)
        variables[name] = value
    problems += check_variables(variables)
    if problems:
        tdm.fail("invalid_variables", "; ".join(problems), problems=problems)
    return variables, changed


def failed_jobs(config, token, pipeline_id):
    """Return the failed jobs of the pipeline, or [] when GitLab does not answer."""
    path = f"/pipelines/{pipeline_id}/jobs?scope[]=failed&per_page=20"
    try:
        jobs = gitlab(config, token, "GET", path)
    except GitLabError:
        return []
    return [
        {"name": j.get("name"), "stage": j.get("stage"), "web_url": j.get("web_url")}
        for j in jobs
    ]


def parse_arguments():
    """Return the parsed command line."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--set",
        dest="sets",
        action="append",
        default=[],
        help="VARIABLE=value; repeat for more",
    )
    parser.add_argument(
        "--yes",
        action="store_true",
        help="start the pipeline; without it only the variables are shown",
    )
    parser.add_argument(
        "--interval", type=float, default=30, help="seconds between two status requests"
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=1800,
        help="seconds to wait for the pipeline to end",
    )
    return parser.parse_args()


def start_pipeline(config, token, variables):
    """Start the pipeline and return {"id", "status", "web_url"}."""
    payload = {
        "ref": config["GITLAB_REF"],
        "variables": [{"key": k, "value": v} for k, v in variables.items()],
    }
    try:
        created = gitlab(config, token, "POST", "/pipeline", payload)
    except GitLabError as exc:
        tdm.fail(exc.code, exc.message, **exc.extra)
    return {
        "id": created.get("id"),
        "status": created.get("status", "unknown"),
        "web_url": created.get("web_url", ""),
    }


def poll_status(config, token, pipeline, variables, args):
    """Poll until the pipeline ends; return the seconds waited, or fail."""
    started, failures = time.monotonic(), 0
    while pipeline["status"] not in FINISHED:
        waited = time.monotonic() - started
        if waited >= args.timeout:
            tdm.fail(
                "timeout",
                f"The pipeline did not end within {int(args.timeout)} seconds. "
                "It keeps running.",
                pipeline=pipeline,
                variables=variables,
            )
        time.sleep(min(args.interval, max(args.timeout - waited, 0)))
        try:
            answer = gitlab(config, token, "GET", f"/pipelines/{pipeline['id']}")
            pipeline["status"] = answer.get("status", "unknown")
            failures = 0
        except GitLabError as exc:
            failures += 1
            progress(
                f"Status request failed ({exc.message}); "
                f"attempt {failures} of {POLL_FAILURES_ALLOWED}"
            )
            if failures >= POLL_FAILURES_ALLOWED:
                tdm.fail(
                    "poll_failed",
                    f"{exc.message}. The pipeline may still be running.",
                    pipeline=pipeline,
                    variables=variables,
                )
        progress(
            f"Pipeline {pipeline['id']}: {pipeline['status']} "
            f"after {int(time.monotonic() - started)} s"
        )
    return int(time.monotonic() - started)


def main():
    """Show the variables, or start the pipeline and wait for it."""
    args = parse_arguments()
    overrides = tdm.parse_filters(args.sets)
    config = tdm.load_config(REQUIRED_KEYS)
    variables, changed = load_variables(config, overrides)

    if not args.yes:
        tdm.succeed(
            started=False,
            ref=config["GITLAB_REF"],
            variables=variables,
            changed=changed,
        )

    token = read_token(config)
    pipeline = start_pipeline(config, token, variables)
    progress(f"Pipeline {pipeline['id']} started: {pipeline['web_url']}")
    waited = poll_status(config, token, pipeline, variables, args)

    result = {
        "started": True,
        "succeeded": pipeline["status"] == "success",
        "pipeline": pipeline,
        "variables": variables,
        "waited_seconds": waited,
    }
    if pipeline["status"] == "failed":
        result["failed_jobs"] = failed_jobs(config, token, pipeline["id"])
    tdm.succeed(**result)


if __name__ == "__main__":
    tdm.use_utf8_output()
    main()
