#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require_file(path: str) -> None:
    if not (ROOT / path).is_file():
        print(f"Missing required file: {path}", file=sys.stderr)
        sys.exit(1)


def require(path: str, token: str) -> None:
    text = read(path)
    if token not in text:
        print(f"Missing token in {path}: {token}", file=sys.stderr)
        sys.exit(1)


def forbid(path: str, token: str) -> None:
    text = read(path)
    if token in text:
        print(f"Forbidden token in {path}: {token}", file=sys.stderr)
        sys.exit(1)


def main() -> None:
    service = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java"
    orchestrator = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingOrchestrator.java"
    flow_resolution = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/flow/FlowResolution.java"
    flow_resolver = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/flow/FlowResolver.java"
    rule_resolver = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/flow/RuleResolver.java"
    doc = "docs/current/development/phase3-5-flow-rule-resolver.md"
    changelog = "docs/current/phase3-5-change-log.md"
    files = "docs/current/phase3-5-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"

    for path in (
        service,
        orchestrator,
        flow_resolution,
        flow_resolver,
        rule_resolver,
        doc,
        changelog,
        files,
        "scripts/verify/verify-phase3-5-flow-rule-resolver.py",
    ):
        require_file(path)

    # RoutingDecisionService still exposes FlowResolver facade methods, while
    # RoutingOrchestrator owns the high-level FlowResolution call after Phase 3-7.
    for token in (
        "import com.opensocket.aievent.core.routing.flow.FlowResolver;",
        "FlowResolver flowResolver()",
        "return new FlowResolver(properties, flowRuleRoutingService);",
        "return flowResolver().isFlowRuleTask(task);",
        "return flowResolver().isSourceFlowPoolFirstTask(task);",
        "return flowResolver().decisionSuffix(task);",
    ):
        require(service, token)
    for token in (
        "import com.opensocket.aievent.core.routing.flow.FlowResolution;",
        "FlowResolution flowResolution = service.flowResolver().resolve(task);",
        "task = flowResolution.task();",
        "RoutingPolicy policy = flowResolution.policy();",
    ):
        require(orchestrator, token)

    for token in (
        "import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingPlan;",
        "private TaskRecord applyFlowRuleRuntimeRepair",
        "flowRuleRoutingService.resolve(task)",
        "routing_flow_rule_runtime_repaired taskId=",
        "private RoutingPolicy resolvePolicy",
        "boolean standardPath = \"FLOW_RULE\".equals(path)",
        "plan.isSourceDefaultPool() ? \"SOURCE_FLOW_DEFAULT_POOL\" : \"FLOW_RULE\"",
    ):
        forbid(service, token)

    # FlowResolver is the facade and RuleResolver owns actual runtime repair.
    for token in (
        "public record FlowResolution",
        "RoutingPolicy policy",
        "boolean flowRuleTask",
        "boolean sourceFlowPoolFirstTask",
    ):
        require(flow_resolution, token)

    for token in (
        "public class FlowResolver",
        "private final RuleResolver ruleResolver;",
        "public FlowResolution resolve(TaskRecord task)",
        "TaskRecord repaired = ruleResolver.applyRuntimeRepair(task);",
        "RoutingPolicy policy = flowRuleTask ? RoutingPolicy.FLOW_RULE : RoutingPolicy.MANUAL_REVIEW;",
        "boolean sourceFlowPoolFirstTask = ruleResolver.isSourceFlowPoolFirstTask(repaired);",
        "public String decisionSuffix(TaskRecord task)",
        "public java.util.List<String> requiredSkills(TaskRecord task)",
    ):
        require(flow_resolver, token)

    for token in (
        "public class RuleResolver",
        "private final FlowRuleRoutingService flowRuleRoutingService;",
        "public TaskRecord applyRuntimeRepair(TaskRecord task)",
        "FlowRuleRoutingPlan plan = flowRuleRoutingService.resolve(task);",
        "routing_flow_rule_runtime_repair_not_matched",
        "task.setMatchedFlowId(plan.getFlowId());",
        "task.setMatchedRuleId(plan.getRuleId());",
        "task.setRequestedSkill(plan.getRequestedSkill());",
        "task.setTargetPoolId(firstNonBlank(plan.getTargetPoolId(), task.getTargetPoolId()));",
        "task.setAssignedPoolId(firstNonBlank(plan.getTargetPoolId(), task.getAssignedPoolId()));",
        "plan.isSourceDefaultPool() ? \"SOURCE_FLOW_DEFAULT_POOL\" : \"FLOW_RULE\"",
        "task.setRoutingPolicy(\"FLOW_RULE\");",
        "task.setRequiredCapabilities(List.of());",
        "routing_flow_rule_runtime_repaired",
        "routing_flow_rule_runtime_repair_failed",
        "public boolean isFlowRuleTask(TaskRecord task)",
        "public boolean isSourceFlowPoolFirstTask(TaskRecord task)",
        "public String decisionSuffix(TaskRecord task)",
        "public List<String> requiredSkills(TaskRecord task)",
    ):
        require(rule_resolver, token)

    for token in (
        "Phase 3-5: Extract FlowResolver / RuleResolver",
        "No routing behavior should change" if False else "behavior-preserving",
        "FlowRuleRoutingService.resolve(...)",
        "matchedFlowId / matchedRuleId repair",
        "SOURCE_FLOW_DEFAULT_POOL vs FLOW_RULE routingPath selection",
        "Phase 3-6 should extract evidence and blocker builders",
    ):
        require(doc, token)

    require(changelog, "Behavior changes")
    require(changelog, "None intended")
    require(files, "FlowResolver.java")
    require(files, "RuleResolver.java")
    require(readme, "development/phase3-5-flow-rule-resolver.md")
    require(readme, "make verify-phase3-5-flow-rule-resolver")
    require(makefile, "verify-phase3-5-flow-rule-resolver")
    require(makefile, "verify-phase3-5-flow-rule-resolver.py")

    line_count = len(read(service).splitlines())
    if line_count >= 1671:
        print(f"RoutingDecisionService line count did not decrease from Phase 3-4 baseline: {line_count}", file=sys.stderr)
        sys.exit(1)

    print("Phase 3-5 flow/rule resolver contract verified.")


if __name__ == "__main__":
    main()
