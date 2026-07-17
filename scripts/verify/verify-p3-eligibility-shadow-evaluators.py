#!/usr/bin/env python3
"""Verify P3 generic Agent eligibility evaluators remain shadow-only and source-agnostic."""
from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def require(relative: str, fragments: list[str]) -> str:
    text = read(relative)
    for fragment in fragments:
        if fragment not in text:
            fail(f"{relative} is missing contract fragment: {fragment}")
    return text


def forbid_named_sources(relative: str) -> None:
    text = read(relative)
    for token in ('"ERP"', '"MES"', '"CMS"', '"HR"', '"WMS"', "tenant-a", "agent-cluster-node"):
        if token in text:
            fail(f"P3 generic file contains forbidden source-specific token {token}: {relative}")


def compile_evaluator_harness() -> None:
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        print("[WARN] Java toolchain unavailable; P3 evaluator harness skipped")
        return

    with tempfile.TemporaryDirectory(prefix="p3-eligibility-shadow-") as temp:
        tmp = Path(temp)
        src = tmp / "src"
        out = tmp / "classes"
        stubs: dict[str, str] = {
            "org/springframework/stereotype/Component.java": "package org.springframework.stereotype; public @interface Component {}\n",
            "com/opensocket/aievent/core/agent/AgentStatus.java": "package com.opensocket.aievent.core.agent; public enum AgentStatus { CONNECTED,IDLE,BUSY,BUSY_ACCEPTING,DRAINING,OFFLINE,EXPIRED,ERROR }\n",
            "com/opensocket/aievent/core/agent/AgentSnapshot.java": r'''package com.opensocket.aievent.core.agent;
public class AgentSnapshot {
 private String id,node,session; private AgentStatus status=AgentStatus.IDLE; private boolean draining,backoff; private int slots=1,current,reserved,max=1;
 public String getAgentId(){return id;} public void setAgentId(String v){id=v;} public String getOwnerGatewayNodeId(){return node;} public void setOwnerGatewayNodeId(String v){node=v;}
 public String getAgentSessionId(){return session;} public void setAgentSessionId(String v){session=v;} public AgentStatus getStatus(){return status;} public void setStatus(AgentStatus v){status=v;}
 public boolean isDraining(){return draining;} public boolean isRuntimeBackoffActive(){return backoff;} public Object getRuntimeBackoffUntil(){return null;} public String getRuntimeBackoffReason(){return null;}
 public int getAvailableSlots(){return slots;} public void setAvailableSlots(int v){slots=v;} public int getCurrentTaskCount(){return current;} public int getReservedTaskCount(){return reserved;}
 public int getEffectiveTaskCount(){return current+reserved;} public int getMaxConcurrentTasks(){return max;} public void setMaxConcurrentTasks(int v){max=v;}
}''',
            "com/opensocket/aievent/core/agent/assignment/AgentCapabilityAssignmentStatus.java": "package com.opensocket.aievent.core.agent.assignment; public enum AgentCapabilityAssignmentStatus { APPROVED,PENDING_APPROVAL,SUSPENDED,REVOKED }\n",
            "com/opensocket/aievent/core/agent/assignment/AgentCapabilityAssignment.java": r'''package com.opensocket.aievent.core.agent.assignment; import java.time.*; public class AgentCapabilityAssignment {
 private String tenant,code; private AgentCapabilityAssignmentStatus status; private OffsetDateTime expires;
 public String getTenantId(){return tenant;} public void setTenantId(String v){tenant=v;} public String getCapabilityCode(){return code;} public void setCapabilityCode(String v){code=v;}
 public AgentCapabilityAssignmentStatus getStatus(){return status;} public void setStatus(AgentCapabilityAssignmentStatus v){status=v;} public OffsetDateTime getExpiresAt(){return expires;}
}''',
            "com/opensocket/aievent/core/agent/governance/AgentApprovalStatus.java": "package com.opensocket.aievent.core.agent.governance; public enum AgentApprovalStatus { APPROVED,PENDING_REVIEW; public boolean isApproved(){return this==APPROVED;} }\n",
            "com/opensocket/aievent/core/agent/governance/AgentRiskStatus.java": "package com.opensocket.aievent.core.agent.governance; public enum AgentRiskStatus { NORMAL,SUSPENDED; public boolean allowsAssignment(){return this==NORMAL;} }\n",
            "com/opensocket/aievent/core/agent/governance/AgentCredentialStatus.java": "package com.opensocket.aievent.core.agent.governance; public enum AgentCredentialStatus { ACTIVE,EXPIRED,REVOKED }\n",
            "com/opensocket/aievent/core/agent/governance/AgentCredentialSummary.java": r'''package com.opensocket.aievent.core.agent.governance; import java.time.*; public class AgentCredentialSummary {
 private AgentCredentialStatus status=AgentCredentialStatus.ACTIVE; private OffsetDateTime expires; public AgentCredentialStatus getCredentialStatus(){return status;} public OffsetDateTime getExpiresAt(){return expires;}
}''',
            "com/opensocket/aievent/core/agent/governance/AgentProfile.java": r'''package com.opensocket.aievent.core.agent.governance; public class AgentProfile {
 private String id,tenant; private AgentApprovalStatus approval=AgentApprovalStatus.APPROVED; private boolean enabled=true; private AgentRiskStatus risk=AgentRiskStatus.NORMAL; private AgentCredentialSummary credential=new AgentCredentialSummary();
 public String getAgentId(){return id;} public String getTenantId(){return tenant;} public void setTenantId(String v){tenant=v;} public AgentApprovalStatus getApprovalStatus(){return approval;} public boolean isEnabled(){return enabled;}
 public AgentRiskStatus getRiskStatus(){return risk;} public AgentCredentialSummary getCredential(){return credential;}
}''',
            "com/opensocket/aievent/core/task/TaskRecord.java": "package com.opensocket.aievent.core.task; public class TaskRecord { private String type; public String getEffectiveTaskTypeCode(){return type;} public void setEffectiveTaskTypeCode(String v){type=v;} }\n",
            "com/opensocket/aievent/core/routing/governance/RequirementResolutionMode.java": "package com.opensocket.aievent.core.routing.governance; public enum RequirementResolutionMode { LEGACY,EXPLICIT_CAPABILITY,SOURCE_BASELINE,NONE }\n",
            "com/opensocket/aievent/core/routing/governance/DispatchOperation.java": "package com.opensocket.aievent.core.routing.governance; public enum DispatchOperation { READ(false),ANALYZE(false),PROPOSE(false),EXECUTE(true),REMEDIATE(true),APPROVE(true); private final boolean e; DispatchOperation(boolean e){this.e=e;} public boolean isEffectful(){return e;} }\n",
            "com/opensocket/aievent/core/routing/governance/SideEffectLevel.java": "package com.opensocket.aievent.core.routing.governance; public enum SideEffectLevel { NONE,REVERSIBLE_WRITE,IRREVERSIBLE_WRITE }\n",
            "com/opensocket/aievent/core/routing/governance/CandidatePoolMode.java": "package com.opensocket.aievent.core.routing.governance; public enum CandidatePoolMode { LEGACY,EXPLICIT_FLOW_AGENTS,SOURCE_SYSTEM_POOL,CAPABILITY_MATCHED_POOL }\n",
            "com/opensocket/aievent/core/routing/governance/SourceCoverageScope.java": "package com.opensocket.aievent.core.routing.governance; public enum SourceCoverageScope { ALL_SOURCE_TASKS,FLOW_ASSIGNED_TASKS,EXPLICIT_TASK_TYPES }\n",
            "com/opensocket/aievent/core/routing/governance/DispatchApprovalStatus.java": "package com.opensocket.aievent.core.routing.governance; public enum DispatchApprovalStatus { PENDING,APPROVED }\n",
            "com/opensocket/aievent/core/routing/governance/AgentSourceAssignment.java": r'''package com.opensocket.aievent.core.routing.governance; import java.time.*; import java.util.*; public class AgentSourceAssignment {
 private String id="a",agent="agent",profile="p",status="ACTIVE"; private DispatchApprovalStatus approval=DispatchApprovalStatus.APPROVED; private SourceCoverageScope scope=SourceCoverageScope.ALL_SOURCE_TASKS; private List<String> types=List.of();
 public boolean isEligibleAt(OffsetDateTime n){return "ACTIVE".equals(status)&&approval==DispatchApprovalStatus.APPROVED;} public String getAssignmentId(){return id;} public String getAgentId(){return agent;}
 public String getOperationProfileId(){return profile;} public String getStatus(){return status;} public DispatchApprovalStatus getApprovalStatus(){return approval;} public SourceCoverageScope getCoverageScope(){return scope;} public List<String> getTaskTypes(){return types;}
}''',
            "com/opensocket/aievent/core/routing/governance/DispatchGovernanceStatus.java": "package com.opensocket.aievent.core.routing.governance; public enum DispatchGovernanceStatus { DRAFT,ACTIVE,DISABLED }\n",
            "com/opensocket/aievent/core/routing/governance/action/AgentActionGrant.java": r'''package com.opensocket.aievent.core.routing.governance.action; import java.time.*; public class AgentActionGrant { private String id="grant",agent="agent",source="SOURCE_RANDOM",action="ACTION_RANDOM"; public boolean isActiveAt(OffsetDateTime now){return true;} public String getGrantId(){return id;} public String getAgentId(){return agent;} public String getSourceSystem(){return source;} public String getActionCode(){return action;} }''',
            "com/opensocket/aievent/core/routing/governance/DispatchOperationProfile.java": r'''package com.opensocket.aievent.core.routing.governance; import java.util.*; public class DispatchOperationProfile {
 private String id="p",status="ACTIVE"; private Set<DispatchOperation> ops=Set.of(DispatchOperation.READ,DispatchOperation.ANALYZE,DispatchOperation.PROPOSE); public boolean allows(DispatchOperation op){return ops.contains(op);}
 public String getProfileId(){return id;} public String getStatus(){return status;} public Set<DispatchOperation> getOperations(){return ops;} public void setOperations(Set<DispatchOperation> v){ops=v;}
}''',
            "com/opensocket/aievent/core/routing/governance/TaskRequirementEvidence.java": r'''package com.opensocket.aievent.core.routing.governance; import java.util.*; public class TaskRequirementEvidence {
 private String tenant="tenant"; private RequirementResolutionMode mode=RequirementResolutionMode.SOURCE_BASELINE; private List<DispatchOperation> ops=List.of(DispatchOperation.READ,DispatchOperation.ANALYZE); private List<String> caps=List.of(); private SideEffectLevel side=SideEffectLevel.NONE; private boolean auth=true; private String profile="p"; private Map<String,Object> evidence=new HashMap<>(Map.of("candidatePoolMode","SOURCE_SYSTEM_POOL"));
 public String getTenantId(){return tenant;} public String getSourceSystem(){return "SOURCE_RANDOM";} public RequirementResolutionMode getResolutionMode(){return mode;} public void setResolutionMode(RequirementResolutionMode v){mode=v;} public List<DispatchOperation> getRequiredOperations(){return ops;} public void setRequiredOperations(List<DispatchOperation> v){ops=v;} public List<String> getRequiredCapabilities(){return caps;} public void setRequiredCapabilities(List<String> v){caps=v;} public SideEffectLevel getSideEffectLevel(){return side;} public void setSideEffectLevel(SideEffectLevel v){side=v;} public boolean isExplicitActionAuthorizationRequired(){return auth;} public String getOperationProfileId(){return profile;} public Map<String,Object> getEvidence(){return evidence;}
}''',
        }
        for relative, content in stubs.items():
            path = src / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")

        harness = src / "P3EligibilityHarness.java"
        harness.write_text(r'''import java.time.*; import java.util.*;
import com.opensocket.aievent.core.agent.*; import com.opensocket.aievent.core.agent.governance.*; import com.opensocket.aievent.core.routing.governance.*; import com.opensocket.aievent.core.routing.governance.eligibility.*;
public class P3EligibilityHarness {
 public static void main(String[] args){
  DispatchEligibilityShadowContext c=new DispatchEligibilityShadowContext(); c.setRequirement(new TaskRequirementEvidence()); c.setSourceAssignment(new AgentSourceAssignment()); c.setOperationProfile(new DispatchOperationProfile()); c.setGovernanceProfile(new AgentProfile());
  AgentSnapshot r=new AgentSnapshot(); r.setAgentId("agent"); r.setOwnerGatewayNodeId("node"); r.setAgentSessionId("session"); r.setStatus(AgentStatus.IDLE); r.setAvailableSlots(1); c.setRuntime(r); c.setEvaluatedAt(OffsetDateTime.now());
  List<DispatchEligibilityShadowEvaluator> e=List.of(new SourceCoverageEligibilityEvaluator(),new CapabilityEligibilityEvaluator(),new OperationEligibilityEvaluator(),new ActionAuthorizationEvaluator(),new GovernanceEligibilityEvaluator(),new RuntimeEligibilityEvaluator(),new CapacityEligibilityEvaluator());
  if(e.stream().map(x->x.evaluate(c)).anyMatch(AgentEligibilityShadowCheck::isBlocking)) throw new IllegalStateException("safe analysis blocked");
  c.getRequirement().setRequiredOperations(List.of(DispatchOperation.REMEDIATE)); c.getRequirement().setSideEffectLevel(SideEffectLevel.REVERSIBLE_WRITE); c.getOperationProfile().setOperations(Set.of(DispatchOperation.REMEDIATE));
  if(!new ActionAuthorizationEvaluator().evaluate(c).isBlocking()) throw new IllegalStateException("effectful action not blocked");
  c.getRequirement().setResolutionMode(RequirementResolutionMode.EXPLICIT_CAPABILITY); c.getRequirement().setRequiredCapabilities(List.of("CAP_RANDOM"));
  if(!new CapabilityEligibilityEvaluator().evaluate(c).isBlocking()) throw new IllegalStateException("missing capability not blocked");
  System.out.println("P3 eligibility evaluator harness passed");
 }
}''', encoding="utf-8")

        actual = [
            ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/EligibilityShadowCheckOutcome.java",
            ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/AgentEligibilityShadowCheck.java",
            ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/DispatchEligibilityShadowContext.java",
            ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/DispatchEligibilityShadowEvaluator.java",
            ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/SourceCoverageEligibilityEvaluator.java",
            ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/CapabilityEligibilityEvaluator.java",
            ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/OperationEligibilityEvaluator.java",
            ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/ActionAuthorizationEvaluator.java",
            ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/GovernanceEligibilityEvaluator.java",
            ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/RuntimeEligibilityEvaluator.java",
            ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/CapacityEligibilityEvaluator.java",
        ]
        sources = [str(path) for path in src.rglob("*.java")] + [str(path) for path in actual]
        out.mkdir(parents=True, exist_ok=True)
        result = subprocess.run([javac, "-d", str(out), *sources], cwd=ROOT)
        if result.returncode != 0:
            fail("P3 evaluator compilation failed")
        result = subprocess.run([java, "-cp", str(out), "P3EligibilityHarness"], cwd=ROOT)
        if result.returncode != 0:
            fail("P3 evaluator harness failed")


