#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAVAC="${JAVAC:-$(command -v javac || true)}"
JAVA="${JAVA:-$(command -v java || true)}"
if [[ -z "$JAVAC" || -z "$JAVA" ]]; then
  echo "[WARN] Java toolchain unavailable; P5 Java harness skipped"
  exit 0
fi
TMP="$(mktemp -d -t p5-runtime-fail-closed-XXXXXX)"
trap 'rm -rf "$TMP"' EXIT
SRC="$TMP/src"; OUT="$TMP/classes"
mkdir -p "$SRC" "$OUT"
write() { mkdir -p "$(dirname "$SRC/$1")"; cat > "$SRC/$1"; }
write org/springframework/stereotype/Service.java <<'EOF'
package org.springframework.stereotype; public @interface Service {}
EOF
write org/springframework/beans/factory/annotation/Autowired.java <<'EOF'
package org.springframework.beans.factory.annotation; public @interface Autowired {}
EOF
write org/springframework/beans/factory/ObjectProvider.java <<'EOF'
package org.springframework.beans.factory; public interface ObjectProvider<T>{ T getIfAvailable(); }
EOF
write org/springframework/boot/context/properties/ConfigurationProperties.java <<'EOF'
package org.springframework.boot.context.properties; public @interface ConfigurationProperties { String prefix(); }
EOF
write com/opensocket/aievent/core/agent/skill/AgentSkillDefinition.java <<'EOF'
package com.opensocket.aievent.core.agent.skill; public class AgentSkillDefinition { private String code; public AgentSkillDefinition(){} public AgentSkillDefinition(String c){code=c;} public String getSkillCode(){return code;} }
EOF
write com/opensocket/aievent/core/agent/skill/AgentSkillRegistryService.java <<'EOF'
package com.opensocket.aievent.core.agent.skill; import java.util.*; public class AgentSkillRegistryService { public java.util.List<AgentSkillDefinition> search(String d, boolean e){return List.of();} }
EOF
write com/opensocket/aievent/core/agent/assignment/DispatchEventTaskMapping.java <<'EOF'
package com.opensocket.aievent.core.agent.assignment; public class DispatchEventTaskMapping { public String getMappingId(){return null;} public String getSourceSystem(){return null;} public String getTaskType(){return null;} public String getCapabilityCode(){return null;} }
EOF
write com/opensocket/aievent/core/agent/assignment/DispatchTaskDefinition.java <<'EOF'
package com.opensocket.aievent.core.agent.assignment; public class DispatchTaskDefinition { public String getDefinitionId(){return null;} public String getSourceSystem(){return null;} public String getTaskType(){return null;} }
EOF
write com/opensocket/aievent/core/agent/assignment/AgentAssignmentProfile.java <<'EOF'
package com.opensocket.aievent.core.agent.assignment; import java.util.*; public class AgentAssignmentProfile { public boolean isActive(){return false;} public String getSourceSystem(){return null;} public String getTaskType(){return null;} public java.util.List<String> getAllowedTaskTypes(){return List.of();} public String getTaskDefinitionId(){return null;} public String getProfileCode(){return null;} public String getProfileName(){return null;} }
EOF
write com/opensocket/aievent/core/agent/assignment/AssignmentProfileCapabilityBinding.java <<'EOF'
package com.opensocket.aievent.core.agent.assignment; public class AssignmentProfileCapabilityBinding { public boolean isActive(){return false;} public boolean isRequired(){return false;} public String getCapabilityCode(){return null;} }
EOF
write com/opensocket/aievent/core/agent/assignment/AgentAssignmentRepository.java <<'EOF'
package com.opensocket.aievent.core.agent.assignment; import java.util.*; public interface AgentAssignmentRepository { Optional<DispatchEventTaskMapping> findBestEventTaskMapping(String t,String s,String o,String e,String c,String m); List<DispatchTaskDefinition> searchTaskDefinitions(String t,String st,int l); List<AgentAssignmentProfile> searchProfiles(String t,String a,Boolean b,int l); List<AssignmentProfileCapabilityBinding> findCapabilityBindings(String t,String p,Boolean a); }
EOF
write P5RuntimeFailClosedHarness.java <<'EOF'
import java.util.*;
import com.opensocket.aievent.core.agent.recipe.*;
import com.opensocket.aievent.core.agent.skill.*;
import com.opensocket.aievent.core.routing.*;
public class P5RuntimeFailClosedHarness {
 public static void main(String[] args) {
  DispatchRecipeRepository repo=new DispatchRecipeRepository(){
   public List<DispatchRecipe> search(String d,boolean e){return List.of();}
   public Optional<DispatchRecipe> findByCode(String c){return Optional.empty();}
   public DispatchRecipe upsert(DispatchRecipe r){return r;} public boolean delete(String c){return false;} public String mode(){return "EMPTY";}
  };
  TaskCapabilityResolverService resolver=new TaskCapabilityResolverService(repo,new AgentSkillRegistryService());
  TaskCapabilityResolveRequest request=new TaskCapabilityResolveRequest(); request.setTenantId("tenant-random"); request.setSourceSystem("SOURCE_RANDOM_1001"); request.setEventType("EVENT_RANDOM");
  TaskCapabilityResolveResult result=resolver.resolve(request);
  if(result.getPrimaryCapability()!=null || !result.getRequiredCapabilities().isEmpty()) throw new IllegalStateException("unconfigured source received an implicit capability");
  if(result.isFallback() || result.getResolutionReasons().stream().noneMatch(v->v.contains("NO_CONFIGURED_DISPATCH_CONTRACT"))) throw new IllegalStateException("missing fail-closed evidence");
  try { TaskCapabilityResolveRequest bad=new TaskCapabilityResolveRequest(); bad.setSourceSystem("SOURCE_RANDOM"); resolver.resolve(bad); throw new IllegalStateException("missing tenant accepted"); } catch(IllegalArgumentException expected){}
  RoutingProperties properties=new RoutingProperties();
  if(!properties.isZeroSpecialCaseRuntimeEnabled() || properties.isFlowRuleLegacyFallbackEnabled()) throw new IllegalStateException("P5 Stage 8 flow authority safety flags invalid");
  if(RoutingPolicy.valueOf("CAPABILITY_FIRST")!=RoutingPolicy.CAPABILITY_FIRST) throw new IllegalStateException("generic policy missing");
  System.out.println("P5 runtime fail-closed harness passed");
 }
}
EOF
ACTUAL=(
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/recipe/DispatchRecipe.java"
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/recipe/DispatchRecipeRepository.java"
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolveRequest.java"
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolveResult.java"
 "$ROOT/ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolverService.java"
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/RoutingPolicy.java"
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/EligibilityEngineMode.java"
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/cutover/DispatchCutoverMode.java"
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingProperties.java"
)
STUBS=()
while IFS= read -r stub; do
  STUBS+=("$stub")
done < <(find "$SRC" -name '*.java' -print)
"$JAVAC" -d "$OUT" "${STUBS[@]}" "${ACTUAL[@]}"
"$JAVA" -cp "$OUT" P5RuntimeFailClosedHarness
