#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    raise SystemExit(f"[FAIL] {message}")


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        fail(f"{label} missing required fragment: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        fail(f"{label} contains forbidden fragment: {needle}")


def main() -> int:
    mapper_path = "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/agent.assignment/AgentAssignmentDao.xml"
    baseline_path = "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql"
    hotfix_path = "scripts/db/phase25-live-runtime-resource-upsert-conflict-repair.sql"
    doc_path = "docs/PHASE25_RUNTIME_RESOURCE_UPSERT_CONFLICT_REPAIR.md"
    makefile_path = "Makefile"

    mapper = read(mapper_path).lower()
    baseline = read(baseline_path).lower()
    hotfix = read(hotfix_path).lower()
    doc = read(doc_path)
    makefile = read(makefile_path)

    require(mapper, "insert into runtime_resources", mapper_path)
    require(mapper, "on conflict (tenant_id, runtime_id) do update set", mapper_path)
    forbid(mapper, "on conflict (runtime_id) do update set", mapper_path)

    for fragment in [
        "create table if not exists runtime_resources",
        "primary key (tenant_id, runtime_id)",
        "ux_runtime_resources_tenant_runtime",
        "ux_runtime_resources_code",
    ]:
        require(baseline, fragment, baseline_path)
        require(hotfix, fragment if fragment != "primary key (tenant_id, runtime_id)" else fragment, hotfix_path)

    for fragment in [
        "on conflict (tenant_id, runtime_id)",
        "tenant-scoped",
        "phase25-live-runtime-resource-upsert-conflict-repair.sql",
    ]:
        require(doc, fragment, doc_path)

    for fragment in [
        "verify-stage25-runtime-resource-upsert-conflict-repair",
        "phase25-runtime-resource-upsert-conflict-repair",
        "verify-stage25-runtime-resource-upsert-conflict-repair.py",
    ]:
        require(makefile, fragment, makefile_path)

    print("Stage 25 runtime resource upsert conflict repair contract verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
