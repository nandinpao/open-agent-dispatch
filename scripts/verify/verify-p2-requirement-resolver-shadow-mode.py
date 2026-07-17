#!/usr/bin/env python3
"""Verify P2 generic requirement resolver shadow mode without activating it as authority."""
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
            fail(f"P2 generic file contains forbidden source-specific token {token}: {relative}")


def compile_and_run_harness() -> None:
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        print("[WARN] Java toolchain unavailable; P2 resolver harness skipped")
        return

    with tempfile.TemporaryDirectory(prefix="p2-requirement-shadow-") as temp:
        tmp = Path(temp)
        stubs = tmp / "stubs"
        output = tmp / "classes"
        harness = tmp / "P2ResolverHarness.java"
        files = {
            "org/springframework/stereotype/Service.java":
                "package org.springframework.stereotype; public @interface Service {}\n",
            "org/springframework/boot/context/properties/ConfigurationProperties.java":
                "package org.springframework.boot.context.properties; public @interface ConfigurationProperties { String prefix(); }\n",
            "org/springframework/transaction/annotation/Propagation.java":
                "package org.springframework.transaction.annotation; public enum Propagation { REQUIRED, REQUIRES_NEW }\n",
            "org/springframework/transaction/annotation/Transactional.java":
                "package org.springframework.transaction.annotation; public @interface Transactional { Propagation propagation() default Propagation.REQUIRED; }\n",
            "org/springframework/beans/factory/ObjectProvider.java":
                "package org.springframework.beans.factory; public interface ObjectProvider<T> { T getIfAvailable(); }\n",
            "org/springframework/beans/factory/annotation/Autowired.java":
                "package org.springframework.beans.factory.annotation; public @interface Autowired {}\n",
            "org/slf4j/Logger.java":
                "package org.slf4j; public interface Logger { default void info(String s,Object... a){} default void warn(String s,Object... a){} default void debug(String s,Object... a){} }\n",
            "org/slf4j/LoggerFactory.java":
                "package org.slf4j; public final class LoggerFactory { private static final Logger L=new Logger(){}; public static Logger getLogger(Class<?> c){return L;} }\n",
            "io/micrometer/tracing/Span.java":
                "package io.micrometer.tracing; public interface Span { default Span name(String s){return this;} default Span start(){return this;} default Span tag(String k,String v){return this;} default void error(Throwable t){} default void end(){} }\n",
            "io/micrometer/tracing/Tracer.java":
                "package io.micrometer.tracing; public interface Tracer { interface SpanInScope extends AutoCloseable { default void close(){} } Span nextSpan(); SpanInScope withSpan(Span s); }\n",
        }
        for relative, content in files.items():
            path = stubs / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")

        harness.write_text(r'''
import java.util.*;
import com.opensocket.aievent.core.dispatch.flow.*;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.routing.governance.*;
import com.opensocket.aievent.core.task.TaskRecord;

public class P2ResolverHarness {
    public static void main(String[] args) {
        Profiles profiles = new Profiles();
        Defaults defaults = new Defaults();
        DispatchOperationProfile profile = new DispatchOperationProfile();
        profile.setProfileId(PlatformOperationProfiles.ANALYSIS_AND_PROPOSAL_ID);
        profile.setScopeType(OperationProfileScope.PLATFORM);
        profile.setProfileCode(PlatformOperationProfiles.ANALYSIS_AND_PROPOSAL_CODE);
        profile.setProfileName("Analysis and proposal");
        profile.setOperations(Set.of(DispatchOperation.READ, DispatchOperation.ANALYZE, DispatchOperation.PROPOSE));
        profile.setEffectfulOperationsAllowed(false);
        profile.setStatus(DispatchGovernanceStatus.ACTIVE.name());
        profiles.save(profile);
        defaults.save(SourceSystemDispatchDefault.analysisBaseline("tenant-random", "SRC_RANDOM_8F2A", "actor"));

        GenericDispatchRequirementResolver resolver = new GenericDispatchRequirementResolver(defaults, profiles);
        TaskRecord task = task();
        FlowRuleRoutingPlan sourceDefault = plan(CapabilityRequirementMode.SOURCE_DEFAULT, "LEGACY_COMPATIBILITY_CAPABILITY");
        DispatchRequirementResolution baseline = resolver.resolve(task, sourceDefault);
        check(baseline.getResolutionMode() == RequirementResolutionMode.SOURCE_BASELINE, "source baseline mode");
        check(baseline.getOutcome() == RequirementDecisionStatus.RESOLVED, "source baseline outcome");
        check(baseline.getRequiredCapabilities().isEmpty(), "source baseline must not inherit compatibility capability");
        check(baseline.getRequiredOperations().contains(DispatchOperation.ANALYZE), "analysis operation");

        FlowRuleRoutingPlan effectful = plan(CapabilityRequirementMode.SOURCE_DEFAULT, "LEGACY_COMPATIBILITY_CAPABILITY");
        effectful.setRequiredOperation(DispatchOperation.REMEDIATE.name());
        effectful.setSideEffectLevel(SideEffectLevel.REVERSIBLE_WRITE.name());
        DispatchRequirementResolution blocked = resolver.resolve(task, effectful);
        check(blocked.getOutcome() == RequirementDecisionStatus.BLOCKED, "effectful source baseline must block");

        FlowRuleRoutingPlan explicit = plan(CapabilityRequirementMode.EXPLICIT, "CUSTOM_REVIEW_ANALYSIS_TRIAGE");
        DispatchRequirementResolution explicitResult = resolver.resolve(task, explicit);
        check(explicitResult.getRequiredCapabilities().equals(List.of("CUSTOM_REVIEW_ANALYSIS_TRIAGE")), "opaque explicit capability");

        TaskRecord retryTask = task();
        retryTask.setMatchedFlowId("flow-random");
        retryTask.setMatchedRuleId("rule-random");
        retryTask.setRequestedSkill("AUTHORITATIVE_RETRY_CAPABILITY");
        FlowRuleRoutingService retryResolver = new FlowRuleRoutingService(query -> {
            FlowRuleRuntimeMatch match = new FlowRuleRuntimeMatch();
            match.setFlowId("FLOW-RANDOM"); match.setRuleId("RULE-RANDOM");
            match.setRequestedSkill("DIFFERENT_PERSISTED_COMPATIBILITY_CAPABILITY");
            match.setRequiredSkills(List.of("DIFFERENT_PERSISTED_COMPATIBILITY_CAPABILITY"));
            match.setCapabilityRequirementMode(CapabilityRequirementMode.SOURCE_DEFAULT.name());
            match.setRequiredOperation(DispatchOperation.ANALYZE.name());
            match.setSideEffectLevel(SideEffectLevel.NONE.name());
            match.setCandidatePoolMode(CandidatePoolMode.SOURCE_SYSTEM_POOL.name());
            match.setExplicitActionAuthorizationRequired(false);
            match.setRequirementModelVersion(2);
            return Optional.of(match);
        });
        FlowRuleRoutingPlan enrichedRetryPlan = retryResolver.resolve(retryTask);
        check("AUTHORITATIVE_RETRY_CAPABILITY".equals(enrichedRetryPlan.getRequestedSkill()), "retry capability evidence unchanged");
        check(enrichedRetryPlan.getRequiredSkills().equals(List.of("AUTHORITATIVE_RETRY_CAPABILITY")), "retry required skills unchanged");
        check(CapabilityRequirementMode.SOURCE_DEFAULT.name().equals(enrichedRetryPlan.getCapabilityRequirementMode()), "retry requirement contract enriched");

        EvidenceRepo evidence = new EvidenceRepo();
        ComparisonRepo comparisons = new ComparisonRepo();
        RoutingProperties properties = new RoutingProperties();
        DispatchRequirementShadowService shadow = new DispatchRequirementShadowService(
            resolver, new DispatchRequirementShadowPersistenceService(evidence, comparisons), properties);
        String originalTaskSkill = task.getRequestedSkill();
        String originalPlanSkill = sourceDefault.getRequestedSkill();
        shadow.observe(task, sourceDefault);
        check(Objects.equals(originalTaskSkill, task.getRequestedSkill()), "task unchanged");
        check(Objects.equals(originalPlanSkill, sourceDefault.getRequestedSkill()), "plan unchanged");
        check(evidence.values.size() == 1, "evidence persisted");
        check(evidence.values.get(0).getDecisionStatus() == RequirementDecisionStatus.SHADOW_ONLY, "shadow-only evidence");
        check(comparisons.values.size() == 1 && !comparisons.values.get(0).isEquivalent(), "difference persisted");
        System.out.println("P2 resolver harness passed");
    }

    static TaskRecord task() {
        TaskRecord task = new TaskRecord();
        task.setTenantId("tenant-random"); task.setTaskId("task-random"); task.setSourceSystem("SRC_RANDOM_8F2A");
        task.setRequestedSkill("LEGACY_COMPATIBILITY_CAPABILITY");
        task.setRequiredCapabilities(List.of("LEGACY_COMPATIBILITY_CAPABILITY"));
        return task;
    }
    static FlowRuleRoutingPlan plan(CapabilityRequirementMode mode, String compatibility) {
        FlowRuleRoutingPlan plan = new FlowRuleRoutingPlan();
        plan.setMatched(true); plan.setFlowId("flow-random"); plan.setRuleId("rule-random"); plan.setRoutingPath("FLOW_RULE");
        plan.setCapabilityRequirementMode(mode.name()); plan.setRequestedSkill(compatibility); plan.setRequiredSkills(List.of(compatibility));
        plan.setRequiredOperation(DispatchOperation.ANALYZE.name()); plan.setSideEffectLevel(SideEffectLevel.NONE.name());
        plan.setCandidatePoolMode(CandidatePoolMode.SOURCE_SYSTEM_POOL.name()); plan.setExplicitActionAuthorizationRequired(true);
        plan.setRequirementModelVersion(2); return plan;
    }
    static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    static final class Profiles implements DispatchOperationProfileRepository {
        final Map<String,DispatchOperationProfile> values=new LinkedHashMap<>();
        public DispatchOperationProfile save(DispatchOperationProfile v){v.validate();values.put(v.getProfileId(),v);return v;}
        public Optional<DispatchOperationProfile> findById(String id){return Optional.ofNullable(values.get(id));}
        public Optional<DispatchOperationProfile> findEffectiveByCode(String tenant,String code){return values.values().stream().filter(v->code.equals(v.getProfileCode())).findFirst();}
        public List<DispatchOperationProfile> listEffective(String tenant,String status,int limit){return values.values().stream().limit(limit).toList();}
    }
    static final class Defaults implements SourceSystemDispatchDefaultRepository {
        final Map<String,SourceSystemDispatchDefault> values=new LinkedHashMap<>();
        public SourceSystemDispatchDefault save(SourceSystemDispatchDefault v){v.validate();values.put(v.getTenantId()+"|"+v.getSourceSystem(),v);return v;}
        public Optional<SourceSystemDispatchDefault> findBySourceSystem(String tenant,String source){return Optional.ofNullable(values.get(tenant+"|"+source));}
        public List<SourceSystemDispatchDefault> list(String tenant,String status,int limit){return values.values().stream().limit(limit).toList();}
    }
    static final class EvidenceRepo implements TaskRequirementEvidenceRepository {
        final List<TaskRequirementEvidence> values=new ArrayList<>();
        public TaskRequirementEvidence append(TaskRequirementEvidence v){v.validate();values.add(v);return v;}
        public List<TaskRequirementEvidence> findByTaskId(String t,String id,int l){return values;}
        public List<TaskRequirementEvidence> recent(String t,int l){return values;}
    }
    static final class ComparisonRepo implements TaskRequirementShadowComparisonRepository {
        final List<TaskRequirementShadowComparison> values=new ArrayList<>();
        public TaskRequirementShadowComparison append(TaskRequirementShadowComparison v){v.validate();values.add(v);return v;}
        public List<TaskRequirementShadowComparison> findByTaskId(String t,String id,int l){return values;}
        public List<TaskRequirementShadowComparison> recent(String t,boolean d,int l){return values;}
    }
}
''', encoding="utf-8")

        sources = [str(path) for path in stubs.rglob("*.java")]
        sources += [
            str(path) for path in (ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance").glob("*.java")
        ]
        sources += [str(ROOT / value) for value in [
            "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingPlan.java",
            "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingRepository.java",
            "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRuntimeMatch.java",
            "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRuntimeQuery.java",
            "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/event/EventSeverity.java",
            "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskRecord.java",
            "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskType.java",
            "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskStatus.java",
            "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskPriority.java",
            "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/RoutingPolicy.java",
            "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/EligibilityEngineMode.java",
            "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/cutover/DispatchCutoverMode.java",
            "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingProperties.java",
            "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/DispatchOperationProfileRepository.java",
            "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/SourceSystemDispatchDefaultRepository.java",
            "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/TaskRequirementEvidenceRepository.java",
            "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/TaskRequirementShadowComparisonRepository.java",
            "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/DispatchRequirementResolver.java",
            "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/GenericDispatchRequirementResolver.java",
            "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/DispatchRequirementShadowPersistenceService.java",
            "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/DispatchRequirementShadowService.java",
            "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java",
        ]]
        sources.append(str(harness))
        output.mkdir(parents=True, exist_ok=True)
        compile_result = subprocess.run([javac, "-d", str(output), *sources], cwd=ROOT)
        if compile_result.returncode != 0:
            fail("P2 resolver/shadow integration compilation failed")
        run_result = subprocess.run([java, "-cp", str(output), "P2ResolverHarness"], cwd=ROOT)
        if run_result.returncode != 0:
            fail("P2 resolver harness failed")


def main() -> int:
    migration = "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V116__p2_1_requirement_shadow_comparison.sql"
    require(migration, [
        "task_requirement_shadow_comparisons",
        "shadow_resolution_mode",
        "shadow_outcome",
        "difference_types_json",
        "dispatch_p2_requirement_shadow_difference_report",
        "dispatch_p2_requirement_shadow_summary",
        "append-only",
        "before update or delete on task_requirement_shadow_comparisons",
    ])
    forbid_named_sources(migration)

    compatibility_migration = "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V117__p2_2_shadow_runtime_compatibility_guard.sql"
    require(compatibility_migration, [
        "ck_dispatch_policies_p2_shadow_legacy_bridge",
        "requested_skill is not null",
        "ck_dispatch_policies_p2_effectful_authorization",
        "ck_dispatch_policies_p2_requirement_version",
        "dispatch_p2_shadow_rule_readiness",
        "P2_SHADOW_READY",
    ])
    forbid_named_sources(compatibility_migration)

    model_base = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/"
    for name in [
        "DispatchRequirementResolution.java",
        "TaskRequirementShadowComparison.java",
        "RequirementShadowDifferenceType.java",
    ]:
        forbid_named_sources(model_base + name)

    resolver = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/GenericDispatchRequirementResolver.java"
    require(resolver, [
        "case EXPLICIT",
        "case SOURCE_DEFAULT",
        "case NONE",
        "SOURCE_DEFAULT_POLICY_NOT_CONFIGURED",
        "EXPLICIT_CAPABILITY_REQUIRED_FOR_EFFECTFUL_TASK",
        "CAPABILITY_FREE_EFFECTFUL_TASK_NOT_ALLOWED",
        "CandidatePoolMode.SOURCE_SYSTEM_POOL",
    ])
    forbid_named_sources(resolver)

    shadow = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/DispatchRequirementShadowService.java"
    shadow_text = require(shadow, [
        "RequirementDecisionStatus.SHADOW_ONLY",
        "authoritativeRuntimeUnchanged",
        "SOURCE_BASELINE_REPLACES_LEGACY_CAPABILITY",
        "requirement_shadow_failed",
        "authoritativeRoutingUnchanged=true",
    ])
    for forbidden_mutation in ("task.set", "authoritativeLegacyPlan.set"):
        if forbidden_mutation in shadow_text:
            fail(f"P2 shadow observer mutates authoritative input: {forbidden_mutation}")
    forbid_named_sources(shadow)

    persistence = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/DispatchRequirementShadowPersistenceService.java"
    require(persistence, [
        "Propagation.REQUIRES_NEW",
        "evidenceRepository.append(evidence)",
        "comparisonRepository.append(comparison)",
    ])

    flow_service = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java"
    require(flow_service, [
        "observeRequirementShadow(task, plan)",
        "DispatchRequirementShadowService",
        "plan.setCapabilityRequirementMode",
        "plan.setRequiredOperation",
        "plan.setSideEffectLevel",
        "plan.setCandidatePoolMode",
        "enrichRequirementContractFromPersistedRule",
        "authoritativeRoutingUnchanged=true",
    ])

    jdbc_flow = "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java"
    require(jdbc_flow, [
        "capability_requirement_mode",
        "required_operation",
        "side_effect_level",
        "candidate_pool_mode",
        "explicit_action_authorization_required",
        "requirement_model_version",
        "match.setCapabilityRequirementMode",
    ])

    management = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java"
    p10_cutover = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V133__p10_4_remove_shadow_compatibility_guard.sql"
    if p10_cutover.is_file():
        require(management, [
            "CapabilityRequirementMode.SOURCE_DEFAULT",
            "CandidatePoolMode.SOURCE_SYSTEM_POOL",
            "Math.max(10, rule.getRequirementModelVersion())",
        ])
        require(str(p10_cutover.relative_to(ROOT)), [
            "drop constraint if exists ck_dispatch_policies_p2_shadow_legacy_bridge",
            "dispatch_validate_p10_authoritative_rule",
        ])
    else:
        require(management, [
            "P2 shadow mode requires requestedSkill as an authoritative legacy compatibility bridge",
        ])

    controller = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchGovernanceController.java"
    require(controller, [
        '@GetMapping("/tasks/{taskId}/requirement-shadow-comparisons")',
        '@GetMapping("/requirement-shadow-comparisons")',
        "differencesOnly",
    ])

    require("ai-event-gateway-core/control-plane-app/src/main/resources/application.yml", [
        "requirement-resolver-shadow-enabled",
        "requirement-resolver-shadow-persist-evidence",
    ])

    require("ai-event-gateway-core/architecture/table-ownership.csv", [
        "task_requirement_shadow_comparisons,task-orchestration,com.opensocket.aievent.core.routing.governance.TaskRequirementShadowComparisonRepository",
    ])
    require("ai-event-gateway-core/architecture/baseline/m8-cross-context-repository-imports.csv", [
        "JdbcTaskRequirementShadowComparisonRepository.java,com.opensocket.aievent.core.routing.governance.TaskRequirementShadowComparisonRepository",
    ])

    require(
        "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingServiceCharacterizationTest.java",
        [
            "shouldEnrichRetryTaskRequirementContractWithoutChangingAuthoritativeRoutingEvidence",
            "CAP_AUTHORITATIVE_RETRY_EVIDENCE",
            "CAP_DIFFERENT_FROM_AUTHORITATIVE_EVIDENCE",
            "SOURCE_SYSTEM_POOL",
        ],
    )

    require(
        "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/routing/governance/GenericDispatchRequirementResolverTest.java",
        [
            "sourceDefaultResolvesOpaqueSourceToAnalysisAndProposalWithoutCapability",
            "sourceDefaultEffectfulTaskIsBlockedWithoutExplicitCapabilityAndActionAuthorization",
            "explicitModeUsesPersistedCapabilityWithoutInspectingItsName",
            "noneModeAllowsNonEffectfulAnalysisWithoutCapability",
            "missingSourceDefaultFailsClosedInShadowDecision",
        ],
    )
    require(
        "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/routing/governance/DispatchRequirementShadowServiceTest.java",
        [
            "persistsShadowEvidenceAndComparisonWithoutMutatingAuthoritativeTaskOrPlan",
            "shadowFailureDoesNotPropagateOrMutateAuthoritativeInputs",
        ],
    )

    compile_and_run_harness()

    p0_guard = ROOT / "scripts/architecture/zero_special_case_guard.py"
    result = subprocess.run([sys.executable, str(p0_guard)], cwd=ROOT)
    if result.returncode != 0:
        fail("P0 zero-special-case guard failed after P2 changes")

    print("[PASS] P2 generic requirement resolver shadow mode, isolated evidence, and difference reporting verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
