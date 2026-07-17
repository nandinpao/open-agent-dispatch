#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
SRC=$TMP/src; OUT=$TMP/out; mkdir -p "$SRC" "$OUT"
w(){ mkdir -p "$(dirname "$SRC/$1")"; cat > "$SRC/$1"; }
w org/springframework/stereotype/Service.java <<'JAVA'
package org.springframework.stereotype; public @interface Service {}
JAVA
w org/springframework/transaction/annotation/Transactional.java <<'JAVA'
package org.springframework.transaction.annotation; public @interface Transactional {}
JAVA
for a in RestController RequestMapping GetMapping PostMapping RequestParam PathVariable RequestBody; do
w org/springframework/web/bind/annotation/$a.java <<JAVA
package org.springframework.web.bind.annotation; public @interface $a { String value() default ""; boolean required() default true; String defaultValue() default ""; }
JAVA
done
w org/springframework/dao/EmptyResultDataAccessException.java <<'JAVA'
package org.springframework.dao; public class EmptyResultDataAccessException extends RuntimeException { public EmptyResultDataAccessException(){super();} }
JAVA
w org/springframework/jdbc/core/RowMapper.java <<'JAVA'
package org.springframework.jdbc.core; public interface RowMapper<T>{ T mapRow(java.sql.ResultSet r,int n) throws java.sql.SQLException; }
JAVA
w org/springframework/jdbc/core/namedparam/MapSqlParameterSource.java <<'JAVA'
package org.springframework.jdbc.core.namedparam; public class MapSqlParameterSource { public MapSqlParameterSource addValue(String k,Object v){return this;} }
JAVA
w org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplate.java <<'JAVA'
package org.springframework.jdbc.core.namedparam; import java.util.*; import org.springframework.jdbc.core.RowMapper; public class NamedParameterJdbcTemplate { public <T> List<T> query(String s,MapSqlParameterSource p,RowMapper<T> r){return List.of();} public <T> T queryForObject(String s,MapSqlParameterSource p,RowMapper<T> r){return null;} public int update(String s,MapSqlParameterSource p){return 0;} }
JAVA
w com/fasterxml/jackson/databind/ObjectMapper.java <<'JAVA'
package com.fasterxml.jackson.databind; public class ObjectMapper {}
JAVA
w com/opensocket/aievent/database/persistence/spi/DatabaseRepositoryAdapter.java <<'JAVA'
package com.opensocket.aievent.database.persistence.spi; public @interface DatabaseRepositoryAdapter {}
JAVA
w com/opensocket/aievent/database/persistence/dispatch/governance/DispatchGovernanceJdbcJson.java <<'JAVA'
package com.opensocket.aievent.database.persistence.dispatch.governance; import java.util.*; import com.fasterxml.jackson.databind.ObjectMapper; public class DispatchGovernanceJdbcJson { public DispatchGovernanceJdbcJson(ObjectMapper o){} public Map<String,Object> readMap(String s){return new LinkedHashMap<>();} public String write(Object o){return "{}";} }
JAVA
MODELS=()
while IFS= read -r model; do
  MODELS+=("${model}")
done < <(find "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/migration" -name '*.java' | sort)
STUBS=()
while IFS= read -r stub; do
  STUBS+=("${stub}")
done < <(find "$SRC" -name '*.java' | sort)
javac -d "$OUT" "${STUBS[@]}" \
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/PlatformOperationProfiles.java" \
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/CandidatePoolMode.java" \
 "$ROOT/ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/GenericRoutingStrategy.java" \
 "${MODELS[@]}" \
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/migration/DispatchDataMigrationRepository.java" \
 "$ROOT/ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/migration/DispatchDataMigrationService.java" \
 "$ROOT/ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/migration/JdbcDispatchDataMigrationRepository.java" \
 "$ROOT/ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchDataMigrationController.java"
echo 'P6 migration adapter/controller compile harness passed'
