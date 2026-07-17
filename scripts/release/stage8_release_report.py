#!/usr/bin/env python3
"""Build Stage 8 release-ready and failure-map artifacts.

This script is intentionally lightweight and dependency-free so it can run even
when the strict gate fails before Maven, Docker, or Admin UI dependencies are
available. It converts the command log, per-step JSONL results, and the failed
step log into the evidence files needed for Stage 8-F0a failure analysis.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

REQUIRED_EVIDENCE = [
    "Fresh DB Golden Path",
    "No-Capability Golden Path",
    "Explicit-Capability Golden Path",
    "Upgrade DB Golden Path",
    "Multi-Tenant Isolation",
    "Restart Recovery",
    "Browser E2E",
    "No Legacy Runtime Dependency",
    "No Source-Specific Hardcode",
]

STEP_TO_STAGE = [
    ("Toolchain", "Environment / Toolchain"),
    ("Fresh DB Golden Path", "Stage 1 / Stage 3"),
    ("Tenant and PostgreSQL", "Stage 2"),
    ("Flow aggregate", "Stage 3"),
    ("Real Event to Task", "Stage 5"),
    ("Configuration recovery", "Stage 6"),
    ("No Legacy Runtime", "Stage 7"),
    ("PostgreSQL optional filters", "Stage 2"),
    ("Flow aggregate PostgreSQL", "Stage 3"),
    ("Stage 5 Core", "Stage 5"),
    ("Stage 6 Core", "Stage 6"),
    ("Stage 7 Legacy", "Stage 7"),
    ("Upgrade DB Golden Path", "Stage 8 / Upgrade / Browser E2E"),
    ("Legacy inventory", "Stage 7"),
    ("No Source-Specific", "Stage 8-F1"),
    ("Stage 4 beginner", "Stage 4"),
    ("Stage 0", "Stage 0"),
    ("Stage 1 static", "Stage 1"),
    ("Stage 2", "Stage 2"),
    ("Stage 3", "Stage 3"),
    ("Stage 5", "Stage 5"),
    ("Stage 6", "Stage 6"),
    ("Stage 7", "Stage 7"),
    ("Stage 8", "Stage 8"),
]

ERROR_PATTERNS = [
    re.compile(r"\b(ERROR|Error|error|FAILED|Failed|failed|FAILURE|Failure|Exception|Traceback|BUILD FAILURE|401|403|404|500|BadSqlGrammarException|tenantId is required)\b.*"),
]


def read_steps(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    steps: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            steps.append(json.loads(line))
        except json.JSONDecodeError:
            steps.append({"status": "UNPARSEABLE", "raw": line})
    return steps


def relativize(path: str, root: Path) -> str:
    if not path:
        return ""
    p = Path(path)
    try:
        return str(p.resolve().relative_to(root.resolve()))
    except Exception:
        return path


def first_error_snippet(log_file: Path) -> str:
    if not log_file.exists():
        return "Log file was not created."
    lines = log_file.read_text(encoding="utf-8", errors="replace").splitlines()
    for idx, line in enumerate(lines):
        if any(pattern.search(line) for pattern in ERROR_PATTERNS):
            start = max(0, idx - 3)
            end = min(len(lines), idx + 8)
            return "\n".join(lines[start:end])[-4000:]
    if not lines:
        return "Log file is empty."
    return "\n".join(lines[-40:])[-4000:]


def recommended_stage(step: str) -> str:
    for token, stage in STEP_TO_STAGE:
        if token in step:
            return stage
    return "Stage 8-F0a / Release Gate Runner"


def write_json(path: Path, data: Any) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--mode", required=True)
    parser.add_argument("--started-at", required=True)
    parser.add_argument("--completed-at", required=True)
    parser.add_argument("--release-ready", required=True, choices=["true", "false"])
    parser.add_argument("--steps-file", required=True)
    parser.add_argument("--failed-step", default="")
    parser.add_argument("--exit-code", type=int, default=0)
    parser.add_argument("--log-file", default="")
    parser.add_argument("--command-log", required=True)
    args = parser.parse_args()

    out = Path(args.output_dir)
    root = out.parents[1]
    out.mkdir(parents=True, exist_ok=True)
    steps = read_steps(Path(args.steps_file))
    release_ready = args.release_ready == "true"
    failed_step = args.failed_step
    failed_log = Path(args.log_file) if args.log_file else None
    failed_step_record = next((step for step in steps if step.get("step") == failed_step), None)
    failed_command = (failed_step_record or {}).get("command", "")
    first_error = first_error_snippet(failed_log) if failed_log else ""
    rec_stage = recommended_stage(failed_step) if failed_step else ""

    stage1_drilldown = root / ".ci-output" / "stage1-characterization" / "stage1-golden-path-drilldown.md"
    stage1_drilldown_json = root / ".ci-output" / "stage1-characterization" / "stage1-golden-path-drilldown.json"

    failures = {
        "releaseReady": release_ready,
        "mode": args.mode,
        "failedStep": failed_step,
        "command": failed_command,
        "exitCode": args.exit_code,
        "logFile": relativize(str(failed_log), root) if failed_log else "",
        "firstErrorSnippet": first_error,
        "recommendedRollbackStage": rec_stage,
        "steps": steps,
    }
    if stage1_drilldown.exists():
        failures["stage1Drilldown"] = relativize(str(stage1_drilldown), root)
    if stage1_drilldown_json.exists():
        failures["stage1DrilldownJson"] = relativize(str(stage1_drilldown_json), root)

    if not failed_step:
        failures["message"] = "No failed step captured. Dry-run intentionally keeps releaseReady=false; strict success sets releaseReady=true."

    release_report = {
        "stage": "Stage 8 - Release Gate",
        "mode": args.mode,
        "releaseReady": release_ready,
        "startedAt": args.started_at,
        "completedAt": args.completed_at,
        "requiredEvidence": REQUIRED_EVIDENCE,
        "commandLog": relativize(args.command_log, root),
        "stepsFile": relativize(args.steps_file, root),
        "failures": relativize(str(out / "failures.json"), root),
        "failureMap": relativize(str(out / "failure-map.md"), root),
        "steps": steps,
    }

    write_json(out / "failures.json", failures)
    write_json(out / "release-ready.json", release_report)

    md_lines = [
        "# Stage 8 Release Gate Result",
        "",
        f"- Mode: `{args.mode}`",
        f"- Release ready: `{str(release_ready).lower()}`",
        f"- Started at: `{args.started_at}`",
        f"- Completed at: `{args.completed_at}`",
        f"- Command log: `{relativize(args.command_log, root)}`",
        f"- Steps file: `{relativize(args.steps_file, root)}`",
        "",
        "## Required evidence",
        "",
    ]
    md_lines.extend(f"- {item}" for item in REQUIRED_EVIDENCE)
    md_lines.extend(["", "## Step results", ""])
    if steps:
        md_lines.append("| Step | Status | Exit code | Log |")
        md_lines.append("|---|---:|---:|---|")
        for step in steps:
            md_lines.append(
                f"| {step.get('step', '')} | {step.get('status', '')} | {step.get('exitCode', '')} | `{relativize(step.get('logFile', ''), root)}` |"
            )
    else:
        md_lines.append("No step results were captured.")
    (out / "release-ready.md").write_text("\n".join(md_lines) + "\n", encoding="utf-8")

    failure_lines = [
        "# Stage 8 Failure Map",
        "",
        f"- Release ready: `{str(release_ready).lower()}`",
        f"- Mode: `{args.mode}`",
    ]
    if failed_step:
        failure_lines.extend([
            f"- Failed step: `{failed_step}`",
            f"- Command: `{failed_command}`",
            f"- Exit code: `{args.exit_code}`",
            f"- Log file: `{relativize(str(failed_log), root) if failed_log else ''}`",
            f"- Recommended rollback stage: `{rec_stage}`",
            *( [f"- Stage 1 drilldown: `{relativize(str(stage1_drilldown), root)}`"] if stage1_drilldown.exists() else [] ),
            "",
            "## First error snippet",
            "",
            "```text",
            first_error,
            "```",
        ])
    else:
        failure_lines.extend([
            "- Failed step: none captured",
            "",
            "Dry-run mode does not produce release-ready evidence. Strict mode must pass all release gates before `releaseReady=true`.",
        ])
    failure_lines.extend(["", "## Steps", ""])
    for step in steps:
        failure_lines.append(
            f"- `{step.get('status', '')}` `{step.get('step', '')}` exit={step.get('exitCode', '')} log=`{relativize(step.get('logFile', ''), root)}`"
        )
    (out / "failure-map.md").write_text("\n".join(failure_lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
