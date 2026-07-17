#!/usr/bin/env python3
"""Append one Stage 8 release-gate step result to a JSONL file."""
from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--step", required=True)
    parser.add_argument("--command", required=True)
    parser.add_argument("--exit-code", required=True, type=int)
    parser.add_argument("--started-at", required=True)
    parser.add_argument("--completed-at", required=True)
    parser.add_argument("--log-file", required=True)
    parser.add_argument("--steps-file", required=True)
    args = parser.parse_args()

    path = Path(args.steps_file)
    path.parent.mkdir(parents=True, exist_ok=True)
    record = {
        "step": args.step,
        "command": args.command,
        "exitCode": args.exit_code,
        "status": "PASS" if args.exit_code == 0 else "FAIL",
        "startedAt": args.started_at,
        "completedAt": args.completed_at,
        "logFile": args.log_file,
    }
    with path.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")


if __name__ == "__main__":
    main()
