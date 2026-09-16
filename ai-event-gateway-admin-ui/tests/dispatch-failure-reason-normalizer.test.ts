import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { normalizeOperatorDispatchFailureReason } from "../lib/dispatch-evidence/operatorFailureReasons";
import { parseDispatchUserFacingError } from "../lib/dispatch-readiness/dispatchUserFacingError";
import { buildDispatchOperatorActions } from "../lib/dispatch-readiness/dispatchOperatorActions";

describe("operator-facing dispatch failure normalization", () => {
  it("maps NO_CANDIDATE to a human-readable message and actions", () => {
    const reason = normalizeOperatorDispatchFailureReason({
      latestDecision: {
        decisionId: "decision-1",
        taskId: "task-1",
        status: "NO_CANDIDATE",
        candidates: [],
      },
      rawRequirements: ["INCIDENT_ANALYSIS"],
      effectiveCapabilities: ["MES_ALARM_TRIAGE"],
    });

    assert.equal(reason.code, "NO_MATCHING_AGENT");
    assert.equal(reason.title, "No eligible Agent was found in the resolved Agent Pool");
    assert.match(reason.message, /Agent Pool/i);
    assert.ok(reason.actions.some((action) => action.href === "/agents"));
  });

  it("keeps capability observations diagnostic and reports the Current no-candidate reason", () => {
    const reason = normalizeOperatorDispatchFailureReason({
      latestDecision: {
        decisionId: "decision-2",
        taskId: "task-1",
        status: "NO_CANDIDATE",
      },
      effectiveCapabilities: ["CAP_LOT_TRACE"],
      runtimeCapabilities: [],
    });

    assert.equal(reason.code, "NO_MATCHING_AGENT");
    assert.match(reason.message, /Agent Pool/i);
    assert.match(reason.nextAction, /Source Flow|Agent Pool/i);
  });


  it("prioritizes missing runtime binding over generic eligibility waiting", () => {
    const reason = normalizeOperatorDispatchFailureReason({
      selectedAgentId: "agent-cluster-node-001-002",
      effectiveCapabilities: ["MES_LOT_TRACE"],
      runtimeCapabilities: ["MES_LOT_TRACE"],
      setupReadiness: {
        agentId: "agent-cluster-node-001-002",
        ready: false,
        status: "INCOMPLETE",
        blockingReasons: ["RUNTIME_BINDING_ACTIVE"],
        checks: [],
      },
    });

    assert.equal(reason.code, "RUNTIME_BINDING_MISSING");
    assert.match(reason.message, /runtime binding/i);
    assert.ok(reason.actions.some((action) => action.href?.includes("/agents/agent-cluster-node-001-002")));
  });

  it("does not block only because runtime capability observations are empty", () => {
    const reason = normalizeOperatorDispatchFailureReason({
      selectedAgentId: "agent-cluster-node-001-002",
      effectiveCapabilities: ["MES_ALARM_TRIAGE"],
      runtimeCapabilities: [],
    });

    assert.notEqual(reason.code, "RUNTIME_CAPABILITY_MISSING");
    assert.doesNotMatch(reason.nextAction, /OPENSOCKET_AGENT_CAPABILITIES/);
  });

  it("reports a ready assignment when request is eligible", () => {
    const reason = normalizeOperatorDispatchFailureReason({
      selectedAgentId: "agent-cluster-node-001-002",
      latestRequest: {
        dispatchRequestId: "dispatch-1",
        taskId: "task-1",
        agentId: "agent-cluster-node-001-002",
        status: "DISPATCHED",
        eligibilityStatus: "ELIGIBLE",
      },
    });

    assert.equal(reason.code, "DISPATCH_ASSIGNMENT_READY");
    assert.equal(reason.tone, "success");
  });

  it("normalizes legacy technical codes for DecisionHeader and operator actions", () => {
    const parsed = parseDispatchUserFacingError("NO_CANDIDATE");
    assert.equal(parsed.code, "NO_CANDIDATE");
    assert.match(parsed.message, /eligible Agent.*Agent Pool/i);
    assert.match(parsed.nextAction ?? "", /Source Flow|Agent Pool/i);

    const actions = buildDispatchOperatorActions(parsed, { taskId: "task-1" });
    assert.ok(
      actions.some((action) => action.href === "/dispatch-flows"),
    );
  });
});
