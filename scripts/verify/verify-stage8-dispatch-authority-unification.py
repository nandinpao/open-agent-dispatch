#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        raise SystemExit(f"missing file: {rel}")
    return path.read_text(encoding="utf-8")


def require(text: str, token: str, label: str) -> None:
    if token not in text:
        raise SystemExit(f"missing {label}: {token}")


def forbid(text: str, token: str, label: str) -> None:
    if token in text:
        raise SystemExit(f"forbidden {label}: {token}")

task_decision = read("ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java")
plan = read("ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingPlan.java")
flow_routing = read("ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java")
resolver = read("ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/GenericDispatchRequirementResolver.java")
jdbc = read("ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java")
app_yml = "\n".join(read(rel) for rel in (
    "ai-event-gateway-core/control-plane-app/src/main/resources/application.yml",
    "ai-event-gateway-core/control-plane-app/src/main/resources/application-dev.yml",
    "ai-event-gateway-core/control-plane-app/src/main/resources/application-local.yml",
    "ai-event-gateway-core/control-plane-app/src/main/resources/application-prod.yml",
))


require(task_decision, "taskAssignmentService.assignIfPossible(saved)", "standard direct assignment service path")
forbid(task_decision, "Task" + "Offer", "removed two-step dispatch service in task decision")

require(plan, "private String capabilityRequirementMode = \"NONE\";", "flow plan default no capability")
require(plan, "private String candidatePoolMode = \"EXPLICIT_FLOW_AGENTS\";", "flow plan explicit agent pool")
forbid(plan, "private String capabilityRequirementMode = \"LEGACY\";", "legacy capability mode default")
forbid(plan, "private String candidatePoolMode = \"LEGACY\";", "legacy candidate pool default")

require(flow_routing, "standardCapabilityRequirementMode", "standard flow requirement mode helper")
require(flow_routing, "return hasRequiredCapability(requiredSkills) ? \"EXPLICIT\" : \"NONE\";", "requestedSkill is not capability")
require(flow_routing, "return \"EXPLICIT_FLOW_AGENTS\";", "standard explicit flow agents")
forbid(flow_routing, "? (blank(skill) ? List.of() : List.of(skill))", "requestedSkill fallback to required capability")
forbid(flow_routing, "? (blank(plan.getRequestedSkill()) ? List.of() : List.of(plan.getRequestedSkill()))", "requestedSkill fallback in repair")

require(resolver, "Stage 8 direct-dispatch requirement resolver", "resolver standard authority comment")
require(resolver, "CandidatePoolMode.EXPLICIT_FLOW_AGENTS", "resolver explicit flow agents")
require(resolver, "STANDARD_FLOW_DIRECT_NO_CAPABILITY_REQUIREMENT_RESOLVED", "resolver no-capability mode")
require(resolver, "STANDARD_FLOW_DIRECT_CAPABILITY_REQUIREMENT_RESOLVED", "resolver explicit capability mode")
require(resolver, "parallelDispatchModelsRemoved", "resolver parallel models removed evidence")
forbid(resolver, "SourceSystemDispatchDefaultRepository", "source default repository in standard resolver")
forbid(resolver, "DispatchOperationProfileRepository", "operation profile repository in standard resolver")
require(resolver, "resolution.setRequiredOperations(List.of());", "resolver no operation requirement in standard path")

require(jdbc, "coalesce(p.capability_requirement_mode, 'NONE')", "jdbc null capability mode default")
require(jdbc, "coalesce(p.candidate_pool_mode, 'EXPLICIT_FLOW_AGENTS')", "jdbc null candidate pool default")
forbid(jdbc, "coalesce(p.capability_requirement_mode, 'LEGACY')", "jdbc legacy capability default")
forbid(jdbc, "coalesce(p.candidate_pool_mode, 'LEGACY')", "jdbc legacy candidate default")

print("Stage 8 dispatch authority unification contract verified.")
