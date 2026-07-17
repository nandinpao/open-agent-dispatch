#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAVAC="${JAVAC:-$(command -v javac || true)}"; JAVA="${JAVA:-$(command -v java || true)}"
if [[ -z "$JAVAC" || -z "$JAVA" ]]; then echo "[WARN] Java unavailable; P9 callback acceptance guard harness skipped"; exit 0; fi
TMP="$(mktemp -d -t p9-callback-guard-XXXXXX)"; trap 'rm -rf "$TMP"' EXIT
SRC="$TMP/src"; OUT="$TMP/out"; mkdir -p "$SRC" "$OUT"
w(){ mkdir -p "$(dirname "$SRC/$1")"; cat > "$SRC/$1"; }
w org/springframework/stereotype/Component.java <<'JAVA'
package org.springframework.stereotype; public @interface Component {}
JAVA
w com/opensocket/aievent/core/routing/governance/action/ActionGovernanceRepository.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance.action; import java.util.*; public interface ActionGovernanceRepository { Optional<EffectfulActionTaskLink> findTaskLinkByTaskForUpdate(String tenantId,String taskId); }
JAVA
w com/opensocket/aievent/core/routing/governance/action/ActionGovernanceService.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance.action; import com.opensocket.aievent.core.callback.*; public class ActionGovernanceService { public int rejected; public String reason; public void recordCallbackGuardRejection(TaskCallbackGuardContext c,String r,String m){rejected++;reason=r;} }
JAVA
w P9CallbackGuardHarness.java <<'JAVA'
import java.time.*; import java.util.*;
import com.opensocket.aievent.core.callback.*;
import com.opensocket.aievent.core.routing.governance.action.*;
public class P9CallbackGuardHarness {
 public static void main(String[] args){ Repo repo=new Repo(); ActionGovernanceService service=new ActionGovernanceService(); EffectfulActionCallbackAcceptanceGuard guard=new EffectfulActionCallbackAcceptanceGuard(repo,service);
  TaskCallbackGuardContext ordinary=ctx("ordinary","agent-random",Map.of("externalExecutionKey","anything")); require(guard.evaluate(ordinary).allowed(),"ordinary callback must pass");
  EffectfulActionTaskLink link=link("action-task","agent-random","exec-random"); repo.link=link;
  require(guard.evaluate(ctx("action-task","agent-random",Map.of("externalExecutionKey","exec-random"))).allowed(),"valid effectful callback");
  require(guard.evaluate(ctx("action-task","agent-random",Map.of("agentCallback",Map.of("externalExecutionKey","exec-random")))).allowed(),"relay-nested execution key");
  require(!guard.evaluate(ctx("action-task","agent-random",Map.of())).allowed(),"missing execution key blocked"); require(ActionRuntimeReasonCode.EXTERNAL_EXECUTION_KEY_REQUIRED.name().equals(service.reason),"missing key reason");
  require(!guard.evaluate(ctx("action-task","agent-random",Map.of("externalExecutionKey","wrong"))).allowed(),"mismatch blocked"); require(ActionRuntimeReasonCode.EXTERNAL_EXECUTION_KEY_MISMATCH.name().equals(service.reason),"mismatch reason");
  require(!guard.evaluate(ctx("action-task","other-agent",Map.of("externalExecutionKey","exec-random"))).allowed(),"agent mismatch blocked"); require(ActionRuntimeReasonCode.CALLBACK_AGENT_MISMATCH.name().equals(service.reason),"agent reason");
  link.setStatus(EffectfulActionHandoffStatus.GRANT_REVOKED); require(!guard.evaluate(ctx("action-task","agent-random",Map.of("externalExecutionKey","exec-random"))).allowed(),"revoked action blocked");
  require(service.rejected==4,"every blocked callback recorded"); System.out.println("P9 callback acceptance guard harness passed"); }
 static TaskCallbackGuardContext ctx(String task,String agent,Map<String,Object> payload){return new TaskCallbackGuardContext("tenant-random",task,"callback-"+UUID.randomUUID(),TaskCallbackType.RESULT,"dispatch-random","assignment-random",agent,"idem-random",payload,OffsetDateTime.now());}
 static EffectfulActionTaskLink link(String task,String agent,String key){EffectfulActionTaskLink l=new EffectfulActionTaskLink();l.setTenantId("tenant-random");l.setLinkId("link-random");l.setProposalId("proposal-random");l.setApprovalRequestId("approval-random");l.setActionCode("ACTION_RANDOM");l.setActionTaskId(task);l.setAnalysisTaskId("analysis-random");l.setTargetAgentId(agent);l.setSelectedAgentId(agent);l.setActionGrantId("grant-random");l.setIdempotencyKey("idem-link");l.setExternalExecutionKey(key);l.setPayloadHash("hash-random");l.setMaterializedBy("actor-random");l.setStatus(EffectfulActionHandoffStatus.DISPATCHED);return l;}
 static void require(boolean ok,String label){if(!ok)throw new IllegalStateException(label);}
 static final class Repo implements ActionGovernanceRepository { EffectfulActionTaskLink link; public Optional<EffectfulActionTaskLink> findTaskLinkByTaskForUpdate(String t,String task){return link!=null&&task.equals(link.getActionTaskId())?Optional.of(link):Optional.empty();} }
}
JAVA
STUBS=()
while IFS= read -r stub; do
  STUBS+=("${stub}")
done < <(find "$SRC" -name '*.java' | sort)
"$JAVAC" -d "$OUT" "${STUBS[@]}" \
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackType.java" \
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackGuardContext.java" \
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackGuardDecision.java" \
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackAcceptanceGuard.java" \
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/action/EffectfulActionHandoffStatus.java" \
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/action/EffectfulActionTaskLink.java" \
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/action/ActionRuntimeReasonCode.java" \
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/EffectfulActionCallbackAcceptanceGuard.java"
"$JAVA" -cp "$OUT" P9CallbackGuardHarness
