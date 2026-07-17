#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAVAC="${JAVAC:-$(command -v javac || true)}"
if [[ -z "$JAVAC" ]]; then echo "[WARN] javac unavailable; P8 adapter compile skipped"; exit 0; fi
TMP="$(mktemp -d -t p8-action-adapters-XXXXXX)"; trap 'rm -rf "$TMP"' EXIT
SRC="$TMP/src"; OUT="$TMP/classes"; mkdir -p "$SRC" "$OUT"
w(){ mkdir -p "$(dirname "$SRC/$1")"; cat > "$SRC/$1"; }

w org/springframework/dao/EmptyResultDataAccessException.java <<'JAVA'
package org.springframework.dao; public class EmptyResultDataAccessException extends RuntimeException { public EmptyResultDataAccessException(){super();} }
JAVA
w org/springframework/jdbc/core/RowMapper.java <<'JAVA'
package org.springframework.jdbc.core; public interface RowMapper<T>{ T mapRow(java.sql.ResultSet rs,int rowNum) throws java.sql.SQLException; }
JAVA
w org/springframework/jdbc/core/namedparam/MapSqlParameterSource.java <<'JAVA'
package org.springframework.jdbc.core.namedparam; public class MapSqlParameterSource { public MapSqlParameterSource addValue(String key,Object value){ return this; } }
JAVA
w org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplate.java <<'JAVA'
package org.springframework.jdbc.core.namedparam; import java.util.*; import org.springframework.jdbc.core.RowMapper; public class NamedParameterJdbcTemplate { public int update(String sql,MapSqlParameterSource p){return 0;} public <T> List<T> query(String sql,MapSqlParameterSource p,RowMapper<T> mapper){return List.of();} public <T> T queryForObject(String sql,MapSqlParameterSource p,RowMapper<T> mapper){return null;} }
JAVA
w com/fasterxml/jackson/databind/ObjectMapper.java <<'JAVA'
package com.fasterxml.jackson.databind; public class ObjectMapper {}
JAVA
w com/opensocket/aievent/database/persistence/spi/DatabaseRepositoryAdapter.java <<'JAVA'
package com.opensocket.aievent.database.persistence.spi; public @interface DatabaseRepositoryAdapter {}
JAVA
w com/opensocket/aievent/database/persistence/dispatch/governance/DispatchGovernanceJdbcJson.java <<'JAVA'
package com.opensocket.aievent.database.persistence.dispatch.governance; import java.util.*; import com.fasterxml.jackson.databind.ObjectMapper; public class DispatchGovernanceJdbcJson { public DispatchGovernanceJdbcJson(ObjectMapper mapper){} public Map<String,Object> readMap(String value){return new LinkedHashMap<>();} public String write(Object value){return "{}";} }
JAVA
for ann in RestController RequestMapping GetMapping PostMapping PutMapping RequestParam PathVariable RequestBody; do
w org/springframework/web/bind/annotation/$ann.java <<JAVA
package org.springframework.web.bind.annotation; public @interface $ann { String value() default ""; boolean required() default true; String defaultValue() default ""; }
JAVA
done
w com/opensocket/aievent/core/routing/governance/action/ActionGovernanceService.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance.action; import java.time.*; import java.util.*;
public class ActionGovernanceService {
 public List<ActionCatalogEntry> listActions(String t,String s,int l){return List.of();} public ActionCatalogEntry saveAction(String t,String c,ActionCatalogEntry r,String a){return r;}
 public List<AgentActionGrant> searchGrants(String t,String a,String s,String c,String st,int l){return List.of();} public AgentActionGrant saveGrant(String t,String id,AgentActionGrant r,String a){return r;} public AgentActionGrant approveGrant(String t,String id,String a){return null;} public AgentActionGrant revokeGrant(String t,String id,String a){return null;}
 public List<ProposedAction> searchProposals(String t,String task,String s,int l){return List.of();} public ProposedAction createProposal(String t,ProposedAction p,String a){return p;} public ActionApprovalRequest submitProposal(String t,String p,String a,OffsetDateTime e){return null;} public ActionApprovalRequest decideApproval(String t,String r,ActionApprovalDecisionType d,String a,String reason){return null;}
 public List<ActionApprovalRequest> searchApprovalRequests(String t,String s,int l){return List.of();} public List<ActionApprovalDecision> approvalDecisions(String t,String r){return List.of();} public ActionTaskMaterializationResult materializeAndDispatch(String t,String p,String a){return null;} public List<EffectfulActionEvidence> evidence(String t,String p,int l){return List.of();} public EffectfulActionTaskLink recordExecutionResult(String t,String id,boolean s,String a,Map<String,Object> r){return null;}
 public List<EffectfulActionTaskLink> runtimeActions(String t,String s,int l){return List.of();} public EffectfulActionRuntimeMetrics runtimeMetrics(String t){return null;} public ActionRuntimeRecoveryResult processRuntimeDeadlines(OffsetDateTime at,int l){return null;} public EffectfulActionTaskLink requestCancellation(String t,String id,String a,String r){return null;} public ProposedAction prepareCompensation(String t,String id,String a){return null;} public List<EffectfulActionManualCase> manualCases(String t,String s,int l){return List.of();} public EffectfulActionManualCase acknowledgeManualCase(String t,String id,String a){return null;} public EffectfulActionManualCase resolveManualCase(String t,String id,String a,String r,boolean w){return null;}
}
JAVA

GOV="$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance"
ACTION="$GOV/action"
ACTUAL=("$GOV/DispatchOperation.java" "$GOV/SideEffectLevel.java" "$GOV/DispatchApprovalStatus.java" "$GOV/DispatchGovernanceStatus.java")
while IFS= read -r f; do [[ "$f" == *"ActionGovernanceService.java" ]] || ACTUAL+=("$f"); done < <(find "$ACTION" -maxdepth 1 -name '*.java' | sort)
ACTUAL+=(
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/ActionGovernanceRepository.java"
 "$ROOT/ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/action/JdbcActionGovernanceRepository.java"
 "$ROOT/ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ActionGovernanceController.java"
)
STUBS=()
while IFS= read -r stub; do
  STUBS+=("${stub}")
done < <(find "$SRC" -name '*.java' | sort)
"$JAVAC" -d "$OUT" "${STUBS[@]}" "${ACTUAL[@]}"
echo "P8 action repository adapter/controller compile harness passed"
