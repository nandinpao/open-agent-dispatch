#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAVAC="${JAVAC:-$(command -v javac || true)}"
if [[ -z "$JAVAC" ]]; then echo "[WARN] Java unavailable; P9 runtime adapter compile skipped"; exit 0; fi
TMP="$(mktemp -d -t p9-runtime-adapters-XXXXXX)"; trap 'rm -rf "$TMP"' EXIT
SRC="$TMP/src"; OUT="$TMP/out"; mkdir -p "$SRC" "$OUT"
w(){ mkdir -p "$(dirname "$SRC/$1")"; cat > "$SRC/$1"; }
w org/springframework/stereotype/Component.java <<'JAVA'
package org.springframework.stereotype; public @interface Component {}
JAVA
w org/springframework/scheduling/annotation/Scheduled.java <<'JAVA'
package org.springframework.scheduling.annotation; public @interface Scheduled { String fixedDelayString() default ""; }
JAVA
w org/springframework/boot/context/properties/ConfigurationProperties.java <<'JAVA'
package org.springframework.boot.context.properties; public @interface ConfigurationProperties { String prefix(); }
JAVA
w com/opensocket/aievent/core/events/ModuleEvent.java <<'JAVA'
package com.opensocket.aievent.core.events; public interface ModuleEvent { String eventType(); String aggregateType(); String aggregateId(); }
JAVA
w com/opensocket/aievent/core/outbox/ModuleEventHandler.java <<'JAVA'
package com.opensocket.aievent.core.outbox; public interface ModuleEventHandler<T> { String eventType(); Class<T> payloadType(); void handle(T event); }
JAVA
w com/opensocket/aievent/core/routing/governance/action/ActionGovernanceService.java <<'JAVA'
package com.opensocket.aievent.core.routing.governance.action; import java.time.*; import com.opensocket.aievent.core.events.TaskCallbackAcceptedEvent; public class ActionGovernanceService { public void handleAcceptedCallback(TaskCallbackAcceptedEvent event){} public ActionRuntimeRecoveryResult processRuntimeDeadlines(OffsetDateTime at,int limit){return new ActionRuntimeRecoveryResult(limit,0,0,0);} }
JAVA
STUBS=()
while IFS= read -r stub; do
  STUBS+=("${stub}")
done < <(find "$SRC" -name '*.java' | sort)
"$JAVAC" -d "$OUT" "${STUBS[@]}" \
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/events/TaskCallbackAcceptedEvent.java" \
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/action/ActionRuntimeRecoveryResult.java" \
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/EffectfulActionRuntimeProperties.java" \
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/EffectfulActionCallbackEventHandler.java" \
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/ScheduledEffectfulActionRuntimeRecovery.java"
echo "P9 effectful Action event/recovery adapter compile harness passed"
