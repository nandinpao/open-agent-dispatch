#!/usr/bin/env python3
from pathlib import Path
import json
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]

checks = []

def require(path: str, contains: list[str] | None = None):
    p = ROOT / path
    if not p.exists():
        raise AssertionError(f"Missing required file: {path}")
    text = p.read_text(encoding="utf-8") if p.is_file() else ""
    for needle in contains or []:
        if needle not in text:
            raise AssertionError(f"Missing expected content in {path}: {needle}")
    checks.append(path)
    return text

# Core metrics service and wiring
require("ai-event-gateway-core/observability/src/main/java/com/opensocket/aievent/core/observability/AgentRemediationWorkflowMetricsService.java", [
    "aeg.core.remediation.workflows.created.total",
    "aeg.core.remediation.workflows.approval.latency",
    "aeg.core.remediation.workflow.actions.executions.total",
    "aeg.core.remediation.workflow.stale_leases.recovered.total",
    "agentId",
])
require("ai-event-gateway-core/observability/src/main/java/com/opensocket/aievent/core/observability/ObservabilityProperties.java", [
    "RemediationWorkflowMetrics",
    "actionFailureRatioWarning",
    "approvalLatencyCritical",
])
require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationController.java", [
    "AgentRemediationWorkflowMetricsService",
    "recordWorkflowCreated",
    "recordApprovalLatency",
    "recordWorkflowExecution",
    "recordActionExecutionMetrics",
    "recordWorkflowLeaseEvent",
])
require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationWorkflowStaleLeaseRecoveryService.java", [
    "recordStaleLeaseRecoveryRun",
])
require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreStatusController.java", [
    "agentRemediationWorkflowMetricsEnabled",
    "P12_MICROMETER_PROMETHEUS_ALERTING",
    "agentRemediationWorkflowMetricsGuardrails",
])
require("ai-event-gateway-core/control-plane-app/src/main/resources/application.yml", [
    "remediation-workflow-metrics:",
    "CORE_REMEDIATION_WORKFLOW_METRICS_ENABLED",
    "CORE_REMEDIATION_ACTION_FAILURE_RATIO_WARNING",
])

# Alerting and dashboard artifacts
alerts = require("deploy/observability/prometheus/alerts/opendispatch-remediation-workflow.rules.yml", [
    "OpenDispatchRemediationWorkflowExecutionFailures",
    "OpenDispatchRemediationWorkflowActionFailureRatioHigh",
    "OpenDispatchRemediationWorkflowApprovalLatencyHigh",
    "OpenDispatchRemediationWorkflowStaleLeaseRecovery",
    "aeg_core_remediation_workflow_actions_executions_total",
])
if "agentId" in alerts or "workflowId" in alerts or "operatorId" in alerts:
    raise AssertionError("Prometheus alert rules must not use high-cardinality remediation labels")

dashboard_path = ROOT / "deploy/observability/grafana/dashboards/opendispatch-remediation-workflow.json"
json.loads(dashboard_path.read_text(encoding="utf-8"))
checks.append(str(dashboard_path.relative_to(ROOT)))

# Documentation, README, and delivery-summary markdown files are intentionally skipped.

# XML sanity for Maven/MyBatis paths touched indirectly.
for path in [
    "ai-event-gateway-core/observability/pom.xml",
    "ai-event-gateway-core/control-plane-app/pom.xml",
]:
    ET.parse(ROOT / path)
    checks.append(path)

print(f"P12 remediation metrics / alerting verification passed ({len(checks)} checks).")
