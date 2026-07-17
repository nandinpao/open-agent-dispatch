#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
SRC="$TMP/src"; OUT="$TMP/out"; mkdir -p "$SRC" "$OUT"
w(){ mkdir -p "$SRC/$(dirname "$1")"; cat > "$SRC/$1"; }
w org/springframework/stereotype/Service.java <<'JAVA'
package org.springframework.stereotype; public @interface Service {}
JAVA
w com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingPlan.java <<'JAVA'
package com.opensocket.aievent.core.dispatch.flow; public class FlowRuleRoutingPlan { private boolean matched=true; public boolean isMatched(){return matched;} public void setMatched(boolean v){matched=v;} }
JAVA
w com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java <<'JAVA'
package com.opensocket.aievent.core.dispatch.flow; import com.opensocket.aievent.core.task.TaskRecord; public class FlowRuleRoutingService { public FlowRuleRoutingPlan plan=new FlowRuleRoutingPlan(); public FlowRuleRoutingPlan resolve(TaskRecord t){return plan;} }
JAVA
w com/opensocket/aievent/core/agent/AgentSnapshot.java <<'JAVA'
package com.opensocket.aievent.core.agent; public class AgentSnapshot { private String id; public AgentSnapshot(String v){id=v;} public String getAgentId(){return id;} public String getOwnerGatewayNodeId(){return "gw";} public String getAgentSessionId(){return "session";} public String getSiteId(){return "site";} public Status getStatus(){return Status.READY;} public int getRuntimeFailureCount(){return 0;} public enum Status { READY } }
JAVA
w com/opensocket/aievent/core/routing/AgentCandidateScore.java <<'JAVA'
package com.opensocket.aievent.core.routing; import java.util.*; public record AgentCandidateScore(String agentId,String ownerGatewayNodeId,String agentSessionId,String siteId,String status,int score,List<String> requiredCapabilities,List<String> missingCapabilities,String reason,Map<String,Object> scoreBreakdown) {}
JAVA
w com/opensocket/aievent/core/routing/RoutingProperties.java <<'JAVA'
package com.opensocket.aievent.core.routing; public class RoutingProperties { public boolean isPoisonAgentExclusionEnabled(){return true;} public int getPoisonAgentFailureThreshold(){return 5;} public int getMinimumScore(){return 50;} }
JAVA
w com/opensocket/aievent/core/routing/governance/GenericRoutingStrategy.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance; public enum GenericRoutingStrategy { WEIGHTED_SCORE, MANUAL_REVIEW }
JAVA
w com/opensocket/aievent/core/routing/governance/RequirementDecisionStatus.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance; public enum RequirementDecisionStatus { RESOLVED, BLOCKED }
JAVA
w com/opensocket/aievent/core/routing/governance/TaskRequirementEvidence.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance; import java.util.*; public class TaskRequirementEvidence { private RequirementDecisionStatus status=RequirementDecisionStatus.RESOLVED; private GenericRoutingStrategy strategy=GenericRoutingStrategy.WEIGHTED_SCORE; public RequirementDecisionStatus getDecisionStatus(){return status;} public void setDecisionStatus(RequirementDecisionStatus v){status=v;} public GenericRoutingStrategy getRoutingStrategy(){return strategy;} public void setRoutingStrategy(GenericRoutingStrategy v){strategy=v;} public List<String> getRequiredCapabilities(){return List.of("CAP_RANDOM");} public String getResolutionMode(){return "SOURCE_BASELINE";} public String getReasonCode(){return null;} }
JAVA
w com/opensocket/aievent/core/routing/governance/DispatchRequirementAuthoritativeService.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance; import com.opensocket.aievent.core.task.TaskRecord; import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingPlan; public class DispatchRequirementAuthoritativeService { public TaskRequirementEvidence value=new TaskRequirementEvidence(); public TaskRequirementEvidence resolveAndPersist(TaskRecord t,FlowRuleRoutingPlan p){return value;} }
JAVA
w com/opensocket/aievent/core/routing/governance/eligibility/EligibilityShadowCheckOutcome.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance.eligibility; public enum EligibilityShadowCheckOutcome { PASS, BLOCK }
JAVA
w com/opensocket/aievent/core/routing/governance/eligibility/AgentEligibilityShadowCheck.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance.eligibility; public class AgentEligibilityShadowCheck { public String getEvaluatorCode(){return "GENERIC";} public EligibilityShadowCheckOutcome getOutcome(){return EligibilityShadowCheckOutcome.PASS;} public String getReasonCode(){return "PASS";} }
JAVA
w com/opensocket/aievent/core/routing/governance/eligibility/TaskAgentEligibilityShadowComparison.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance.eligibility; import java.util.*; public class TaskAgentEligibilityShadowComparison { private boolean eligible=true; public boolean isShadowEligible(){return eligible;} public void setShadowEligible(boolean v){eligible=v;} public List<String> getBlockingReasonCodes(){return eligible?List.of():List.of("BLOCKED");} public List<AgentEligibilityShadowCheck> getChecks(){return List.of(new AgentEligibilityShadowCheck());} }
JAVA
w com/opensocket/aievent/core/routing/governance/eligibility/GenericDispatchEligibilityService.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance.eligibility; import java.time.*; import com.opensocket.aievent.core.agent.AgentSnapshot; import com.opensocket.aievent.core.task.TaskRecord; import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence; public class GenericDispatchEligibilityService { public boolean eligible=true; public TaskAgentEligibilityShadowComparison evaluateCandidateAuthoritatively(TaskRecord t,TaskRequirementEvidence r,AgentSnapshot a,String id,OffsetDateTime at){TaskAgentEligibilityShadowComparison c=new TaskAgentEligibilityShadowComparison();c.setShadowEligible(eligible);return c;} }
JAVA
w com/opensocket/aievent/core/routing/governance/routing/CandidatePoolOrigin.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance.routing; public enum CandidatePoolOrigin { SOURCE_ASSIGNMENT }
JAVA
w com/opensocket/aievent/core/routing/governance/routing/CandidateAgentProvider.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance.routing; import java.util.*; import com.opensocket.aievent.core.agent.AgentSnapshot; import com.opensocket.aievent.core.task.TaskRecord; import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence; public interface CandidateAgentProvider { Map<String,GenericCandidateAgent> provide(TaskRecord t,TaskRequirementEvidence r,List<AgentSnapshot> l); }
JAVA
w com/opensocket/aievent/core/routing/governance/routing/GenericRoutingScoreCalculator.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance.routing; import com.opensocket.aievent.core.agent.AgentSnapshot; import com.opensocket.aievent.core.task.TaskRecord; import com.opensocket.aievent.core.routing.governance.*; public class GenericRoutingScoreCalculator { public GenericRoutingScore score(GenericRoutingStrategy s,TaskRecord t,TaskRequirementEvidence r,AgentSnapshot a){return new GenericRoutingScore(88,java.util.Map.of("score",88));} }
JAVA
w com/opensocket/aievent/core/task/TaskRecord.java <<'JAVA'
package com.opensocket.aievent.core.task; public class TaskRecord { private String id="task-random"; public String getTaskId(){return id;} }
JAVA
w harness/P10GenericRoutingHarness.java <<'JAVA'
package harness; import java.util.*; import com.opensocket.aievent.core.agent.*; import com.opensocket.aievent.core.dispatch.flow.*; import com.opensocket.aievent.core.routing.*; import com.opensocket.aievent.core.routing.cutover.*; import com.opensocket.aievent.core.routing.governance.*; import com.opensocket.aievent.core.routing.governance.eligibility.*; import com.opensocket.aievent.core.routing.governance.routing.*; import com.opensocket.aievent.core.task.*;
public final class P10GenericRoutingHarness { public static void main(String[] a){FlowRuleRoutingService flow=new FlowRuleRoutingService();DispatchRequirementAuthoritativeService req=new DispatchRequirementAuthoritativeService();GenericDispatchEligibilityService eligibility=new GenericDispatchEligibilityService();CandidateAgentProvider pool=(t,r,l)->{GenericCandidateAgent c=new GenericCandidateAgent("agent-random");c.setRuntime(new AgentSnapshot("agent-random"));c.addOrigin(CandidatePoolOrigin.SOURCE_ASSIGNMENT);return Map.of("agent-random",c);};GenericDispatchAuthoritativeService service=new GenericDispatchAuthoritativeService(flow,req,pool,eligibility,new GenericRoutingScoreCalculator(),new RoutingProperties());GenericAuthoritativeRoutingResult selected=service.route(new TaskRecord(),Set.of());require(selected.status()==GenericAuthoritativeRoutingResult.Status.SELECTED,"selected");require("agent-random".equals(selected.selected().agentId()),"agent");req.value.setDecisionStatus(RequirementDecisionStatus.BLOCKED);require(service.route(new TaskRecord(),Set.of()).status()==GenericAuthoritativeRoutingResult.Status.REQUIREMENT_BLOCKED,"blocked");req.value.setDecisionStatus(RequirementDecisionStatus.RESOLVED);req.value.setRoutingStrategy(GenericRoutingStrategy.MANUAL_REVIEW);require(service.route(new TaskRecord(),Set.of()).status()==GenericAuthoritativeRoutingResult.Status.MANUAL_REVIEW,"manual");System.out.println("P10 generic authoritative routing harness passed");}static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);} }
JAVA
ACTUAL=("$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/cutover/GenericAuthoritativeRoutingResult.java" "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/cutover/GenericDispatchAuthoritativeService.java" "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/routing/GenericCandidateAgent.java" "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/routing/GenericRoutingScore.java")
STUBS=()
while IFS= read -r stub; do
  STUBS+=("${stub}")
done < <(find "$SRC" -name '*.java' | sort)
javac -d "$OUT" "${STUBS[@]}" "${ACTUAL[@]}"
java -cp "$OUT" harness.P10GenericRoutingHarness
