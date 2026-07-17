#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        raise AssertionError(f"missing required file: {rel}")
    return path.read_text(encoding="utf-8")


def require(text: str, token: str, label: str) -> None:
    if token not in text:
        raise AssertionError(f"{label}: missing {token!r}")


def forbid(text: str, token: str, label: str) -> None:
    if token in text:
        raise AssertionError(f"{label}: forbidden {token!r}")


bootstrap = read("ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js")
cluster = read("ai-event-gateway-netty/scripts/cluster-run-many-agents.sh")
normalizer = read("ai-event-gateway-core/event-processing/src/main/java/com/opensocket/aievent/core/normalize/EventNormalizer.java")
response = read("ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/decision/EventIntakeDecisionResponse.java")
ui_types = read("ai-event-gateway-admin-ui/lib/types/core.ts")
decision = read("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/decision/DecisionEngine.java")
task_decision = read("ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java")
resolver = read("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolverService.java")
compose = read("deploy/docker-compose.local.yml")
env_example = read("deploy/env/.env.local.example")
e2e = read("scripts/acceptance/stage7-fix2-bootstrap-flow-event-agent-e2e.sh")
self_test = read("scripts/acceptance/stage7-fix2-bootstrap-auth-self-test.mjs")
summary_test = read("ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/decision/DecisionEngineSummaryTest.java")

for token in [
    "CORE_BOOTSTRAP_ADMIN_AUTH_MODE",
    "/api/auth/csrf",
    "/api/auth/login",
    "/api/auth/me",
    "cookieHeader()",
    "CORE_GATEWAY_INTERNAL_TOKEN",
    "claimedCapabilities: []",
    "legacyBootstrap=disabled",
    "Circular environment reference",
    "const resolveValue = (name) =>",
    "deprecatedLocalCredentialToken",
    "envFileCredentialToken",
]:
    require(bootstrap, token, "bootstrap")

for token in [
    "/qualifications",
    "bootstrapAssignmentProfile",
    "autoApproveQualification",
    "ensureBootstrapQualification",
    "adminCapabilitiesForAgent",
    "capabilities: legacyCapabilitiesEnabled",
    "scopes: [scope()]",
]:
    forbid(bootstrap, token, "bootstrap")

require(cluster, "CORE_BOOTSTRAP_ADMIN_AUTH_MODE", "cluster runner")
require(cluster, "legacyBootstrap=disabled", "cluster runner")
for token in [
    "CORE_BOOTSTRAP_ASSIGNMENT_PROFILE=",
    "CORE_BOOTSTRAP_AUTO_APPROVE_QUALIFICATION=",
    "CORE_BOOTSTRAP_LEGACY_CAPABILITIES_ENABLED=",
]:
    forbid(cluster, token, "cluster runner")

for token in [
    "cleanUpperNullable(request.getTargetSystem())",
    "cleanUpperNullable(request.getRequestedSkill())",
    "cleanIdentifierNullable(request.getCorrelationId())",
]:
    require(normalizer, token, "EventNormalizer")
for token in [
    '"NO_TARGET_SYSTEM"',
    '"NO_REQUESTED_SKILL"',
    '"NO_HANDOFF_MODE"',
    '"NO_CORRELATION_ID"',
    '"NO_PARENT_TASK_ID"',
]:
    forbid(normalizer, token, "EventNormalizer")

for token in ["String primaryStatus", "String primaryReasonCode", "String nextAction"]:
    require(response, token, "EventIntakeDecisionResponse")
for token in ["primaryStatus?: string", "primaryReasonCode?: string", "nextAction?: string"]:
    require(ui_types, token, "Admin UI Event intake response type")
for token in ["CREATE_OR_ACTIVATE_DISPATCH_FLOW", "WAITING_CONFIGURATION", "NO_ACTIVE_FLOW_RULE"]:
    require(decision, token, "DecisionEngine")
for token in ["R9 flowRule=", "phase1 persists"]:
    forbid(task_decision + decision, token, "public decision wording")
forbid(resolver, '"NO_CONFIGURED_DISPATCH_CONTRACT"', "TaskCapabilityResolverService")
require(resolver, '"NO_ACTIVE_FLOW_RULE"', "TaskCapabilityResolverService")

require(compose, "CORE_INTERNAL_SECURITY_ENABLED: ${CORE_INTERNAL_SECURITY_ENABLED:-true}", "local compose")
require(compose, "GATEWAY_AGENT_AUTHORIZATION_ENABLED: ${GATEWAY_AGENT_AUTHORIZATION_ENABLED:-true}", "local compose")
require(env_example, "CLUSTER_INTERNAL_TOKEN=local-cluster-token-change-me", "local env")
require(env_example, "AGENT_ONBOARDING_TOKEN=local-agent-onboarding-token-change-me", "local env")
require(env_example, "CORE_BOOTSTRAP_ADMIN_AUTH_MODE=SESSION", "local env")

for token in [
    "cluster-run-many-agents.sh restart",
    "STAGE1_AGENT_ID",
    "run-stage1.sh --strict",
    "unset CORE_BOOTSTRAP_ASSIGNMENT_PROFILE",
    "reference_name",
]:
    require(e2e, token, "Stage 7 Fix2 E2E")
for token in [
    "loginCsrf", "gatewayToken", "legacyMutation", "legacyFields",
    "CORE_GATEWAY_INTERNAL_TOKEN=${CLUSTER_INTERNAL_TOKEN}",
    "canonicalCredentialToken",
]:
    require(self_test, token, "bootstrap auth self-test")
for token in [
    "shouldExposeNoActiveFlowAsWaitingConfiguration",
    "shouldExposeMissingCapabilityAsWaitingConfiguration",
    "shouldExposeRuntimeAndCapacityBlockersWithoutLegacyVocabulary",
    "shouldExposeQueuedDispatchWhenAssignmentAndRequestExist",
]:
    require(summary_test, token, "decision summary TDD")

active_files = [
    "ai-event-gateway-netty/.env",
    "ai-event-gateway-netty/scripts/cluster-run-many-agents.sh",
    "ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js",
    "ai-event-gateway-netty/scripts/cluster-send-task.sh",
    "ai-event-gateway-netty/scripts/cluster-send-task-and-event-test.sh",
    "ai-event-gateway-netty/scripts/start-cluster-3nodes-local-jar.sh",
    "scripts/agents/run-task-worker-agent.sh",
    "scripts/agents/run-manual-mock-agent.sh",
    "scripts/agents/simulate-agent-result-callback.sh",
    "ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts",
]
for rel in active_files:
    text = read(rel)
    forbid(text, "local-cluster-internal-token-change-me", rel)
    if rel != "ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js":
        forbid(text, "local-dev-agent-token-change-me", rel)

if bootstrap.count("local-dev-agent-token-change-me") != 1:
    raise AssertionError("bootstrap must mention the deprecated onboarding token exactly once for compatibility detection")

forbid(read("ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts"),
       "CORE_OPERATOR_TOKEN=", "recipe Workflow bootstrap command")

print("Stage 7 Fix2 bootstrap/auth/response/E2E contract verified.")
