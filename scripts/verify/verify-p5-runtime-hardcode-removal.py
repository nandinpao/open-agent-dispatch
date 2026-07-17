#!/usr/bin/env python3
"""Verify P5 removes source-specific runtime behavior and fails closed for new work."""
from __future__ import annotations
import subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def fail(msg:str)->None:
 print(f"[FAIL] {msg}",file=sys.stderr); raise SystemExit(1)
def read(rel:str)->str:
 p=ROOT/rel
 if not p.is_file(): fail(f"Missing required file: {rel}")
 return p.read_text(encoding="utf-8")
def require(rel:str,fragments:list[str])->str:
 text=read(rel)
 for f in fragments:
  if f not in text: fail(f"{rel} missing contract fragment: {f}")
 return text
def forbid(rel:str,tokens:list[str])->str:
 text=read(rel)
 for t in tokens:
  if t in text: fail(f"{rel} still contains forbidden P5 runtime token: {t}")
 return text

def main()->int:
 skill=forbid("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java",["seedDefaults();","ERP_APPROVAL_ANALYSIS","MES_ALARM_TRIAGE","HR_PAYROLL_ANOMALY_ANALYSIS"])
 recipe=forbid("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/recipe/DispatchRecipeService.java",["seedDefaults();","DEFAULT_AGENT_ID",'"tenant-a"'])
 for fragment in ["tenantId = requireNonBlank", "agentId = requireNonBlank", 'requireNonBlank(body.getObjectType(), "objectType")', "request.setTenantId(tenantId)"]:
  if fragment not in recipe: fail(f"DispatchRecipeService missing explicit input contract: {fragment}")

 capability=require("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolverService.java",["tenantId = requireNonBlank","sourceSystem = requireNonBlank","NO_CONFIGURED_DISPATCH_CONTRACT","failClosed="])
 for t in ["INCIDENT_ANALYSIS\");", "isLegacyTaskAlias", "MES_ALARM_TRIAGE", "ERP_PURCHASE_ORDER_REVIEW"]:
  if t in capability: fail(f"TaskCapabilityResolverService retains fallback/alias token: {t}")
 contract=forbid("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/TaskDispatchContractResolverService.java",['"ERP"','"MES"','"CMS"','"HR"',"isMesIncident","domainFromObjectType","operationFromEventType"])
 eligibility=forbid("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityService.java",["mesIncidentResponseRequirementProfile","CANONICAL_MES","isLegacyTaskAlias"])

 enum=read("ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/RoutingPolicy.java")
 for required in ["MANUAL_REVIEW","LOCAL_ONLY","LOCAL_FIRST","GLOBAL_AVAILABLE_FIRST","FLOW_RULE","CAPABILITY_FIRST","LOAD_BALANCED"]:
  if required not in enum: fail(f"RoutingPolicy missing generic policy {required}")
 for obsolete in ["ERP_CAPABILITY_FIRST","MES_LOCAL_FIRST","HR_SENSITIVE_REVIEW","MIS_LOAD_BALANCED","DOMAIN_AWARE"]:
  if obsolete in enum: fail(f"RoutingPolicy retains source-specific policy {obsolete}")

 routing=require("ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java",["RoutingPolicy.MANUAL_REVIEW","properties.isFlowRuleRoutingEnabled() && !properties.isFlowRuleLegacyFallbackEnabled()","decideWithGenericAuthority(task"])
 for obsolete in ["inferTaskDomain",'case "ERP_CAPABILITY_FIRST"','case "MES_LOCAL_FIRST"','case "HR_SENSITIVE_REVIEW"','isPersistedLegacyRecovery' + '(task)','TaskDispatchContractResolverService','backendDispatchEligibilityService','dispatchEligibilityServiceV2']:
  if obsolete in routing: fail(f"RoutingDecisionService retains obsolete inference/alias or legacy dependency: {obsolete}")
 new_work=routing.find("properties.isFlowRuleRoutingEnabled() && !properties.isFlowRuleLegacyFallbackEnabled()")
 generic=routing.find("decideWithGenericAuthority(task")
 if new_work < 0 or generic < 0 or new_work > generic:
  fail("New-work fail-closed branch must execute before generic authority routing")

 props=require("ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingProperties.java",["zeroSpecialCaseRuntimeEnabled = true","persistedLegacyEvidenceRecoveryEnabled","flowRuleLegacyFallbackEnabled = false"])
 if "erpPolicy" in props or "mesPolicy" in props or "hrPolicy" in props: fail("RoutingProperties retains source-specific policy properties")

 flow=require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java",["requireNonBlank(flow.getSourceSystem()",'return requireNonBlank(value, "tenantId").trim();'])
 if 'firstNonBlank(flow.getSourceSystem(), "ERP")' in flow or '"tenant-a"' in flow: fail("DispatchFlowManagementService retains source/tenant defaults")
 controller=read("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java")
 if 'defaultValue = "tenant-a"' in controller: fail("DispatchFlowController retains tenant-a request default")
 for token in ['"ERP"', '"MES"', '"CMS"', 'ERP_', 'MES_', 'CMS_']:
  if token in controller: fail(f"DispatchFlowController retains source-specific preview token: {token}")
 readiness_templates=read("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchReadinessController.java")
 for token in ['"ERP"', '"MES"', '"CMS"', 'ERP_', 'MES_', 'CMS_', 'INCIDENT_ANALYSIS']:
  if token in readiness_templates: fail(f"DispatchReadinessController retains source-specific/default Capability template: {token}")
 repository=require("ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java",["requireNonBlank(query.getTenantId()","tenantAliases(rawTenantId)","where upper(p.tenant_id) in (:tenantIds)"])
 if '"default"' in repository or '"DEFAULT"' in repository: fail("Flow Rule repository retains cross-tenant fallback")


 setup=require("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupService.java",["tenantId is required","purpose is required","Automatic Capability Catalog creation was removed","Automatic Dispatch Rule creation was removed"])
 for token in ["defaultCapabilitiesForPurpose", "defaultTaskTypesForPurpose", "categoryForPurpose", "domainForPurpose", "resourceForPurpose", "operationForCapability", "upsertCapability(", "upsertDispatchPolicy("]:
  if token in setup: fail(f"AgentSetupService retains implicit setup inference: {token}")
 setup_request=require("ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupRequest.java",["private boolean autoApprove = false", "private boolean createDefaultCapabilities = false", "private boolean createRuntimeBinding = false", "private boolean createSupplyProfile = false", "private boolean createDefaultDispatchRule = false"])
 for token in ['private String tenantId =', 'private String purpose =']:
  if token in setup_request: fail(f"AgentSetupRequest retains implicit identity/domain default: {token}")
 onboarding=require("ai-event-gateway-admin-ui/components/agents/AgentOnboardingPanel.tsx",["createDefaultCapabilities: false", "createDefaultDispatchRule: false", "defaultCapabilities: []", "defaultTaskTypes: []"])
 for token in ["tenant-a", "agent-cluster-node", "createDefaultCapabilities: true", "createDefaultDispatchRule: true"]:
  if token in onboarding: fail(f"Agent onboarding retains implicit business setup: {token}")
 recipe_ui=require("ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts",["DispatchRecipeExecutionContext", "requireRecipeValue(context.tenantId, 'tenantId')", "requireRecipeValue(context.sourceSystem, 'sourceSystem')", "explicitRuntimeInputs: true"])
 for token in ["tenant-a", "DEFAULT_RECIPE_AGENT_ID", "scenario.domain ===", "agent-cluster-node"]:
  if token in recipe_ui: fail(f"Dispatch recipe UI retains implicit runtime default/inference: {token}")
 api=read("ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts")
 if 'tenantId = "tenant-a"' in api or "tenantId = 'tenant-a'" in api: fail("Core Admin API retains tenant-a defaults")

 assignment=read("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java")
 for token in ["inferCapabilityOperation", "looksLikeTaskAlias", 'endsWith("_ANALYSIS")', 'endsWith("_REVIEW")']:
  if token in assignment: fail(f"AgentAssignmentService retains Capability-name inference: {token}")
 issue_adapter=read("ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/AdapterActionService.java")
 issue_executor=read("ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/issue/AbstractHttpIssueVendorExecutor.java")
 for source in ['"ERP"', '"MES"', '"HR"']:
  if source in issue_adapter or source in issue_executor: fail(f"Issue adapter retains source inference literal: {source}")
 task_type=read("ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskType.java")
 if "CMS_CONTENT_REVIEW" in task_type: fail("TaskType retains source-specific business enum")

 memory=read("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/InMemoryAgentAssignmentRepository.java")
 for obsolete in ["seedDefinition(","seedCapability(","OPENCLAW_ISSUE_ANALYZER",'? "default" : tenantId']:
  if obsolete in memory: fail(f"In-memory assignment repository retains implicit seed/default: {obsolete}")

 for base in [
  ROOT/"ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch",
  ROOT/"ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment",
 ]:
  for p in base.rglob("*.java"):
   if 'private String tenantId = "default";' in p.read_text(encoding="utf-8"):
    fail(f"Dispatch model retains implicit tenant default: {p.relative_to(ROOT)}")

 require("ai-event-gateway-core/control-plane-app/src/main/resources/application.yml",["zero-special-case-runtime-enabled","persisted-legacy-evidence-recovery-enabled","flow-rule-legacy-fallback-enabled"])
 status=require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreStatusController.java",["routingZeroSpecialCaseRuntimeEnabled","routingPersistedLegacyEvidenceRecoveryEnabled","routingNewWorkFailClosed"])
 if "routingErpPolicy" in status or "routingMesPolicy" in status: fail("Core status exposes removed source policies")

 result=subprocess.run([str(ROOT/"scripts/verify/verify-p5-runtime-fail-closed-java.sh")],cwd=ROOT)
 if result.returncode!=0: fail("P5 Java fail-closed harness failed")
 setup_compile=subprocess.run([str(ROOT/"scripts/verify/verify-p5-agent-setup-java.sh")],cwd=ROOT)
 if setup_compile.returncode!=0: fail("P5 AgentSetupService compile harness failed")
 guard=subprocess.run([sys.executable,str(ROOT/"scripts/architecture/zero_special_case_guard.py")],cwd=ROOT)
 if guard.returncode!=0: fail("P0 zero-special-case guard failed after P5")
 print("[PASS] P5 runtime hardcode removal, explicit tenant/source contracts, and fail-closed recovery verified.")
 return 0
if __name__=="__main__": raise SystemExit(main())
