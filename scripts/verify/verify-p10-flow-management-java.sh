#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAVAC="${JAVAC:-$(command -v javac || true)}"
if [[ -z "$JAVAC" ]]; then echo "[WARN] javac unavailable; P10 Flow management compile skipped"; exit 0; fi
TMP="$(mktemp -d -t p10-flow-management-XXXXXX)"; trap 'rm -rf "$TMP"' EXIT
SRC="$TMP/src"; OUT="$TMP/classes"; mkdir -p "$SRC" "$OUT"
w(){ mkdir -p "$(dirname "$SRC/$1")"; cat > "$SRC/$1"; }
w org/slf4j/Logger.java <<'JAVA'
package org.slf4j; public interface Logger { default void info(String f,Object...a){} default void warn(String f,Object...a){} default void error(String f,Object...a){} }
JAVA
w org/slf4j/LoggerFactory.java <<'JAVA'
package org.slf4j; public final class LoggerFactory { private static final Logger L=new Logger(){}; public static Logger getLogger(Class<?> c){return L;} }
JAVA
w org/springframework/stereotype/Service.java <<'JAVA'
package org.springframework.stereotype; public @interface Service {}
JAVA
w org/springframework/transaction/annotation/Transactional.java <<'JAVA'
package org.springframework.transaction.annotation; public @interface Transactional {}
JAVA
w org/springframework/dao/DataAccessException.java <<'JAVA'
package org.springframework.dao; public class DataAccessException extends RuntimeException { public DataAccessException(){super();} public DataAccessException(String s){super(s);} }
JAVA
w org/springframework/dao/EmptyResultDataAccessException.java <<'JAVA'
package org.springframework.dao; public class EmptyResultDataAccessException extends DataAccessException { public EmptyResultDataAccessException(){super();} }
JAVA
w org/springframework/jdbc/core/RowMapper.java <<'JAVA'
package org.springframework.jdbc.core; public interface RowMapper<T>{ T mapRow(java.sql.ResultSet rs,int rowNum) throws java.sql.SQLException; }
JAVA
w org/springframework/jdbc/core/namedparam/MapSqlParameterSource.java <<'JAVA'
package org.springframework.jdbc.core.namedparam; public class MapSqlParameterSource { public MapSqlParameterSource addValue(String key,Object value){return this;} }
JAVA
w org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplate.java <<'JAVA'
package org.springframework.jdbc.core.namedparam; import java.util.*; import org.springframework.jdbc.core.RowMapper; public class NamedParameterJdbcTemplate { public int update(String sql,MapSqlParameterSource p){return 0;} public <T> List<T> query(String sql,MapSqlParameterSource p,RowMapper<T> mapper){return List.of();} public <T> T queryForObject(String sql,MapSqlParameterSource p,RowMapper<T> mapper){return null;} }
JAVA
w com/fasterxml/jackson/core/JsonProcessingException.java <<'JAVA'
package com.fasterxml.jackson.core; public class JsonProcessingException extends Exception { public JsonProcessingException(String s){super(s);} }
JAVA
w com/fasterxml/jackson/core/type/TypeReference.java <<'JAVA'
package com.fasterxml.jackson.core.type; public abstract class TypeReference<T> {}
JAVA
w com/fasterxml/jackson/databind/ObjectMapper.java <<'JAVA'
package com.fasterxml.jackson.databind; import com.fasterxml.jackson.core.*; import com.fasterxml.jackson.core.type.*; public class ObjectMapper { public String writeValueAsString(Object v) throws JsonProcessingException{return "{}";} public <T>T readValue(String v,TypeReference<T> t) throws JsonProcessingException{return null;} }
JAVA

FLOW="$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow"
ACTUAL=(
 "$FLOW/DispatchFlowView.java"
 "$FLOW/DispatchFlowRuleView.java"
 "$FLOW/DispatchFlowRequiredSkillView.java"
 "$FLOW/DispatchFlowAgentView.java"
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/CapabilityRequirementMode.java"
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/CandidatePoolMode.java"
 "$ROOT/ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java"
)
STUBS=()
while IFS= read -r stub; do
  STUBS+=("${stub}")
done < <(find "$SRC" -name '*.java' | sort)
"$JAVAC" -d "$OUT" "${STUBS[@]}" "${ACTUAL[@]}"
echo "P10 Dispatch Flow management compile harness passed"
