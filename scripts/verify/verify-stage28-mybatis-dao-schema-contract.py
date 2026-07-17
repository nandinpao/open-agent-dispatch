#!/usr/bin/env python3
from pathlib import Path
import html
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
V1 = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql"
MAPPER_ROOT = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql"


def split_top_level(text: str):
    parts = []
    current = []
    depth = 0
    single = False
    double = False
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == "'" and not double:
            single = not single
        elif ch == '"' and not single:
            double = not double
        elif not single and not double:
            if ch == "(":
                depth += 1
            elif ch == ")" and depth > 0:
                depth -= 1
            elif ch == "," and depth == 0:
                value = "".join(current).strip()
                if value:
                    parts.append(value)
                current = []
                i += 1
                continue
        current.append(ch)
        i += 1
    value = "".join(current).strip()
    if value:
        parts.append(value)
    return parts


def baseline_tables_and_columns(sql: str):
    tables = {}
    for match in re.finditer(r"create\s+table\s+if\s+not\s+exists\s+(\w+)\s*\((.*?)\);", sql, re.IGNORECASE | re.DOTALL):
        table = match.group(1)
        columns = tables.setdefault(table, set())
        for segment in split_top_level(match.group(2)):
            tokens = segment.strip().split()
            if not tokens:
                continue
            first = tokens[0].lower()
            if first in {"primary", "unique", "foreign", "constraint", "check", "exclude"}:
                continue
            column = re.match(r"([A-Za-z_][A-Za-z0-9_]*)\b", segment.strip())
            if column:
                columns.add(column.group(1))
    for match in re.finditer(r"alter\s+table\s+(\w+)\s+add\s+column\s+if\s+not\s+exists\s+(\w+)", sql, re.IGNORECASE):
        tables.setdefault(match.group(1), set()).add(match.group(2))
    return tables


def expand_includes(mapper_text: str):
    snippets = dict(re.findall(r"<sql\s+id=\"([^\"]+)\">(.*?)</sql>", mapper_text, re.DOTALL))
    return re.sub(r"<include\s+refid=\"([^\"]+)\"\s*/>", lambda m: snippets.get(m.group(1), ""), mapper_text)


def mapper_insert_columns():
    for mapper in sorted(MAPPER_ROOT.rglob("*.xml")):
        text = expand_includes(html.unescape(mapper.read_text()))
        for match in re.finditer(r"insert\s+into\s+(\w+)\s*\((.*?)\)\s*values", text, re.IGNORECASE | re.DOTALL):
            table = match.group(1)
            raw_columns = re.sub(r"<[^>]+>", " ", match.group(2))
            columns = []
            for candidate in split_top_level(raw_columns):
                name = candidate.strip()
                if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name):
                    columns.append(name)
            yield mapper.relative_to(ROOT), table, columns


def mapper_table_references():
    references = {}
    ignored_aliases = {"candidate", "candidates", "skip", "id", "profile", "dependency_edges", "set", "where", "select", "values"}
    patterns = [
        r"\bfrom\s+(\w+)",
        r"\bjoin\s+(\w+)",
        r"\binsert\s+into\s+(\w+)",
        r"\bupdate\s+(\w+)",
        r"\bdelete\s+from\s+(\w+)",
    ]
    for mapper in sorted(MAPPER_ROOT.rglob("*.xml")):
        text = re.sub(r"\s+", " ", html.unescape(mapper.read_text()))
        for pattern in patterns:
            for match in re.finditer(pattern, text, re.IGNORECASE):
                table = match.group(1)
                if table in ignored_aliases:
                    continue
                references.setdefault(table, set()).add(str(mapper.relative_to(ROOT)))
    return references


def require_contains(sql: str, token: str):
    if token not in sql:
        print(f"Missing required SQL token: {token}")
        sys.exit(1)


def main():
    sql = V1.read_text()
    tables = baseline_tables_and_columns(sql)
    refs = mapper_table_references()
    ignored_legacy_tables = {"agent_assignment_profiles", "agent_qualifications"}
    missing_tables = sorted(table for table in refs if table not in tables and table not in ignored_legacy_tables)
    if missing_tables:
        print("MyBatis XML references tables missing from V1 baseline:")
        for table in missing_tables:
            print(f" - {table}: {sorted(refs[table])}")
        sys.exit(1)

    missing_columns = []
    ignored_legacy_tables = {"agent_assignment_profiles", "agent_qualifications"}
    for mapper, table, columns in mapper_insert_columns():
        if table in ignored_legacy_tables:
            continue
        baseline_columns = tables.get(table, set())
        for column in columns:
            if column not in baseline_columns:
                missing_columns.append((str(mapper), table, column))
    if missing_columns:
        print("MyBatis XML insert columns missing from V1 baseline:")
        for mapper, table, column in missing_columns:
            print(f" - {mapper}: {table}.{column}")
        sys.exit(1)

    required_tokens = [
        "create table if not exists incident_occurrence_summary",
        "create or replace function severity_rank",
        "unique (incident_id, window_start, window_end)",
        "create unique index if not exists ux_event_decisions_event_id",
        "alter table event_decisions alter column decision_id set default",
        "create unique index if not exists ux_task_issue_links_task_id",
        "create unique index if not exists ux_dispatch_attempt_history_history_id",
        "create unique index if not exists ux_task_dispatch_attempts_dispatch_attempt_id",
        "create unique index if not exists ux_task_execution_attempts_execution_attempt_id",
        "create table if not exists recovery_approval_requests",
        "create table if not exists agent_skill_definitions",
        "create unique index if not exists ux_dispatch_policies_tenant_policy_code",
        "create unique index if not exists ux_agent_runtime_feature_trust_agent_feature",
    ]
    for token in required_tokens:
        require_contains(sql, token)

    hotfix = ROOT / "scripts/db/phase28-live-mybatis-dao-schema-contract-repair.sql"
    if not hotfix.exists():
        print("Missing live hotfix SQL: scripts/db/phase28-live-mybatis-dao-schema-contract-repair.sql")
        sys.exit(1)
    hotfix_text = hotfix.read_text()
    for token in required_tokens[:8]:
        require_contains(hotfix_text, token)

    print("Stage 28 MyBatis DAO schema contract verified.")


if __name__ == "__main__":
    main()
