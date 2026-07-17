#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
SRC="$TMP/src"; OUT="$TMP/out"; mkdir -p "$SRC" "$OUT"
w(){ mkdir -p "$SRC/$(dirname "$1")"; cat > "$SRC/$1"; }
w org/springframework/stereotype/Service.java <<'JAVA'
package org.springframework.stereotype; public @interface Service {}
JAVA
w org/springframework/stereotype/Repository.java <<'JAVA'
package org.springframework.stereotype; public @interface Repository {}
JAVA
w org/springframework/transaction/annotation/Transactional.java <<'JAVA'
package org.springframework.transaction.annotation; public @interface Transactional {}
JAVA
for a in RestController RequestMapping GetMapping PostMapping RequestParam RequestBody; do
w org/springframework/web/bind/annotation/$a.java <<JAVA
package org.springframework.web.bind.annotation; public @interface $a { String value() default ""; boolean required() default true; String defaultValue() default ""; }
JAVA
done
w org/springframework/jdbc/core/RowMapper.java <<'JAVA'
package org.springframework.jdbc.core; public interface RowMapper<T>{ T mapRow(java.sql.ResultSet r,int n) throws java.sql.SQLException; }
JAVA
w org/springframework/jdbc/core/JdbcTemplate.java <<'JAVA'
package org.springframework.jdbc.core; import java.util.*; public class JdbcTemplate { public <T> T queryForObject(String s,RowMapper<T> r,Object...a){return null;} public <T> List<T> query(String s,RowMapper<T> r,Object...a){return List.of();} public int update(String s,Object...a){return 1;} }
JAVA
w org/springframework/jdbc/core/namedparam/MapSqlParameterSource.java <<'JAVA'
package org.springframework.jdbc.core.namedparam; public class MapSqlParameterSource { public MapSqlParameterSource addValue(String k,Object v){return this;} }
JAVA
w org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplate.java <<'JAVA'
package org.springframework.jdbc.core.namedparam; import java.util.*; import org.springframework.jdbc.core.*; public class NamedParameterJdbcTemplate { public NamedParameterJdbcTemplate(JdbcTemplate j){} public <T> List<T> query(String s,MapSqlParameterSource p,RowMapper<T> r){return List.of();} }
JAVA
w com/fasterxml/jackson/core/type/TypeReference.java <<'JAVA'
package com.fasterxml.jackson.core.type; public abstract class TypeReference<T> {}
JAVA
w com/fasterxml/jackson/databind/ObjectMapper.java <<'JAVA'
package com.fasterxml.jackson.databind; public class ObjectMapper { public Object readValue(String s,Class<?> c){return null;} public String writeValueAsString(Object v){return "{}";} }
JAVA
MODELS=("$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/enforce/EnforceArtifactRetentionRecord.java" "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/enforce/EnforceLegacyFinalReportItem.java" "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/enforce/EnforceObservabilitySnapshot.java" "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/enforce/EnforceOperatorIncidentRequest.java" "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/enforce/EnforceOperatorIncidentResult.java" "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/enforce/EnforceRoutingAuditRecord.java")
ACTUAL=("${MODELS[@]}" "$ROOT/ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/spi/DatabaseRepositoryAdapter.java" "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/enforce/EnforceOperationsRepository.java" "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/enforce/EnforceOperationsService.java" "$ROOT/ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/enforce/JdbcEnforceOperationsRepository.java" "$ROOT/ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/EnforceOperationsController.java")
STUBS=()
while IFS= read -r stub; do
  STUBS+=("${stub}")
done < <(find "$SRC" -name '*.java' | sort)
javac -d "$OUT" "${STUBS[@]}" "${ACTUAL[@]}"
echo 'P11 enforce operations adapter/controller compile harness passed'