def main() -> int:
    evaluator_dir = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/"
    names = [
        "SourceCoverageEligibilityEvaluator.java",
        "CapabilityEligibilityEvaluator.java",
        "OperationEligibilityEvaluator.java",
        "ActionAuthorizationEvaluator.java",
        "GovernanceEligibilityEvaluator.java",
        "RuntimeEligibilityEvaluator.java",
        "CapacityEligibilityEvaluator.java",
    ]
    for name in names:
        require(evaluator_dir + name, ["implements DispatchEligibilityShadowEvaluator", "EligibilityShadowCheckOutcome"])
        forbid_named_sources(evaluator_dir + name)

    service = evaluator_dir + "DispatchEligibilityShadowService.java"
    require(service, [
        "authoritativeRoutingUnchanged=true",
        "TaskRequirementEvidenceRepository",
        "AgentSourceAssignmentRepository",
        "findAgentCapabilities",
        "getProfile",
        "EQUIVALENT_ELIGIBLE",
        "LEGACY_ONLY_ELIGIBLE",
        "SHADOW_ONLY_ELIGIBLE",
    ])
    forbid_named_sources(service)

    routing = require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java",
        ["observeGenericEligibilityShadow(task, candidatePool.included(), scores)", "genericEligibilityShadowService.observe"],
    )
    observe_index = routing.find("observeGenericEligibilityShadow(task, candidatePool.included(), scores)")
    enforce_index = routing.find("if (eligibilityMode.enforce()", observe_index)
    if observe_index < 0 or enforce_index < 0 or observe_index > enforce_index:
        fail("P3 observer must run before any authoritative V2 enforce filtering")

    migration = "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V118__p3_eligibility_shadow_comparison.sql"
    require(migration, [
        "task_agent_eligibility_shadow_comparisons",
        "requirement_evidence_id",
        "legacy_eligible",
        "shadow_eligible",
        "checks_json",
        "append-only",
        "dispatch_p3_agent_eligibility_shadow_difference_report",
        "dispatch_p3_agent_eligibility_shadow_summary",
    ])
    forbid_named_sources(migration)

    require("ai-event-gateway-core/control-plane-app/src/main/resources/application.yml", [
        "generic-eligibility-shadow-enabled",
        "generic-eligibility-shadow-persist-comparisons",
        "generic-eligibility-shadow-max-candidates",
    ])
    require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchGovernanceController.java", [
        "/agent-eligibility-shadow-comparisons",
        "/tasks/{taskId}/agent-eligibility-shadow-comparisons",
    ])

    compile_evaluator_harness()
    guard = subprocess.run([sys.executable, str(ROOT / "scripts/verify/verify-p0-zero-special-case-dispatch.py")], cwd=ROOT)
    if guard.returncode != 0:
        fail("Zero-special-case guard failed after P3")
    print("[PASS] P3 generic eligibility shadow evaluators and Agent-by-Agent comparison verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
