#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAVAC="${JAVAC:-$(command -v javac || true)}"
JAVA="${JAVA:-$(command -v java || true)}"
if [[ -z "$JAVAC" || -z "$JAVA" ]]; then
  echo "[WARN] Java toolchain unavailable; P4 Java harness skipped"
  exit 0
fi
TMP="$(mktemp -d -t p4-routing-shadow-XXXXXX)"
trap 'rm -rf "$TMP"' EXIT
SRC="$TMP/src"
OUT="$TMP/classes"
mkdir -p "$SRC/org/springframework/stereotype" "$SRC/org/slf4j" \
  "$SRC/com/opensocket/aievent/core/agent" "$SRC/com/opensocket/aievent/core/task" \
  "$SRC/com/opensocket/aievent/core/routing" \
  "$SRC/com/opensocket/aievent/core/routing/governance" \
  "$SRC/com/opensocket/aievent/core/routing/governance/eligibility" \
  "$SRC/com/opensocket/aievent/core/routing/governance/routing" "$OUT"

cat > "$SRC/org/springframework/stereotype/Service.java" <<'JAVA'
package org.springframework.stereotype; public @interface Service {}
JAVA
cat > "$SRC/org/springframework/stereotype/Component.java" <<'JAVA'
package org.springframework.stereotype; public @interface Component {}
JAVA
cat > "$SRC/org/slf4j/Logger.java" <<'JAVA'
package org.slf4j; public interface Logger { default void info(String s,Object...o){} default void warn(String s,Object...o){} }
JAVA
cat > "$SRC/org/slf4j/LoggerFactory.java" <<'JAVA'
package org.slf4j; public final class LoggerFactory { private LoggerFactory(){} public static Logger getLogger(Class<?> c){return new Logger(){};} }
JAVA
cat > "$SRC/com/opensocket/aievent/core/agent/AgentSnapshot.java" <<'JAVA'
package com.opensocket.aievent.core.agent;
public class AgentSnapshot {
 private String id,site; private int health=100,max=1,current,reserved; private double utilization;
 public String getAgentId(){return id;} public void setAgentId(String v){id=v;}
 public String getSiteId(){return site;} public void setSiteId(String v){site=v;}
 public int getHealthScore(){return health;} public void setHealthScore(int v){health=v;}
 public int getMaxConcurrentTasks(){return max;} public void setMaxConcurrentTasks(int v){max=v;}
 public int getEffectiveTaskCount(){return current+reserved;} public void setCurrentTaskCount(int v){current=v;}
 public double getCapacityUtilization(){return utilization;} public void setCapacityUtilization(double v){utilization=v;}
}
JAVA
cat > "$SRC/com/opensocket/aievent/core/agent/AgentDirectoryFacade.java" <<'JAVA'
package com.opensocket.aievent.core.agent; import java.util.Optional; public interface AgentDirectoryFacade { Optional<AgentSnapshot> findById(String id); }
JAVA
cat > "$SRC/com/opensocket/aievent/core/task/TaskRecord.java" <<'JAVA'
package com.opensocket.aievent.core.task;
public class TaskRecord { private String id,site,stage; public String getTaskId(){return id;} public void setTaskId(String v){id=v;} public String getSiteId(){return site;} public void setSiteId(String v){site=v;} public String getEventStage(){return stage;} public void setEventStage(String v){stage=v;} }
JAVA
cat > "$SRC/com/opensocket/aievent/core/routing/AgentCandidateScore.java" <<'JAVA'
package com.opensocket.aievent.core.routing; public record AgentCandidateScore(String agentId,int score) {}
JAVA
cat > "$SRC/com/opensocket/aievent/core/routing/RoutingProperties.java" <<'JAVA'
package com.opensocket.aievent.core.routing;
public class RoutingProperties { private int limit=500; public int getGenericRoutingShadowMaxCandidates(){return limit;} public void setGenericRoutingShadowMaxCandidates(int v){limit=v;} public boolean isGenericRoutingShadowEnabled(){return true;} public boolean isGenericRoutingShadowPersistComparisons(){return false;} }
JAVA
cat > "$SRC/com/opensocket/aievent/core/routing/governance/AgentSourceAssignment.java" <<'JAVA'
package com.opensocket.aievent.core.routing.governance; public class AgentSourceAssignment { private String agentId; public AgentSourceAssignment(String v){agentId=v;} public String getAgentId(){return agentId;} }
JAVA
cat > "$SRC/com/opensocket/aievent/core/routing/governance/AgentSourceAssignmentRepository.java" <<'JAVA'
package com.opensocket.aievent.core.routing.governance; import java.util.List; public interface AgentSourceAssignmentRepository { List<AgentSourceAssignment> search(String tenant,String source,String agent,String status,int limit); }
JAVA
cat > "$SRC/com/opensocket/aievent/core/routing/governance/TaskRequirementEvidence.java" <<'JAVA'
package com.opensocket.aievent.core.routing.governance;
import java.util.*;
public class TaskRequirementEvidence {
 private String tenant="tenant-random",flow="flow-random",rule="rule-random",source="SRC_RANDOM_8F2A",evidence="evidence-random",task="task-random";
 private Map<String,Object> details=new LinkedHashMap<>(); private List<String> capabilities=new ArrayList<>(); private CandidatePoolMode poolMode=CandidatePoolMode.LEGACY; private GenericRoutingStrategy strategy=GenericRoutingStrategy.WEIGHTED_SCORE;
 public String getTenantId(){return tenant;} public String getMatchedFlowId(){return flow;} public String getMatchedRuleId(){return rule;} public String getSourceSystem(){return source;} public String getEvidenceId(){return evidence;} public String getTaskId(){return task;}
 public Map<String,Object> getEvidence(){return details;} public CandidatePoolMode getCandidatePoolMode(){return poolMode;} public void setCandidatePoolMode(CandidatePoolMode value){poolMode=value;}
 public List<String> getRequiredCapabilities(){return capabilities;} public void setRequiredCapabilities(List<String> v){capabilities=v==null?new ArrayList<>():new ArrayList<>(v);}
 public GenericRoutingStrategy getRoutingStrategy(){return strategy;} public void setRoutingStrategy(GenericRoutingStrategy v){strategy=v;}
}
JAVA
cat > "$SRC/com/opensocket/aievent/core/routing/governance/eligibility/TaskAgentEligibilityShadowComparison.java" <<'JAVA'
package com.opensocket.aievent.core.routing.governance.eligibility; import java.util.List; public class TaskAgentEligibilityShadowComparison { public boolean isShadowEligible(){return true;} public List<String> getBlockingReasonCodes(){return List.of();} }
JAVA
cat > "$SRC/com/opensocket/aievent/core/routing/governance/eligibility/DispatchEligibilityShadowService.java" <<'JAVA'
package com.opensocket.aievent.core.routing.governance.eligibility;
import java.time.OffsetDateTime; import com.opensocket.aievent.core.agent.AgentSnapshot; import com.opensocket.aievent.core.task.TaskRecord; import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;
public class DispatchEligibilityShadowService { public TaskRequirementEvidence findLatestRequirement(TaskRecord task){return new TaskRequirementEvidence();} public TaskAgentEligibilityShadowComparison evaluateCandidateForRoutingShadow(TaskRecord t,TaskRequirementEvidence r,AgentSnapshot a,String id,boolean lc,boolean le,Integer score,OffsetDateTime at){return new TaskAgentEligibilityShadowComparison();} }
JAVA
cat > "$SRC/com/opensocket/aievent/core/routing/governance/routing/DispatchRoutingShadowPersistenceService.java" <<'JAVA'
package com.opensocket.aievent.core.routing.governance.routing; import java.util.List; public class DispatchRoutingShadowPersistenceService { public void persist(List<TaskAgentRoutingShadowComparison> values){} }
JAVA
cat > "$SRC/P4RoutingHarness.java" <<'JAVA'
import java.util.*;
import com.opensocket.aievent.core.agent.*;
import com.opensocket.aievent.core.task.*;
import com.opensocket.aievent.core.routing.*;
import com.opensocket.aievent.core.routing.governance.*;
import com.opensocket.aievent.core.routing.governance.routing.*;

