#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAVAC="${JAVAC:-$(command -v javac || true)}"
if [[ -z "$JAVAC" ]]; then echo "[WARN] javac unavailable; P10 adapter compile skipped"; exit 0; fi
TMP="$(mktemp -d -t p10-cutover-adapters-XXXXXX)"; trap 'rm -rf "$TMP"' EXIT
SRC="$TMP/src"; OUT="$TMP/classes"; mkdir -p "$SRC" "$OUT"
w(){ mkdir -p "$(dirname "$SRC/$1")"; cat > "$SRC/$1"; }

w org/springframework/jdbc/core/RowMapper.java <<'JAVA'
package org.springframework.jdbc.core; public interface RowMapper<T>{ T mapRow(java.sql.ResultSet rs,int rowNum) throws java.sql.SQLException; }
JAVA
w org/springframework/jdbc/core/namedparam/MapSqlParameterSource.java <<'JAVA'
package org.springframework.jdbc.core.namedparam; public class MapSqlParameterSource { public MapSqlParameterSource addValue(String key,Object value){return this;} }
JAVA
w org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplate.java <<'JAVA'
package org.springframework.jdbc.core.namedparam; import java.util.*; import org.springframework.jdbc.core.RowMapper; public class NamedParameterJdbcTemplate { public int update(String sql,MapSqlParameterSource p){return 0;} public <T> List<T> query(String sql,MapSqlParameterSource p,RowMapper<T> mapper){return List.of();} }
JAVA
w com/opensocket/aievent/database/persistence/spi/DatabaseRepositoryAdapter.java <<'JAVA'
package com.opensocket.aievent.database.persistence.spi; public @interface DatabaseRepositoryAdapter {}
JAVA
for ann in RestController RequestMapping GetMapping PostMapping PutMapping RequestParam PathVariable RequestBody; do
w org/springframework/web/bind/annotation/$ann.java <<JAVA
package org.springframework.web.bind.annotation; public @interface $ann { String value() default ""; boolean required() default true; String defaultValue() default ""; }
JAVA
done
w com/opensocket/aievent/core/routing/cutover/DispatchCutoverService.java <<'JAVA'
package com.opensocket.aievent.core.routing.cutover; import java.util.*; public class DispatchCutoverService { public List<DispatchCutoverPolicy> policies(String t,int l){return List.of();} public DispatchCutoverPolicy savePolicy(String t,String id,DispatchCutoverPolicy p,String a){return p;} public DispatchCutoverReadiness readiness(String t,String f){return DispatchCutoverReadiness.empty(t,f);} public DispatchCutoverPolicy rollback(String t,String id,String r,String a){return null;} }
JAVA

CUT="$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/cutover"
ACTUAL=(
 "$CUT/DispatchCutoverMode.java"
 "$CUT/DispatchCutoverPolicyStatus.java"
 "$CUT/DispatchCutoverPolicy.java"
 "$CUT/DispatchCutoverReadiness.java"
 "$CUT/DispatchCutoverDecision.java"
 "$CUT/DispatchCutoverOutcome.java"
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/cutover/DispatchCutoverRepository.java"
 "$ROOT/ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/cutover/JdbcDispatchCutoverRepository.java"
 "$ROOT/ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchCutoverController.java"
)
STUBS=()
while IFS= read -r stub; do
  STUBS+=("${stub}")
done < <(find "$SRC" -name '*.java' | sort)
"$JAVAC" -d "$OUT" "${STUBS[@]}" "${ACTUAL[@]}"
echo "P10 cutover repository/controller compile harness passed"
