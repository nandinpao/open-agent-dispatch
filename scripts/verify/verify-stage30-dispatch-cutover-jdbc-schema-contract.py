#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
V1 = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql"
JAVA_ROOT = ROOT / "ai-event-gateway-core/database-platform/src/main/java"
HOTFIX = ROOT / "scripts/db/phase30-live-dispatch-cutover-jdbc-schema-repair.sql"

SQL_REF_PATTERNS = [
    r"\bfrom\s+(\w+)",
    r"\bjoin\s+(\w+)",
    r"\binsert\s+into\s+(\w+)",
    r"\bupdate\s+(\w+)",
    r"\bdelete\s+from\s+(\w+)",
]

IGNORED_SQL_ALIASES = {
    # CTE aliases used by JdbcFlowRuleRoutingRepository, not physical schema objects.
    "candidate_rules",
    "rules",
    "flags",
}

IGNORED_TEST_OR_DOC_WORDS = {
    "select",
    "where",
    "values",
    "set",
    "with",
}


def schema_objects(sql: str) -> set[str]:
    tables = set(re.findall(r"create\s+table\s+if\s+not\s+exists\s+(\w+)", sql, re.IGNORECASE))
    views = set(re.findall(r"create\s+or\s+replace\s+view\s+(\w+)", sql, re.IGNORECASE))
    return tables | views


def java_sql_refs() -> dict[str, set[str]]:
    refs: dict[str, set[str]] = {}
    for path in sorted(JAVA_ROOT.rglob("*.java")):
        if "/src/test/" in path.as_posix():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        # Java JDBC repositories keep SQL in text blocks. We intentionally scan
        # all Java sources under database-platform because JDBC SQL does not live
        # in MyBatis XML and must be covered by a separate contract gate.
        flat = re.sub(r"\s+", " ", text)
        for pattern in SQL_REF_PATTERNS:
            for match in re.finditer(pattern, flat, re.IGNORECASE):
                name = match.group(1)
                lowered = name.lower()
                if lowered in IGNORED_SQL_ALIASES or lowered in IGNORED_TEST_OR_DOC_WORDS:
                    continue
                refs.setdefault(name, set()).add(str(path.relative_to(ROOT)))
    return refs


def require_contains(text: str, token: str, source: str) -> None:
    if token not in text:
        print(f"{source} missing required token: {token}")
        sys.exit(1)


def main() -> int:
    sql = V1.read_text(encoding="utf-8")
    objects = schema_objects(sql)
    refs = java_sql_refs()
    missing = sorted((name, sorted(paths)) for name, paths in refs.items() if name not in objects)
    if missing:
        print("Java JDBC SQL references schema objects missing from V1 baseline:")
        for name, paths in missing:
            print(f" - {name}: {paths}")
        return 1

    required_tokens = [
        "create table if not exists dispatch_cutover_policies",
        "primary key (tenant_id, policy_id)",
        "unique (tenant_id, flow_id)",
        "create table if not exists dispatch_cutover_task_decisions",
        "unique (tenant_id, task_id, flow_id)",
        "create table if not exists dispatch_cutover_outcomes",
        "unique (tenant_id, task_id, flow_id, authoritative)",
        "create or replace view dispatch_p10_cutover_readiness as",
        "create table if not exists dispatch_operator_incidents",
        "create table if not exists dispatch_release_artifacts",
        "create or replace view dispatch_p11_enforce_observability_snapshot as",
        "create or replace view dispatch_p11_routing_audit as",
        "create or replace view dispatch_p11_legacy_final_report as",
        "create or replace view dispatch_p11_artifact_retention as",
    ]
    for token in required_tokens:
        require_contains(sql, token, "V1 baseline")

    if not HOTFIX.exists():
        print("Missing live hotfix SQL: scripts/db/phase30-live-dispatch-cutover-jdbc-schema-repair.sql")
        return 1
    hotfix = HOTFIX.read_text(encoding="utf-8")
    for token in required_tokens:
        require_contains(hotfix, token, "Phase 30 live hotfix")

    repo = ROOT / "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/cutover/JdbcDispatchCutoverRepository.java"
    repo_text = repo.read_text(encoding="utf-8")
    for token in [
        "from dispatch_cutover_policies",
        "insert into dispatch_cutover_task_decisions",
        "insert into dispatch_cutover_outcomes",
        "from dispatch_p10_cutover_readiness",
    ]:
        require_contains(repo_text, token, str(repo.relative_to(ROOT)))

    print("Stage 30 dispatch cutover JDBC schema contract verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
