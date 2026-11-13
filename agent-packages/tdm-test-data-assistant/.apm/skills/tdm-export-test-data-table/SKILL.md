---
name: tdm-export-test-data-table
description: Use when the user wants a TDM3 table's data as a downloadable Excel or CSV file, rather than as rows in the response. Calls the TDM3 REST API directly.
user-invocable: false
---

# Download a TDM3 table as a file

```bash
curl -o table.xlsx '<TDM3_BASE_URL>/api/tdm/download/excel?tableName=<tableName>'
curl -o table.csv '<TDM3_BASE_URL>/api/tdm/download/csv?tableName=<tableName>'
```

Addressed by database table name (from [tdm-list-tables](../tdm-list-tables/SKILL.md)). The response body is the
file itself — `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` for Excel, `text/csv` for CSV —
not JSON, so save it to disk rather than parsing it as a request result. Give the saved path back to the user
rather than the file's raw bytes.

## Common pitfalls

- Treating the response as JSON: both endpoints return a binary/text file body directly, with no `ResponseMessage`
  envelope.
- Reaching for this skill to read data programmatically: for that, use
  [tdm-browse-test-data-table](../tdm-browse-test-data-table/SKILL.md) or
  [tdm-find-test-data](../tdm-find-test-data/SKILL.md) instead — this skill is for handing the user a file.