public class P4RoutingHarness {
 public static void main(String[] args) {
  scoreStrategies(); candidatePools(); System.out.println("P4 routing harness passed");
 }
 static void scoreStrategies(){
  GenericRoutingScoreCalculator c=new GenericRoutingScoreCalculator(); TaskRecord task=new TaskRecord(); task.setTaskId("task-random"); task.setSiteId("SITE-A");
  TaskRequirementEvidence req=new TaskRequirementEvidence(); req.setRequiredCapabilities(List.of("CAP_RANDOM_ANALYSIS_REVIEW_TRIAGE"));
  AgentSnapshot local=agent("agent-local","SITE-A",95,1,10); AgentSnapshot remote=agent("agent-remote","SITE-B",95,1,10); AgentSnapshot loaded=agent("agent-loaded","SITE-A",95,9,10);
  check(c.score(GenericRoutingStrategy.LOCAL_FIRST,task,req,local).score()>c.score(GenericRoutingStrategy.LOCAL_FIRST,task,req,remote).score(),"local first");
  check(c.score(GenericRoutingStrategy.LOWEST_LOAD,task,req,local).score()>c.score(GenericRoutingStrategy.LOWEST_LOAD,task,req,loaded).score(),"lowest load");
  check(c.score(GenericRoutingStrategy.MANUAL_REVIEW,task,req,local).score()==0,"manual review");
  int a=c.score(GenericRoutingStrategy.ROUND_ROBIN,task,req,local).score(); int b=c.score(GenericRoutingStrategy.ROUND_ROBIN,task,req,local).score(); check(a==b,"deterministic round robin");
 }
 static void candidatePools(){
  Map<String,AgentSnapshot> runtime=new LinkedHashMap<>(); for(String id:List.of("agent-source","agent-explicit","agent-capability")) runtime.put(id,agent(id,"SITE-X",90,0,2));
  AgentSourceAssignmentRepository source=(tenant,sourceSystem,agent,status,limit)->List.of(new AgentSourceAssignment("agent-source"));
  GenericCandidateAgentRepository repo=new GenericCandidateAgentRepository(){ public List<String> findExplicitFlowAgentIds(String t,String f,String s,int l){return List.of("agent-explicit");} public List<String> findCapabilityMatchedAgentIds(String t,List<String> c,int l){return List.of("agent-capability");} };
  AgentDirectoryFacade directory=id->Optional.ofNullable(runtime.get(id)); RoutingProperties props=new RoutingProperties();
  GenericCandidateAgentProvider provider=new GenericCandidateAgentProvider(source,repo,directory,props); TaskRecord task=new TaskRecord(); task.setEventStage("EXTERNAL");
  TaskRequirementEvidence req=new TaskRequirementEvidence(); req.setCandidatePoolMode(CandidatePoolMode.SOURCE_SYSTEM_POOL); check(provider.provide(task,req,List.of()).containsKey("agent-source"),"source pool");
  req.setCandidatePoolMode(CandidatePoolMode.EXPLICIT_FLOW_AGENTS); check(provider.provide(task,req,List.of()).containsKey("agent-explicit"),"explicit pool");
  req.setCandidatePoolMode(CandidatePoolMode.CAPABILITY_MATCHED_POOL); req.setRequiredCapabilities(List.of("CAP_RANDOM_719CD")); check(provider.provide(task,req,List.of()).containsKey("agent-capability"),"capability pool");
  req.setCandidatePoolMode(CandidatePoolMode.LEGACY); AgentSnapshot legacy=agent("agent-legacy",null,80,0,1); check(provider.provide(task,req,List.of(legacy)).containsKey("agent-legacy"),"legacy pool");
 }
 static AgentSnapshot agent(String id,String site,int health,int used,int max){AgentSnapshot a=new AgentSnapshot();a.setAgentId(id);a.setSiteId(site);a.setHealthScore(health);a.setCurrentTaskCount(used);a.setMaxConcurrentTasks(max);a.setCapacityUtilization((double)used/Math.max(1,max));return a;}
 static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
JAVA

ACTUAL=(
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/CandidatePoolMode.java"
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/GenericRoutingStrategy.java"
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/routing/CandidatePoolOrigin.java"
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/routing/RoutingShadowDifferenceType.java"
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/routing/TaskAgentRoutingShadowComparison.java"
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/routing/GenericCandidateAgentRepository.java"
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/routing/GenericCandidateAgent.java"
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/routing/CandidateAgentProvider.java"
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/routing/GenericCandidateAgentProvider.java"
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/routing/GenericRoutingScore.java"
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/routing/GenericRoutingScoreCalculator.java"
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/routing/DispatchRoutingShadowService.java"
)
"$JAVAC" -d "$OUT" $(find "$SRC" -name '*.java' | sort) "${ACTUAL[@]}"
"$JAVA" -cp "$OUT" P4RoutingHarness
