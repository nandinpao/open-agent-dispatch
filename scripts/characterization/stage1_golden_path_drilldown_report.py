#!/usr/bin/env python3
"""Build a Stage 8-F0c drilldown report from Stage 1 characterization evidence.

This report is deliberately generated even when Stage 1 strict fails. It turns
coarse blockers such as NO_ACTIVE_FLOW_RULE/SERVICE_SCOPE/NO_CANDIDATE into a
first actionable failure bucket: Flow save, rule activation, condition mismatch,
Flow-Agent persistence, runtime lookup, delivery/ACK/result, or legacy blocker
leakage.
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
REPORT_DIR = ROOT / ".ci-output" / "stage1-characterization"
LATEST = REPORT_DIR / "latest.json"
DRILLDOWN_JSON = REPORT_DIR / "stage1-golden-path-drilldown.json"
DRILLDOWN_MD = REPORT_DIR / "stage1-golden-path-drilldown.md"

SCENARIO_HINTS = {
    "T1-02": "No-Capability Golden Path",
    "T1-03": "Explicit-Capability Golden Path",
    "T1-04": "No matching Flow fail-closed",
    "T1-05": "Offline Agent diagnosis",
    "T1-06": "Tenant isolation",
    "T1-10": "Missing tenant contract",
}

FAILURE_TO_STAGE = {
    "FLOW_CREATE_REJECTED_AGENT_NOT_FOUND": "Stage 8-F0d Agent profile bootstrap / Flow create precondition",
    "FLOW_CREATE_REJECTED_TENANT_CONTEXT_REQUIRED": "Stage 2 Tenant contract / Stage 1 Flow create request",
    "FLOW_CREATE_REJECTED_BAD_REQUEST": "Stage 1 Flow create precondition / Stage 3 aggregate validation",
    "FLOW_CREATE_REJECTED_INTERNAL_ERROR": "Stage 3 Flow aggregate API error handling",
    "FLOW_DETAIL_REJECTED_AGENT_NOT_FOUND": "Stage 8-F0d Agent profile bootstrap / Flow detail precondition",
    "FLOW_DETAIL_REJECTED_TENANT_CONTEXT_REQUIRED": "Stage 2 Tenant contract / Flow detail request",
    "FLOW_NOT_VISIBLE_AFTER_SAVE": "Stage 3 Flow aggregate API / tenant contract",
    "FLOW_RULE_NOT_PERSISTED": "Stage 3 Flow-owned Rule persistence",
    "NO_ACTIVE_EXTERNAL_FLOW_RULE_AFTER_SAVE": "Stage 3 Flow-owned Rule activation",
    "RULE_EVENT_CONDITION_MISMATCH": "Stage 1 characterization setup or Stage 3 rule condition mapping",
    "FLOW_AGENT_ASSIGNMENT_NOT_PERSISTED": "Stage 3 Flow-Agent assignment persistence",
    "REQUIRED_CAPABILITY_NOT_PERSISTED": "Stage 3 required Capability persistence",
    "AGENT_RUNTIME_NOT_CONNECTED": "Stage 1 Agent bootstrap / runtime connectivity",
    "RUNTIME_LOOKUP_OR_ASSIGNMENT_DID_NOT_USE_FLOW_AGGREGATE": "Stage 1 Runtime lookup / Stage 3 repository alignment",
    "DELIVERY_ACK_RESULT_NOT_COMPLETED": "Stage 1 Netty delivery / Agent ACK / result callback",
    "LEGACY_BLOCKER_LEAKED_IN_STANDARD_EVIDENCE": "Stage 7 Legacy blocker isolation",
}


def read_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def scenario_key(name: str) -> str:
    return str(name or "").split(" ", 1)[0]


def json_dump(data: Any) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True)


def first_failure_for(item: dict[str, Any]) -> str:
    actual = item.get("actual") or {}
    if isinstance(actual, dict) and actual.get("firstFailure"):
        return str(actual["firstFailure"])
    blockers = set(item.get("blockers") or [])
    if "SERVICE_SCOPE" in blockers or "ASSIGNMENT_PROFILE" in blockers or "TASK_SCOPE" in blockers:
        return "LEGACY_BLOCKER_LEAKED_IN_STANDARD_EVIDENCE"
    if "NO_ACTIVE_FLOW_RULE" in blockers:
        return "NO_ACTIVE_FLOW_RULE_WITHOUT_FLOW_DRILLDOWN"
    if "NO_CANDIDATE" in blockers:
        return "NO_CANDIDATE_WITHOUT_FLOW_DRILLDOWN"
    if item.get("status") == "EXECUTION_ERROR":
        return "EXECUTION_ERROR"
    return "UNKNOWN_STAGE1_FAILURE"


def main() -> None:
    data = read_json(LATEST)
    scenarios = data.get("scenarios") or []
    failed = [item for item in scenarios if not item.get("passed")]
    golden = [item for item in scenarios if scenario_key(item.get("name", "")) in {"T1-02", "T1-03"}]
    failed_golden = [item for item in golden if not item.get("passed")]
    first = failed_golden[0] if failed_golden else (failed[0] if failed else None)
    first_failure = first_failure_for(first or {}) if first else None
    rec_stage = FAILURE_TO_STAGE.get(first_failure or "", "Stage 1 / Stage 3 drilldown")

    summary = {
        "stage": data.get("stage"),
        "mode": data.get("mode"),
        "runId": data.get("runId"),
        "releaseReady": False,
        "scenarioTotal": len(scenarios),
        "failedScenarioCount": len(failed),
        "goldenPathPassed": bool(golden) and all(item.get("passed") for item in golden),
        "failedGoldenPathCount": len(failed_golden),
        "firstFailedScenario": first.get("name") if first else None,
        "firstFailure": first_failure,
        "recommendedRollbackStage": rec_stage,
        "requiredNextEvidence": [
            "flowDiagnostics.flowVisible",
            "flowDiagnostics.expectedRuleObserved",
            "flowDiagnostics.ruleConditionMatchesEvent",
            "flowDiagnostics.expectedAgentObserved",
            "runtime.connected",
            "assignmentId / assignmentCreated",
            "deliveryStatus / ackStatus / resultStatus / taskStatus",
            "blockerCategories.legacyLeakInStandardEvidence=false",
        ],
        "failedScenarios": [],
    }

    for item in failed:
        actual = item.get("actual") if isinstance(item.get("actual"), dict) else {}
        flow_diag = actual.get("flowDiagnostics") if isinstance(actual, dict) else None
        blocker_categories = actual.get("blockerCategories") if isinstance(actual, dict) else None
        summary["failedScenarios"].append({
            "name": item.get("name"),
            "status": item.get("status"),
            "blockers": item.get("blockers") or [],
            "firstFailure": actual.get("firstFailure") if isinstance(actual, dict) else first_failure_for(item),
            "taskId": actual.get("taskId") if isinstance(actual, dict) else None,
            "flowId": actual.get("flowId") if isinstance(actual, dict) else None,
            "ruleId": actual.get("ruleId") if isinstance(actual, dict) else None,
            "agentId": actual.get("agentId") if isinstance(actual, dict) else None,
            "createFlowStatus": actual.get("createFlowStatus") if isinstance(actual, dict) else None,
            "createFlowApi": actual.get("createFlowApi") if isinstance(actual, dict) else None,
            "createFlow": actual.get("createFlow") if isinstance(actual, dict) else None,
            "flowDiagnostics": flow_diag,
            "blockerCategories": blocker_categories,
            "error": item.get("error"),
        })

    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    DRILLDOWN_JSON.write_text(json_dump(summary) + "\n", encoding="utf-8")

    lines = [
        "# Stage 8-F0c Stage 1 / Stage 3 Golden Path Drilldown",
        "",
        f"- Stage: `{summary['stage']}`",
        f"- Mode: `{summary['mode']}`",
        f"- Run ID: `{summary['runId']}`",
        f"- Golden Path passed: `{str(summary['goldenPathPassed']).lower()}`",
        f"- Failed Golden Path scenarios: `{summary['failedGoldenPathCount']}`",
        f"- First failed scenario: `{summary['firstFailedScenario']}`",
        f"- First failure bucket: `{summary['firstFailure']}`",
        f"- Recommended rollback stage: `{summary['recommendedRollbackStage']}`",
        "",
        "## Failed scenarios",
        "",
    ]
    for item in summary["failedScenarios"]:
        lines.extend([
            f"### {item['name']}",
            "",
            f"- Status: `{item['status']}`",
            f"- Blockers: `{', '.join(item['blockers']) or 'none'}`",
            f"- First failure: `{item['firstFailure']}`",
            f"- Task ID: `{item['taskId']}`",
            f"- Flow ID: `{item['flowId']}`",
            f"- Rule ID: `{item['ruleId']}`",
            f"- Agent ID: `{item['agentId']}`",
            "",
        ])
        create_api = item.get("createFlowApi") or {}
        if create_api:
            lines.extend([
                "Flow create API:",
                "",
                "```json",
                json_dump({
                    "httpStatus": item.get("createFlowStatus"),
                    "api": create_api,
                    "response": item.get("createFlow"),
                }),
                "```",
                "",
            ])
        flow_diag = item.get("flowDiagnostics") or {}
        if flow_diag:
            lines.extend([
                "Flow aggregate diagnosis:",
                "",
                "```json",
                json_dump({
                    "likelyFailure": flow_diag.get("likelyFailure"),
                    "flowVisible": flow_diag.get("flowVisible"),
                    "flowActive": flow_diag.get("flowActive"),
                    "ruleCount": flow_diag.get("ruleCount"),
                    "activeExternalRuleCount": flow_diag.get("activeExternalRuleCount"),
                    "expectedRuleObserved": flow_diag.get("expectedRuleObserved"),
                    "ruleConditionMatchesEvent": flow_diag.get("ruleConditionMatchesEvent"),
                    "flowAgentAssignmentCount": flow_diag.get("flowAgentAssignmentCount"),
                    "expectedAgentObserved": flow_diag.get("expectedAgentObserved"),
                    "requiredSkillCount": flow_diag.get("requiredSkillCount"),
                    "expectedSkillObserved": flow_diag.get("expectedSkillObserved"),
                    "ruleCondition": flow_diag.get("ruleCondition"),
                    "eventCondition": flow_diag.get("eventCondition"),
                    "observedMatchedRule": flow_diag.get("observedMatchedRule"),
                    "observedAgents": flow_diag.get("observedAgents"),
                }),
                "```",
                "",
            ])
        blocker_categories = item.get("blockerCategories") or {}
        if blocker_categories:
            lines.extend([
                "Blocker categories:",
                "",
                "```json",
                json_dump(blocker_categories),
                "```",
                "",
            ])
    lines.extend([
        "## Completion condition for Stage 8-F0c",
        "",
        "Stage 8-F0c is complete when this report can identify whether the first blocker is Flow save, Rule activation, Rule/Event condition mismatch, Flow-Agent persistence, Runtime lookup, Agent connectivity, delivery/ACK/result, or Legacy blocker leakage.",
    ])
    DRILLDOWN_MD.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Stage 8-F0c drilldown report: {DRILLDOWN_MD}")
    print(json.dumps({
        "goldenPathPassed": summary["goldenPathPassed"],
        "firstFailure": summary["firstFailure"],
        "recommendedRollbackStage": summary["recommendedRollbackStage"],
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
