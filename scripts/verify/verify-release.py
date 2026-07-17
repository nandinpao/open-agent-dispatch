#!/usr/bin/env python3
"""Single standard OpenDispatch verification entrypoint.

`make verify` is the only supported Makefile verification command. This script
keeps active production, governance, recovery, and contract checks as release blockers.
P11 archives older implementation-marker checks that require decommissioned source-specific
fixtures or Legacy control behavior.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

CHECKS = [
    ("P1-A OpenTelemetry dependency policy", "verify-observability-dependency-policy.py"),
    ("P1-B OTLP and OpenTelemetry Collector configuration", "verify-p1b-otlp-collector-config.py"),
    ("P2-A HTTP request context and business observation", "verify-p2a-http-observation.py"),
    ("P2-B Core business observation consolidation", "verify-p2b-core-business-observation.py"),
    ("P3-A Spring-managed async context propagation", "verify-p3a-spring-managed-context-propagation.py"),
    ("P3-B manual executor and outbound HTTP context propagation", "verify-p3b-manual-executor-outbound-http.py"),
    ("P3-C Agent protocol trace propagation and EventLoop isolation", "verify-p3c-agent-protocol-trace-propagation.py"),
    ("P4-A Core human authentication and server-side session foundation", "verify-p4a-core-admin-authentication.py"),
    ("P4-B Admin UI Core cookie-session authentication and workspace authority", "verify-p4b-admin-ui-core-authentication.py"),
    ("P4-C authentication final convergence and session governance", "verify-p4c-authentication-final-convergence.py"),
    ("P5-A production OpenTelemetry Collector hardening", "verify-p5a-production-otel-hardening.py"),
    ("P0-A SLF4J direct dependency policy", "verify-slf4j-direct-dependencies.py"),
    ("P0 zero-special-case dispatch architecture freeze", "verify-p0-zero-special-case-dispatch.py"),
    ("P1 generic dispatch governance model", "verify-p1-generic-dispatch-governance-model.py"),
    ("P5 runtime hardcode and legacy fallback removal", "verify-p5-runtime-hardcode-removal.py"),
    ("P7 Dispatch Governance Admin UI", "verify-p7-dispatch-governance-admin-ui.py"),
    ("P8 Action Grant and effectful Task workflow", "verify-p8-action-grant-workflow.py"),
    ("P9 effectful Action runtime integration", "verify-p9-effectful-action-runtime-integration.py"),
    ("P0 production release gate", "verify-p0-production-release-gate.py"),
    ("P1 callback inbox / dispatch ledger query hardening", "verify-p1-callback-ledger-query-hardening.py"),
    ("P2 issue tracking idempotency hardening", "verify-p2-issue-tracking-idempotency.py"),
    ("P3 production security readiness", "verify-p3-production-security-readiness.py"),
    ("P4 observability SLO hardening", "verify-p4-observability-slo-hardening.py"),
    ("remediation approval workflow", "verify-p6-remediation-workflow.py"),
    ("remediation persistence", "verify-p7-remediation-persistence.py"),
    ("remediation guarded execution", "verify-p8-remediation-execution.py"),
    ("remediation action idempotency", "verify-p9-remediation-idempotency.py"),
    ("remediation execution lease", "verify-p10-remediation-execution-lease.py"),
    ("remediation stale lease recovery", "verify-p11-stale-lease-recovery.py"),
    ("remediation metrics and alerting", "verify-p12-remediation-metrics-alerting.py"),
    ("release packaging and external CI policy", "verify-p15-release-automation.py"),
    ("on-prem/offline release readiness", "verify-p16-offline-release-readiness.py"),
    ("release operations safety", "verify-p17-release-operations.py"),
    ("model boundary consolidation", "verify-p18-model-boundaries.py"),
    ("standard API response contract", "verify-p19-api-response-contract.py"),
    ("API contract and frontend type alignment", "verify-p20-api-contract-alignment.py"),
    ("frontend/backend endpoint contract", "verify-phase-b-api-endpoint-contract.py"),
    ("Admin UI information architecture", "verify-phase-c-admin-ui-information-architecture.py"),
    ("production security fail-fast", "verify-phase-d-production-security-fail-fast.py"),
    ("API contract runtime acceptance harness", "verify-p21-api-runtime-acceptance.py"),
    ("Docker image version policy", "verify-p22-docker-image-policy.py"),
    ("P28 stabilization and verification governance", "verify-p28-stabilization.py"),
    ("P0-1 R12.14 / V108 baseline validation", "verify-p0-1-r12-14-v108-baseline.py"),
    ("P0-2 Flow Rule dispatchability validation", "verify-p0-2-flow-rule-dispatchability.py"),
    ("P2 integration chaos hardening", "verify-p2-integration-chaos-hardening.py"),
    ("Phase 3C dispatch readiness beginner API/UI", "verify-phase3c-dispatch-readiness.py"),
    ("Phase 3D beginner dispatch workflow UX", "verify-phase3d-beginner-dispatch-ux.py"),
]

# P11 decommissioned historical marker gates that assert removed source-specific
# recipes, fixture seeding, Legacy Skill UI, or pre-authoritative ENFORCE behavior.
# The scripts remain in the repository for historical audit, but are intentionally
# not release blockers for the zero-special-case authoritative architecture.
DECOMMISSIONED_LEGACY_CHECKS = [
    ("Phase 3E-P0 decision-oriented admin UI", "verify-phase3e-p0-decision-ui.py"),
    ("Phase 3E-P1 lifecycle/timeline separation", "verify-phase3e-p1-lifecycle-timeline.py"),
    ("Phase 3E-P2 skill/governance/runtime/task relationship", "verify-phase3e-p2-skill-governance-task.py"),
    ("Phase 3E-P2.2 dispatch recipe entry", "verify-phase3e-p2-2-dispatch-recipe.py"),
    ("Phase 3E-P3 guided dispatch recipe wizard", "verify-phase3e-p3-guided-dispatch-recipe.py"),
    ("Phase 3F dispatch recipe backend model", "verify-phase3f-dispatch-recipe-backend.py"),
    ("Phase 3G-P1 agent worker callback simulation", "verify-phase3g-p1-agent-worker-callback.py"),
    ("Phase 3G-P2 gateway node recent task correlation", "verify-phase3g-p2-node-task-correlation.py"),
    ("Phase 3G-P0 rejected/disconnect runtime semantics", "verify-phase3g-p0-rejected-disconnect-semantics.py"),
    ("Phase 3G-P3 dispatch recipe E2E test flow", "verify-phase3g-p3-dispatch-recipe-e2e.py"),
    ("Phase 3H-P0 callback truth architecture", "verify-phase3h-p0-callback-truth-architecture.py"),
    ("Phase 3H-P1 durable dispatch ledger", "verify-phase3h-p1-dispatch-ledger.py"),
    ("Phase 3H-P2 durable callback inbox", "verify-phase3h-p2-callback-inbox.py"),
    ("Phase 3H-P3 agent reconnect replay", "verify-phase3h-p3-agent-reconnect-replay.py"),
    ("Phase 3H-P4 dynamic topology recovery", "verify-phase3h-p4-dynamic-topology.py"),
    ("Phase 3H-P4.1 dynamic topology acceptance", "verify-phase3h-p4-1-dynamic-topology-acceptance.py"),
    ("Phase 3H-P4.2 agent worker reality", "verify-phase3h-p4-2-agent-worker-reality.py"),
    ("Phase E-P0 scenario-first dispatch management", "verify-phase-e-p0-scenario-first-management.py"),
    ("Phase E-P1 Agent / Skill management simplification", "verify-phase-e-p1-agent-skill-management-simplification.py"),
    ("Phase E-P2 Issue Tracking management", "verify-phase-e-p2-issue-tracking-management.py"),
    ("P2-R Dispatch Contract integrity and release gate", "verify-p2r-dispatch-contract-release-gate.py"),
    ("P3-M ENFORCE runtime acceptance and release cutover", "verify-p3m-enforce-runtime-cutover.py"),
    ("P3-N runtime fixture seeding and full ENFORCE acceptance automation", "verify-p3n-full-enforce-automation.py"),
    ("P3-O ENFORCE default hardening and legacy runtime removal", "verify-p3o-enforce-hardening.py"),
    ("P3-P production ENFORCE observability and post-cutover operations", "verify-p3p-production-observability.py"),
]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def run_check(index: int, total: int, description: str, script_name: str) -> None:
    script = ROOT / "scripts" / "verify" / script_name
    if not script.is_file():
        fail(f"Missing release verification script: {script.relative_to(ROOT)}")

    print(f"==> [{index}/{total}] {description}", flush=True)
    result = subprocess.run([sys.executable, str(script)], cwd=ROOT)
    if result.returncode != 0:
        fail(f"Verification check failed: {description}")


def main() -> int:
    print("OpenDispatch verification", flush=True)
    for index, (description, script_name) in enumerate(CHECKS, start=1):
        run_check(index, len(CHECKS), description, script_name)

    print("OpenDispatch verification passed.", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
