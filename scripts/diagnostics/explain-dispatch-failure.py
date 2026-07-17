#!/usr/bin/env python3
"""Summarize OpenDispatch routing and agent-block evidence from structured logs."""
from __future__ import annotations

import argparse
import gzip
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Iterable

MARKERS = (
    "flow_rule_runtime_repository_no_match",
    "task_created",
    "generic_dispatch_authoritative_no_selection",
    "generic_dispatch_authoritative_completed",
    "agent_assignment_eligibility_blocked",
    "agent_assignment_eligibility_pass",
    "agent_readiness_blocked",
    "agent_readiness_pass",
    "agent_operational_readiness_blocked",
    "agent_operational_readiness_pass",
    "agent_setup_readiness_unavailable",
    "agent_dispatch_eligibility_unavailable",
    "task_dispatch_recovery_claimed",
    "task_dispatch_recovery_skipped",
)


def iter_files(root: Path) -> Iterable[Path]:
    if root.is_file():
        yield root
        return
    for path in root.rglob("*"):
        if path.is_file() and (path.suffix in {".log", ".txt"} or path.name.endswith(".log.gz")):
            yield path


def read_lines(path: Path) -> Iterable[str]:
    opener = gzip.open if path.name.endswith(".gz") else open
    try:
        with opener(path, "rt", encoding="utf-8", errors="replace") as handle:
            yield from handle
    except OSError as exc:
        print(f"WARN unable to read {path}: {exc}", file=sys.stderr)


def field(line: str, name: str) -> str | None:
    match = re.search(rf"(?:^|\s){re.escape(name)}=([^\s]+)", line)
    return match.group(1) if match else None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log_path", type=Path, help="Log file or extracted diagnostics directory")
    parser.add_argument("--task-id", default="", help="Task ID to trace")
    parser.add_argument("--agent-id", default="", help="Agent ID to trace")
    parser.add_argument("--max-lines", type=int, default=120, help="Maximum matching lines to print")
    args = parser.parse_args()

    if not args.log_path.exists():
        print(f"ERROR log path does not exist: {args.log_path}", file=sys.stderr)
        return 2
    if not args.task_id and not args.agent_id:
        print("ERROR provide --task-id and/or --agent-id", file=sys.stderr)
        return 2

    matched: list[tuple[Path, str, str]] = []
    counts: Counter[str] = Counter()
    no_flow = False
    eligibility_seen = False
    recovery_claims = 0

    for path in iter_files(args.log_path):
        for raw in read_lines(path):
            if args.task_id and args.task_id not in raw and not (args.agent_id and args.agent_id in raw):
                continue
            if args.agent_id and args.agent_id not in raw and not (args.task_id and args.task_id in raw):
                continue
            marker = next((m for m in MARKERS if m in raw), "")
            if not marker:
                continue
            line = raw.rstrip()
            matched.append((path, marker, line))
            counts[marker] += 1
            no_flow = no_flow or (
                marker == "flow_rule_runtime_repository_no_match" and "NO_ACTIVE_FLOW_RULE" in line
            ) or (
                marker == "task_created" and "routingPath=FLOW_RULE_REQUIRED_BLOCKED" in line
            )
            eligibility_seen = eligibility_seen or marker.startswith("agent_assignment_eligibility_")
            recovery_claims += int(marker == "task_dispatch_recovery_claimed")

    print("OpenDispatch dispatch diagnostic summary")
    print(f"  logPath: {args.log_path}")
    if args.task_id:
        print(f"  taskId: {args.task_id}")
    if args.agent_id:
        print(f"  agentId: {args.agent_id}")
    print(f"  matchingEvents: {len(matched)}")

    if no_flow:
        print("\nPRIMARY BLOCKER: NO_ACTIVE_FLOW_RULE")
        print("  Routing stopped before agent candidate evaluation.")
        print("  Verify ACTIVE Dispatch Flow/Rule for tenant, sourceSystem, eventStage and event fields.")
    if args.task_id and not eligibility_seen:
        print("\nAGENT EVALUATION: NOT OBSERVED")
        print("  No agent_assignment_eligibility_* event was found for this task.")
        print("  The task may have stopped before candidate discovery, or it was produced before enhanced diagnostics were deployed.")
    if recovery_claims > 1:
        print(f"\nRECOVERY LOOP: {recovery_claims} claims observed")
        print("  Review recovery eligibility for blocked/no-flow tasks to avoid repeated no-op scans.")

    if counts:
        print("\nEVENT COUNTS")
        for marker, count in counts.most_common():
            print(f"  {marker}: {count}")

    print("\nTIMELINE")
    for path, marker, line in matched[: max(args.max_lines, 0)]:
        print(f"[{path.name}] {marker}: {line}")
    if len(matched) > args.max_lines:
        print(f"... {len(matched) - args.max_lines} additional matching events omitted")

    if not matched:
        print("  No matching structured events found.")
        print("  Confirm the bundle contains current Core logs and that enhanced diagnostics are deployed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
