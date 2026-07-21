#!/usr/bin/env python3
"""Verify Phase 8B documentation archive relocation and verifier consolidation."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
ARCHIVE = DOCS / "archive"

ROOT_HISTORICAL_PATTERNS = [
    re.compile(r"^ADMIN_UI_STAGE_"),
    re.compile(r"^STAGE"),
    re.compile(r"^PHASE"),
    re.compile(r"^P\d"),
    re.compile(r"^R\d"),
]

CURRENT_HISTORICAL_FILE_PATTERNS = [
    re.compile(r"^phase\d", re.IGNORECASE),
    re.compile(r"^p\d", re.IGNORECASE),
    re.compile(r"^r\d", re.IGNORECASE),
    re.compile(r"^stage\d", re.IGNORECASE),
]

REQUIRED_ARCHIVE_FILES = [
    "docs/catalog.yml",
    "docs/archive/historical-document-inventory.json",
    "docs/archive/historical-document-inventory.md",
    "docs/archive/historical-asset-governance.md",
    "docs/archive/legacy-verification/README.md",
    "docs/archive/phase-series/current-history/development/phase4-6-current-legacy-isolation-release-gate.md",
    "docs/archive/phase-series/current-history/phase4-6-change-log.md",
    "docs/archive/phase-series/current-history/phase4-6-modified-files.md",
]

REQUIRED_ARCHIVE_DIRS = [
    "docs/archive/phase-series",
    "docs/archive/stage-series",
    "docs/archive/r-series",
    "docs/archive/legacy-verification",
]


def fail(message: str) -> None:
    print(f"Phase 8B document archive verification failed: {message}", file=sys.stderr)
    sys.exit(1)


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        fail(f"missing required file: {rel}")
    return path.read_text(encoding="utf-8")


def assert_archived_frontmatter(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if not text.startswith("---\n"):
        fail(f"archived markdown missing YAML frontmatter: {path.relative_to(ROOT)}")
    end = text.find("\n---", 4)
    if end == -1:
        fail(f"archived markdown frontmatter not closed: {path.relative_to(ROOT)}")
    frontmatter = text[4:end]
    required = [
        "status: archived",
        "owner: dispatch-platform",
        "do-not-implement: true",
    ]
    for token in required:
        if token not in frontmatter:
            fail(f"archived markdown missing {token}: {path.relative_to(ROOT)}")


def main() -> None:
    for rel in REQUIRED_ARCHIVE_FILES:
        if not (ROOT / rel).is_file():
            fail(f"missing required archive file: {rel}")
    for rel in REQUIRED_ARCHIVE_DIRS:
        if not (ROOT / rel).is_dir():
            fail(f"missing required archive directory: {rel}")

    # Root docs must no longer contain historical delivery-series assets.
    for item in DOCS.iterdir():
        if item.name in {"archive", "current", "adr", "releases"}:
            continue
        if any(pattern.search(item.name) for pattern in ROOT_HISTORICAL_PATTERNS):
            fail(f"historical delivery asset still lives in root docs: docs/{item.name}")

    # docs/current must no longer contain phase/stage/r-series implementation records by filename.
    for path in (DOCS / "current").rglob("*.md"):
        if any(pattern.search(path.name) for pattern in CURRENT_HISTORICAL_FILE_PATTERNS):
            fail(f"historical phase/stage/r-series file still lives in docs/current: {path.relative_to(ROOT)}")

    inventory = json.loads(read("docs/archive/historical-document-inventory.json"))
    if inventory.get("schema_version") != 1:
        fail("historical inventory schema_version must be 1")
    items = inventory.get("items")
    if not isinstance(items, list) or not items:
        fail("historical inventory must contain archived items")
    if inventory.get("total_archived_items") != len(items):
        fail("historical inventory total_archived_items does not match item count")
    if len(items) < 100:
        fail("Phase 8B inventory is unexpectedly small; archive relocation likely did not run")

    categories = {item.get("category") for item in items}
    for category in {"phase-series", "stage-series", "r-series"}:
        if category not in categories:
            fail(f"historical inventory missing category: {category}")

    for item in items:
        for key in ["original_path", "archived_path", "category", "status", "do_not_implement", "replaced_by"]:
            if key not in item:
                fail(f"inventory item missing {key}: {item}")
        archived = ROOT / item["archived_path"]
        if not archived.exists():
            fail(f"inventory archived_path does not exist: {item['archived_path']}")
        if item["status"] != "archived" or item["do_not_implement"] is not True:
            fail(f"inventory item must be archived/do_not_implement: {item['archived_path']}")
        if item["archived_path"].startswith("docs/current/"):
            fail(f"inventory archived path must not remain under docs/current: {item['archived_path']}")

    # Every archived markdown under the three relocated series must be marked do-not-implement.
    for series in ["phase-series", "stage-series", "r-series"]:
        markdown_files = list((ARCHIVE / series).rglob("*.md"))
        if not markdown_files:
            fail(f"archive series has no markdown files: {series}")
        for path in markdown_files:
            assert_archived_frontmatter(path)

    # Current documentation should link to the inventory, not to historical implementation records.
    current_readme = read("docs/current/README.md")
    for token in ["docs/catalog.yml", "docs/archive/", "not Current product guidance"]:
        if token not in current_readme:
            fail(f"docs/current/README.md missing archive governance marker: {token}")
    if re.search(r"docs/(P\d|PHASE|R\d|STAGE|ADMIN_UI_STAGE)", current_readme):
        fail("docs/current/README.md must not link directly to historical delivery-series paths")

    governance = read("docs/archive/historical-asset-governance.md")
    for token in ["Completed Phase 8B relocation", "docs/catalog.yml", "docs/archive/legacy-verification/README.md"]:
        if token not in governance:
            fail(f"historical asset governance missing token: {token}")

    legacy_verification = read("docs/archive/legacy-verification/README.md")
    for token in ["archive-only", "make archive-verify-current-legacy", "make archive-verify-stage8-release-gate", "make archive-verify-phase32"]:
        if token not in legacy_verification:
            fail(f"legacy verification archive missing token: {token}")

    makefile = read("Makefile")
    if "verify-phase8b-document-archive.py" not in makefile:
        fail("Makefile verify-fast must include verify-phase8b-document-archive.py")
    for token in ["archive-verify-current-legacy", "archive-verify-stage8-release-gate", "archive-verify-phase32"]:
        if token not in makefile:
            fail(f"Makefile missing archive-only target: {token}")

    help_block = makefile.split("check-project-layout:", 1)[0]
    for forbidden in ["archive-verify-current-legacy", "archive-verify-stage8-release-gate", "archive-verify-phase32", "verify-phase32"]:
        if forbidden in help_block:
            fail(f"make help block must not advertise archive/historical target: {forbidden}")

    phase4_verifier = read("scripts/verify/verify-phase4-release-gate.py")
    for token in ["docs/archive/phase-series/current-history", "docs/catalog.yml", "legacy-verification/README.md"]:
        if token not in phase4_verifier:
            fail(f"Phase 4 historical verifier must read archived docs after Phase 8B: {token}")

    print("Phase 8B documentation archive relocation and verifier consolidation verified.")


if __name__ == "__main__":
    main()
