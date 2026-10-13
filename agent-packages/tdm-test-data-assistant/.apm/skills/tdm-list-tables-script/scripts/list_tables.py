"""List the test data tables of the current TDM3 system.

Reads .tdm-assistant/config.json, requests GET /api/tdm/tables/catalog with the
project and system IDs from it, and prints one JSON object on stdout (the output
rules are in tdm_common):

  {"ok": true, "scope": "system", "system": "CMS",
   "tables": [{"name": "Agents", "id": "TDM_..."}]}

With --whole-project the catalog is not limited to the current system and
"system" is absent.
"""

import importlib
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # keep __pycache__ out of the skill folder
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tdm-common" / "scripts"))
tdm = importlib.import_module("tdm_common")


def main():
    """Parse the arguments, request the catalog, and print the tables."""
    parser = tdm.new_parser(__doc__)
    tdm.add_switch(parser, "--whole-project", "list the tables of every system")
    args = parser.parse_args()

    required = ["TDM3_BASE_URL", "PROJECT_ID"]
    if not args.whole_project:
        required.append("SYSTEM_ID_DEFAULT")
    config = tdm.load_config(required)

    query = {"projectId": config["PROJECT_ID"]}
    if not args.whole_project:
        query["systemId"] = config["SYSTEM_ID_DEFAULT"]
    catalog = tdm.call(config, "/api/tdm/tables/catalog", query=query)

    result = {
        "scope": "project" if args.whole_project else "system",
        "tables": tdm.list_of_tables(catalog),
    }
    if not args.whole_project:
        result["system"] = config.get("SYSTEM_NAME_DEFAULT")
    tdm.succeed(**result)


if __name__ == "__main__":
    tdm.use_utf8_output()
    main()
