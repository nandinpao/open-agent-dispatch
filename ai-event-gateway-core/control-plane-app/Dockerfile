# This Dockerfile intentionally verifies the pre-built Spring Boot jar before creating
# the runtime image. It prevents Docker from packaging a stale target/*.jar that still
# contains old SharedUtility artifacts such as database-1.0-SNAPSHOT.jar.

FROM eclipse-temurin:25-jdk AS verify
WORKDIR /verify

ARG EXPECTED_SHARED_UTILITY_VERSION=1.0.0
COPY control-plane-app/target/control-plane-app-*.jar /verify/ai-event-gateway-core.jar

RUN set -eux; \
    jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/kernel-[^/]+\.jar"; \
    jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/contracts-[^/]+\.jar"; \
    platform_jar="$(jar tf /verify/ai-event-gateway-core.jar | grep -E 'BOOT-INF/lib/database-platform-[^/]+\.jar' | head -n 1)"; \
    data_model_jar="$(jar tf /verify/ai-event-gateway-core.jar | grep -E 'BOOT-INF/lib/data-model-[^/]+\.jar' | head -n 1)"; \
    test -n "$platform_jar"; \
    test -n "$data_model_jar"; \
    jar xf /verify/ai-event-gateway-core.jar "$platform_jar" "$data_model_jar"; \
    jar tf "/verify/$platform_jar" | grep "db/migration/V1__incident_store.sql"; \
    jar tf "/verify/$platform_jar" | grep "db/migration/V20__dispatch_claim_lease.sql"; \
    jar tf "/verify/$data_model_jar" | grep "com/opensocket/aievent/database/persistence/incident/dao/IncidentDao.class"; \
    jar tf "/verify/$platform_jar" | grep "mybatis/postgresql/incident/IncidentDao.xml"; \
    ! jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/ai-event-gateway-core-db-support-[^/]+\.jar"; \
    ! jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/ai-event-gateway-core-db-migration-[^/]+\.jar"; \
    jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/domain-events-[^/]+\.jar"; \
    jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/service-contracts-[^/]+\.jar"; \
    jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/integration-events-[^/]+\.jar"; \
    jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/incident-[^/]+\.jar"; \
    jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/event-processing-[^/]+\.jar"; \
    jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/agent-control-[^/]+\.jar"; \
    jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/task-orchestration-[^/]+\.jar"; \
    jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/execution-control-[^/]+\.jar"; \
    jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/adapter-action-[^/]+\.jar"; \
    jar tf /verify/ai-event-gateway-core.jar | grep -E "BOOT-INF/lib/observability-[^/]+\.jar"; \
    jar tf /verify/ai-event-gateway-core.jar | grep "BOOT-INF/lib/database-${EXPECTED_SHARED_UTILITY_VERSION}.jar"; \
    jar tf /verify/ai-event-gateway-core.jar | grep "BOOT-INF/lib/redisson-client-${EXPECTED_SHARED_UTILITY_VERSION}.jar"; \
    ! jar tf /verify/ai-event-gateway-core.jar | grep -E 'BOOT-INF/lib/(database|redisson-client)-1\.0-SNAPSHOT\.jar'; \
    jar xf /verify/ai-event-gateway-core.jar "BOOT-INF/lib/database-${EXPECTED_SHARED_UTILITY_VERSION}.jar"; \
    javap -classpath "/verify/BOOT-INF/lib/database-${EXPECTED_SHARED_UTILITY_VERSION}.jar" -verbose com.agitg.database.DatabaseClusterConfig | grep 'pg.single'; \
    ! javap -classpath "/verify/BOOT-INF/lib/database-${EXPECTED_SHARED_UTILITY_VERSION}.jar" -verbose com.agitg.database.DatabaseClusterConfig | grep 'No pg.master or pg.read configuration found'

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/* \
  && addgroup --system --gid 10001 app \
  && adduser --system --uid 10001 --ingroup app app

COPY --from=verify /verify/ai-event-gateway-core.jar /app/ai-event-gateway-core.jar

USER app
EXPOSE 18080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -fsS http://127.0.0.1:18080/actuator/health >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/ai-event-gateway-core.jar"]
