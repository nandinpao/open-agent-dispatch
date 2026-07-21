#!/usr/bin/env python3
"""Verify Phase 8A Current documentation entrypoint governance.

This verifier protects the Current documentation entrypoint without moving the
large historical document set. Phase 8B will perform archive relocation.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_FILES = {
    "README.md": "OpenDispatch",
    "docs/current/README.md": "Current OpenDispatch Documentation",
    "docs/current/api/current-api-catalog.md": "Current API Catalog",
    "docs/current/development/documentation-governance.md": "Documentation Governance",
    "docs/current/development/verification-guide.md": "Current Verification Guide",
    "docs/current/development/full-live-release-gate.md": "Full Live Release Gate",
    "docs/archive/README.md": "Documentation Archive",
    "docs/archive/historical-asset-governance.md": "Historical Asset Governance Plan",
    "docs/adr/README.md": "Architecture Decision Records",
    "docs/releases/README.md": "Releases",
}

CURRENT_FRONTMATTER_FILES = [
    "docs/current/README.md",
    "docs/current/api/current-api-catalog.md",
    "docs/current/development/documentation-governance.md",
    "docs/current/development/verification-guide.md",
    "docs/current/development/full-live-release-gate.md",
    "docs/adr/README.md",
    "docs/releases/README.md",
]

ARCHIVE_FRONTMATTER_FILES = [
    "docs/archive/README.md",
    "docs/archive/historical-asset-governance.md",
]

ROOT_README_REQUIRED = [
    "docs/current/README.md",
    "docs/current/api/current-api-catalog.md",
    "docs/current/development/verification-guide.md",
    "make verify",
    "make verify-e2e",
    "Current Dispatch Model",
]

ROOT_README_FORBIDDEN_PATTERNS = [
    r"verify-phase",
    r"verify-stage",
    r"verify-r\d+",
    r"Phase\s+0",
    r"Phase\s+1",
    r"Phase\s+2",
    r"Phase\s+3",
    r"Phase\s+4",
    r"Phase\s+5",
    r"Phase\s+6",
    r"Phase\s+7",
    r"Stage\s+\d+",
    r"R-series",
]

CURRENT_README_FORBIDDEN_LINK_PATTERNS = [
    r"docs/P\d+_",
    r"docs/R\d+_",
    r"docs/.*STAGE_",
    r"phase\d+-change-log",
    r"phase\d+-modified-files",
    r"development/phase\d+-",
]

POLICY_REQUIRED_PHRASES = [
    "PHASE_*",
    "STAGE_*",
    "P[0-9]*_*",
    "R[0-9]*_*",
    "docs/current/",
    "docs/adr/",
    "docs/releases/",
    "docs/archive/",
]

API_CATALOG_REQUIRED_COLUMNS = [
    "Path",
    "Owner",
    "Lifecycle",
    "Caller",
    "Mutation",
    "Permission boundary",
    "Successor / note",
    "Contract test",
]

API_CATALOG_REQUIRED_PATHS = [
    "/admin/source-systems",
    "/admin/dispatch-flows",
    "/admin/dispatch/simulate",
    "/api/events/intake",
    "/admin/tasks/{taskId}/commands",
    "/admin/tasks/{taskId}/issue-dedup",
]

MAKE_REQUIRED_LINES = [
    "verify-phase8a-current-documentation-entry.py",
]


def fail(message: str) -> None:
    print(f"Phase 8A documentation governance verification failed: {message}", file=sys.stderr)
    sys.exit(1)


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        fail(f"missing required file: {rel}")
    return path.read_text(encoding="utf-8")


def assert_frontmatter(rel: str, expected_status: str) -> None:
    text = read(rel)
    if not text.startswith("---\n"):
        fail(f"{rel} must start with YAML frontmatter")
    end = text.find("\n---", 4)
    if end == -1:
        fail(f"{rel} frontmatter is not closed")
    frontmatter = text[4:end]
    if f"status: {expected_status}" not in frontmatter:
        fail(f"{rel} must contain status: {expected_status}")
    if "owner: dispatch-platform" not in frontmatter:
        fail(f"{rel} must contain owner: dispatch-platform")
    if rel.startswith("docs/archive") and "do-not-implement: true" not in frontmatter:
        fail(f"{rel} archive frontmatter must contain do-not-implement: true")
    if rel.startswith("docs/current") and "phase:" in frontmatter:
        fail(f"{rel} current frontmatter must not use phase metadata")


def main() -> None:
    for rel, title in REQUIRED_FILES.items():
        text = read(rel)
        if title not in text:
            fail(f"{rel} does not contain expected title/marker: {title}")

    for rel in CURRENT_FRONTMATTER_FILES:
        assert_frontmatter(rel, "current")
    for rel in ARCHIVE_FRONTMATTER_FILES:
        assert_frontmatter(rel, "archived")

    root_readme = read("README.md")
    for phrase in ROOT_README_REQUIRED:
        if phrase not in root_readme:
            fail(f"root README missing required current marker: {phrase}")
    for pattern in ROOT_README_FORBIDDEN_PATTERNS:
        if re.search(pattern, root_readme, flags=re.IGNORECASE):
            fail(f"root README must not contain historical verification/product narrative pattern: {pattern}")

    current_readme = read("docs/current/README.md")
    for pattern in CURRENT_README_FORBIDDEN_LINK_PATTERNS:
        if re.search(pattern, current_readme, flags=re.IGNORECASE):
            fail(f"docs/current/README.md must not link to historical phase/stage/r-series files: {pattern}")
    if "Current file frontmatter" not in current_readme:
        fail("docs/current/README.md must document frontmatter rules")

    governance = read("docs/current/development/documentation-governance.md")
    for phrase in POLICY_REQUIRED_PHRASES:
        if phrase not in governance:
            fail(f"documentation governance missing policy phrase: {phrase}")

    archive_plan = read("docs/archive/historical-asset-governance.md")
    for phrase in ["Phase 8B", "docs/archive/phase-series/", "docs/archive/stage-series/", "docs/archive/r-series/", "do-not-implement: true"]:
        if phrase not in archive_plan:
            fail(f"archive governance plan missing: {phrase}")

    api_catalog = read("docs/current/api/current-api-catalog.md")
    for column in API_CATALOG_REQUIRED_COLUMNS:
        if column not in api_catalog:
            fail(f"current API catalog missing column: {column}")
    for path in API_CATALOG_REQUIRED_PATHS:
        if path not in api_catalog:
            fail(f"current API catalog missing path: {path}")

    makefile = read("Makefile")
    for line in MAKE_REQUIRED_LINES:
        if line not in makefile:
            fail(f"Makefile verify-fast must include {line}")

    print("Phase 8A current documentation entrypoint governance verified.")


if __name__ == "__main__":
    main()
