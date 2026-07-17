#!/usr/bin/env python3
"""Verify OpenDispatch model-boundary consolidation.

The project intentionally centralizes reusable DAO/PO/DTO/domain/API contract
classes in these model modules:

* Core: ai-event-gateway-core/data-model
* Netty: ai-event-gateway-netty/gateway-model

Runtime services may still own service-local execution objects and Spring
components, but reusable API/domain beans must not drift back into controller,
transport, or orchestration modules.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

CORE_MODEL = "ai-event-gateway-core/data-model"
CORE_KERNEL = "ai-event-gateway-core/kernel"
NETTY_MODEL = "ai-event-gateway-netty/gateway-model"

MODEL_NAME_RE = re.compile(
    r"(Dto|DTO|Request|Response|Payload|Snapshot|Record|Command|View|Summary|Metadata|Envelope)\.java$"
)
DAO_PO_RE = re.compile(r"(Dao|DAO|Po|PO)\.java$")

# Explicitly allowed non-model classes. These are service-local runtime objects,
# external deployment contracts, or kernel primitives rather than reusable
# model contracts.
ALLOWED_NON_MODEL = {
    # Netty runtime/component-local exceptions.
    "ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/callback/TaskCallbackRelayMetrics.java",
    # Kernel primitives belong in kernel by design.
    "ai-event-gateway-core/kernel/src/main/java/com/opensocket/aievent/core/kernel/persistence/ClaimRequest.java",
    "ai-event-gateway-core/kernel/src/main/java/com/opensocket/aievent/core/kernel/persistence/LeaseRenewalRequest.java",
    # service-contracts is a dedicated external-service boundary module.
    "ai-event-gateway-core/service-contracts/src/main/java/com/opensocket/aievent/service/adapter/AdapterWorkerCompletionRequest.java",
    "ai-event-gateway-core/service-contracts/src/main/java/com/opensocket/aievent/service/adapter/AdapterWorkerFailureRequest.java",
    "ai-event-gateway-core/service-contracts/src/main/java/com/opensocket/aievent/service/adapter/AdapterWorkerHeartbeatRequest.java",
}
# P18-B/P18-C: Adapter Action reusable domain/executor contract classes
# and repository ports must live in Core data-model. adapter-action should
# retain only orchestration, repository implementations, executor
# implementations, and Spring runtime wiring.
REQUIRED_CORE_MODEL_CLASSES = {
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/AdapterAction.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/AdapterActionFacade.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/AdapterActionMetricsPort.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/AdapterActionOrchestrationResult.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/AdapterActionRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/AdapterActionStatus.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/AdapterActionType.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/AdapterType.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/AdapterActionExecutionSummary.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/AdapterActionExecutor.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/AdapterExecutionOutcome.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/AdapterExecutionResult.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/audit/AdapterExecutorAuditRecord.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/audit/AdapterExecutorAuditRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/issue/IssueExecutorRequest.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/issue/IssueExecutorResponse.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/issue/IssueTrackingActionExecutor.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/issue/IssueVendor.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/mcp/McpExecutorRequest.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/mcp/McpActionExecutor.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/mcp/McpExecutorResponse.java",
}

FORBIDDEN_ADAPTER_ACTION_MODEL_CLASSES = {
    path.replace(f"{CORE_MODEL}/", "ai-event-gateway-core/adapter-action/")
    for path in REQUIRED_CORE_MODEL_CLASSES
}

# P18-E: Core-wide reusable service ports, facades, repository ports,
# operational query ports, domain enums, and API/result beans must live in
# Core data-model so feature modules keep only runtime implementation.
REQUIRED_CORE_WIDE_MODEL_CLASSES = {
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/AgentDirectoryFacade.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/AgentDirectoryRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/AgentRuntimeStateRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/AgentStatus.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/CapacityReservationResult.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/governance/AgentGovernanceRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/governance/AgentProfile.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/governance/AgentConnectionAuthorizationResult.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillDefinition.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/gateway/GatewayNodeRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/gateway/GatewayNode.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskOrchestrationFacade.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskType.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskStatus.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskPriority.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskLifecyclePolicy.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskLifecycleTransitionGuard.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskExecutionLifecyclePort.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/assignment/TaskDispatchPort.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/assignment/TaskDispatchAttemptHistoryPort.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/assignment/TaskAssignmentRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/assignment/TaskDispatchAttemptRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/RoutingMetricsPort.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/RoutingPolicy.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/ExecutionControlFacade.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/NettyDispatchPort.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/ExecutionMetricsPort.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/DispatchRecoveryMetricsPort.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/DispatchRequestRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/DispatchAttemptHistoryRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/ExecutionOperationalQuery.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/callback/TaskTerminalActionPort.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/incident/IncidentFacade.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/incident/IncidentRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/incident/Incident.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/incident/IncidentStatus.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/summary/IncidentOccurrenceSummaryRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/processing/EventProcessingFacade.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/processing/EventProcessingResult.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dedup/DedupDecision.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dedup/DedupState.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/outbox/ModuleEventPublisher.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/outbox/OutboxEventRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/outbox/OutboxEventStatus.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/integration/IntegrationEventRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/integration/IntegrationEventSink.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/integration/IntegrationEventStatus.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/runtime/RuntimeDisconnectResult.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/timeline/AdminFailureQueueItem.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/timeline/DispatchTimelineEvent.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/service/events/IntegrationEventEnvelope.java",
}

FORBIDDEN_CORE_WIDE_SOURCE_MODULES = {
    "agent-control",
    "task-orchestration",
    "execution-control",
    "incident",
    "event-processing",
    "domain-events",
    "integration-events",
    "control-plane-app",
    "service-contracts",
}

# P18-F: Netty-wide reusable runtime/API/transport/protocol model and
# service-port contracts must live in gateway-model. Runtime services,
# controllers, implementations, schedulers, and Spring configuration remain
# in their feature modules.
REQUIRED_NETTY_MODEL_CLASSES = {
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/admin/runtime/dto/RuntimeCallbackRelayObservabilityResponse.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/admin/runtime/dto/RuntimeSummaryResponse.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/callback/TaskCallbackRelayAttemptRecord.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/callback/TaskCallbackRelayMetricsHistory.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/callback/TaskCallbackRelayMetricsSnapshot.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/callback/TaskCallbackRelayResult.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/inbound/InboundEventEnvelope.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/tcp/TcpConnectionCleanupResult.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/delivery/routing/DeliveryRouteDecision.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/cluster/sync/ClusterPeerRelation.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/protocol/AiEventEnvelope.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/protocol/MessageType.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/admin/dto/AdminEventPayload.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/admin/dto/AdminRealtimeEvent.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/agent/AgentSnapshot.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/cluster/ClusterNodeSnapshot.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/admin/AdminDashboardSnapshotProvider.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/admin/AdminEventMetricsRecorder.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/admin/AdminEventPublisher.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/admin/audit/AuditEventPersistencePort.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/authorization/AgentConnectionAuthorizationClient.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/authorization/AgentSecurityEventPublisher.java",
    "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/directory/CoreDirectorySyncPublisher.java",
}

FORBIDDEN_NETTY_MODEL_SOURCE_MODULES = {
    "admin-api",
    "audit-core",
    "gateway-core",
    "transport-server",
}


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def module_of(path: Path) -> str:
    parts = path.relative_to(ROOT).parts
    if len(parts) < 2:
        return ""
    return f"{parts[0]}/{parts[1]}"


def package_name(source: str) -> str | None:
    match = re.search(r"^package\s+([\w.]+);", source, flags=re.MULTILINE)
    return match.group(1) if match else None


def java_code_only(source: str) -> str:
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL)
    source = re.sub(r"//.*", "", source)
    source = re.sub(r'"(?:\\.|[^"\\])*"', '""', source)
    return source


def main() -> int:
    java_files = sorted(ROOT.glob("ai-event-gateway-*/*/src/main/java/**/*.java"))

    missing_required = [path for path in sorted(REQUIRED_CORE_MODEL_CLASSES) if not (ROOT / path).is_file()]
    if missing_required:
        fail("Required P18 Adapter Action model/port/extension contract classes missing from Core data-model:\n" + "\n".join(missing_required))

    forbidden_existing = [path for path in sorted(FORBIDDEN_ADAPTER_ACTION_MODEL_CLASSES) if (ROOT / path).is_file()]
    if forbidden_existing:
        fail("Adapter Action model/port/extension contract classes still present outside Core data-model:\n" + "\n".join(forbidden_existing))

    missing_core_wide = [path for path in sorted(REQUIRED_CORE_WIDE_MODEL_CLASSES) if not (ROOT / path).is_file()]
    if missing_core_wide:
        fail("Required P18-E core-wide model/port/API contract classes missing from Core data-model:\n" + "\n".join(missing_core_wide))

    forbidden_core_wide = []
    for required in sorted(REQUIRED_CORE_WIDE_MODEL_CLASSES):
        rel_required = Path(required)
        try:
            class_rel = rel_required.relative_to(f"{CORE_MODEL}/src/main/java")
        except ValueError:
            continue
        for source_module in sorted(FORBIDDEN_CORE_WIDE_SOURCE_MODULES):
            candidate = ROOT / "ai-event-gateway-core" / source_module / "src/main/java" / class_rel
            if candidate.is_file():
                forbidden_core_wide.append(candidate.relative_to(ROOT).as_posix())
    if forbidden_core_wide:
        fail("P18-E core-wide model/port/API contract classes still present outside Core data-model:\n" + "\n".join(forbidden_core_wide))

    missing_netty = [path for path in sorted(REQUIRED_NETTY_MODEL_CLASSES) if not (ROOT / path).is_file()]
    if missing_netty:
        fail("Required P18-F Netty reusable model/port/API contract classes missing from gateway-model:\n" + "\n".join(missing_netty))

    forbidden_netty = []
    for required in sorted(REQUIRED_NETTY_MODEL_CLASSES):
        rel_required = Path(required)
        try:
            class_rel = rel_required.relative_to(f"{NETTY_MODEL}/src/main/java")
        except ValueError:
            continue
        for source_module in sorted(FORBIDDEN_NETTY_MODEL_SOURCE_MODULES):
            candidate = ROOT / "ai-event-gateway-netty" / source_module / "src/main/java" / class_rel
            if candidate.is_file():
                forbidden_netty.append(candidate.relative_to(ROOT).as_posix())
    if forbidden_netty:
        fail("P18-F Netty reusable model/port/API contract classes still present outside gateway-model:\n" + "\n".join(forbidden_netty))

    # 1. DAO/PO must stay in Core data-model. Netty currently has no DAO/PO layer.
    dao_po_violations = []
    for path in java_files:
        relative = rel(path)
        if DAO_PO_RE.search(path.name) or "/dao/" in relative or "/po/" in relative:
            if not relative.startswith(f"{CORE_MODEL}/"):
                dao_po_violations.append(relative)
    if dao_po_violations:
        fail("DAO/PO classes outside Core data-model:\n" + "\n".join(dao_po_violations))

    # 2. Reusable API/domain/model-like classes must be in model modules, except
    # explicitly documented service-local contracts.
    model_violations = []
    for path in java_files:
        relative = rel(path)
        if not (MODEL_NAME_RE.search(path.name) or "/dto/" in relative):
            continue
        if relative.startswith(f"{CORE_MODEL}/") or relative.startswith(f"{NETTY_MODEL}/"):
            continue
        if relative in ALLOWED_NON_MODEL:
            continue
        model_violations.append(relative)
    if model_violations:
        fail("Model-like classes outside canonical model modules:\n" + "\n".join(model_violations))

    # 3. Model modules must not import application/service/transport modules.
    class_to_module: dict[str, str] = {}
    sources: dict[Path, str] = {}
    code_sources: dict[Path, str] = {}
    for path in java_files:
        source = path.read_text(encoding="utf-8")
        sources[path] = source
        code_sources[path] = java_code_only(source)
        pkg = package_name(source)
        if pkg:
            class_to_module[f"{pkg}.{path.stem}"] = module_of(path)

    forbidden_imports = []
    for path, source in sources.items():
        mod = module_of(path)
        if mod not in {CORE_MODEL, NETTY_MODEL}:
            continue
        allowed_modules = {mod}
        if mod == CORE_MODEL:
            allowed_modules.add(CORE_KERNEL)
        for imported in re.findall(r"^import\s+([\w.]+);", source, flags=re.MULTILINE):
            if not imported.startswith("com.opensocket"):
                continue
            imported_module = class_to_module.get(imported)
            if imported_module and imported_module not in allowed_modules:
                forbidden_imports.append(f"{rel(path)} imports {imported} from {imported_module}")
    if forbidden_imports:
        fail("Canonical model module imports non-model application classes:\n" + "\n".join(forbidden_imports))

    # 3b. Also reject unqualified references from Core data-model to same-package
    # classes that still live in feature modules. Java allows same-package use
    # without an import, so import-only checks miss this class of boundary drift.
    outside_class_to_modules: dict[str, set[str]] = {}
    for fqcn, owner_module in class_to_module.items():
        class_name = fqcn.rsplit(".", 1)[-1]
        if owner_module == CORE_MODEL or owner_module == CORE_KERNEL:
            continue
        if not owner_module.startswith("ai-event-gateway-core/"):
            continue
        outside_class_to_modules.setdefault(class_name, set()).add(owner_module)

    unqualified_model_refs = []
    for path, source in sources.items():
        if module_of(path) != CORE_MODEL:
            continue
        tokens = set(re.findall(r"\b[A-Z][A-Za-z0-9_]*\b", code_sources[path]))
        for token in sorted(tokens):
            if token == path.stem or token not in outside_class_to_modules:
                continue
            unqualified_model_refs.append(
                f"{rel(path)} references {token} from {', '.join(sorted(outside_class_to_modules[token]))}"
            )
    if unqualified_model_refs:
        fail("Core data-model references feature-module classes without imports:\n" + "\n".join(unqualified_model_refs))

    netty_outside_class_to_modules: dict[str, set[str]] = {}
    for fqcn, owner_module in class_to_module.items():
        class_name = fqcn.rsplit(".", 1)[-1]
        if owner_module == NETTY_MODEL:
            continue
        if not owner_module.startswith("ai-event-gateway-netty/"):
            continue
        netty_outside_class_to_modules.setdefault(class_name, set()).add(owner_module)

    netty_unqualified_model_refs = []
    for path, source in sources.items():
        if module_of(path) != NETTY_MODEL:
            continue
        tokens = set(re.findall(r"\b[A-Z][A-Za-z0-9_]*\b", code_sources[path]))
        for token in sorted(tokens):
            if token == path.stem or token not in netty_outside_class_to_modules:
                continue
            netty_unqualified_model_refs.append(
                f"{rel(path)} references {token} from {', '.join(sorted(netty_outside_class_to_modules[token]))}"
            )
    if netty_unqualified_model_refs:
        fail("Netty gateway-model references feature-module classes without imports:\n" + "\n".join(netty_unqualified_model_refs))


    # 4. Modules that only consume adapter-action ports/contracts should not keep
    # a compile dependency on the adapter-action implementation module.
    for pom_rel in [
        "ai-event-gateway-core/observability/pom.xml",
        "ai-event-gateway-core/database-platform/pom.xml",
    ]:
        pom_text = (ROOT / pom_rel).read_text(encoding="utf-8")
        if "<artifactId>adapter-action</artifactId>" in pom_text:
            fail(f"Implementation dependency should be removed from contract-only module: {pom_rel}")

    # 5. Runtime implementation/SPI classes must remain in adapter-action. P18-D
    # only moves stable extension contracts, not Spring runtime wiring.
    required_adapter_action_runtime = [
        "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/AdapterActionService.java",
        "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/AdapterActionExecutionService.java",
        "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/issue/IssueTrackingAdapterActionExecutor.java",
        "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/mcp/HttpMcpActionExecutor.java",
        "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/AdapterExecutorUnavailableException.java",
        "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/AdapterExecutorTimeoutException.java",
    ]
    missing_runtime = [path for path in required_adapter_action_runtime if not (ROOT / path).is_file()]
    if missing_runtime:
        fail("Adapter Action runtime implementation/SPI classes should remain in adapter-action:\n" + "\n".join(missing_runtime))


    # 6. P18-G compile-surface guard: after broad model moves, every
    # com.opensocket import must resolve from the same Maven module or from a
    # declared transitive module dependency. This catches missing pom.xml edges
    # that a path-only verifier would miss.
    duplicate_classes = []
    fqcn_to_paths: dict[str, list[str]] = {}
    for fqcn, owner_module in class_to_module.items():
        # class_to_module collapses duplicates, so rebuild path list from sources
        pass
    fqcn_paths: dict[str, list[str]] = {}
    for path, source in sources.items():
        pkg = package_name(source)
        if pkg:
            fqcn_paths.setdefault(f"{pkg}.{path.stem}", []).append(rel(path))
    for fqcn, paths in sorted(fqcn_paths.items()):
        if len(paths) > 1:
            duplicate_classes.append(f"{fqcn}: " + ", ".join(paths))
    if duplicate_classes:
        fail("Duplicate Java FQCNs after model consolidation:\n" + "\n".join(duplicate_classes))

    import xml.etree.ElementTree as ET

    def project_artifact_id(pom: Path) -> str | None:
        try:
            root = ET.parse(pom).getroot()
        except ET.ParseError:
            return None
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        artifact = root.find("m:artifactId", ns)
        parent = root.find("m:parent", ns)
        if parent is not None:
            parent_artifact = parent.findtext("m:artifactId", namespaces=ns)
            # ElementTree find returns the first project-level artifactId for
            # these POMs, but keep this defensive fallback explicit.
            if artifact is not None and artifact.text != parent_artifact:
                return artifact.text
        return artifact.text if artifact is not None else None

    artifact_to_module: dict[str, str] = {}
    for pom in ROOT.glob("ai-event-gateway-*/*/pom.xml"):
        artifact = project_artifact_id(pom)
        if artifact:
            artifact_to_module[artifact] = "/".join(pom.relative_to(ROOT).parts[:2])

    def module_dependencies(pom: Path) -> set[str]:
        try:
            root = ET.parse(pom).getroot()
        except ET.ParseError:
            return set()
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        deps: set[str] = set()
        for dep in root.findall("./m:dependencies/m:dependency", ns):
            group_id = dep.findtext("m:groupId", namespaces=ns)
            artifact = dep.findtext("m:artifactId", namespaces=ns)
            if group_id == "com.opensocket" and artifact in artifact_to_module:
                deps.add(artifact_to_module[artifact])
        return deps

    module_dependency_map: dict[str, set[str]] = {}
    for pom in ROOT.glob("ai-event-gateway-*/*/pom.xml"):
        module_dependency_map["/".join(pom.relative_to(ROOT).parts[:2])] = module_dependencies(pom)

    transitive_dependencies = {module: set(deps) for module, deps in module_dependency_map.items()}
    changed = True
    while changed:
        changed = False
        for module, deps in list(transitive_dependencies.items()):
            expanded = set(deps)
            for dep in deps:
                expanded.update(transitive_dependencies.get(dep, set()))
            if expanded != deps:
                transitive_dependencies[module] = expanded
                changed = True

    def owners_for_import(imported: str) -> set[str]:
        parts = imported.split(".")
        for end in range(len(parts), 0, -1):
            candidate = ".".join(parts[:end])
            owner = class_to_module.get(candidate)
            if owner:
                return {owner}
        return set()

    dependency_violations = []
    all_java_for_dependency_check = sorted(
        ROOT.glob("ai-event-gateway-*/*/src/main/java/**/*.java")
    ) + sorted(ROOT.glob("ai-event-gateway-*/*/src/test/java/**/*.java"))
    for path in all_java_for_dependency_check:
        source = path.read_text(encoding="utf-8")
        source_module = module_of(path)
        compile_path = {source_module} | transitive_dependencies.get(source_module, set())
        for imported in re.findall(r"^import\s+([\w.]+);", source, flags=re.MULTILINE):
            if not imported.startswith("com.opensocket"):
                continue
            owners = owners_for_import(imported)
            if owners and not owners.intersection(compile_path):
                dependency_violations.append(
                    f"{rel(path)} imports {imported} from {', '.join(sorted(owners))} without a module dependency"
                )
    if dependency_violations:
        fail("Missing Maven module dependencies after model consolidation:\n" + "\n".join(dependency_violations))

    gateway_model_pom = (ROOT / "ai-event-gateway-netty/gateway-model/pom.xml").read_text(encoding="utf-8")
    if "tools.jackson.databind.JsonNode" in "\n".join(sources[path] for path in sources if module_of(path) == NETTY_MODEL):
        if "<groupId>tools.jackson.core</groupId>" not in gateway_model_pom or "<artifactId>jackson-databind</artifactId>" not in gateway_model_pom:
            fail("gateway-model uses Jackson 3 JsonNode but does not declare tools.jackson.core:jackson-databind")
    forbidden_jackson2_databind = []
    for path, source in sources.items():
        if module_of(path) in {CORE_MODEL, NETTY_MODEL} and "com.fasterxml.jackson.databind" in source:
            forbidden_jackson2_databind.append(rel(path))
    if forbidden_jackson2_databind:
        fail("Canonical model modules must not import Jackson 2 databind classes:\n" + "\n".join(forbidden_jackson2_databind))

    print("P18 model boundary verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
