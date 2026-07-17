# syntax=docker/dockerfile:1.7

# Runtime image for the pre-built Netty gateway Spring Boot executable jar.
# Build the jar first with:
#   mvn -f ai-event-gateway-netty/pom.xml -pl gateway-app -am package -DskipTests
# Then build from repository root with:
#   docker build -t ai-event-gateway-netty:local -f ai-event-gateway-netty/gateway-app/Dockerfile ai-event-gateway-netty

FROM eclipse-temurin:25-jdk AS verify
WORKDIR /verify

COPY gateway-app/target/gateway-app-*.jar /verify/ai-event-gateway-netty.jar

RUN set -eux; \
    jar tf /verify/ai-event-gateway-netty.jar | grep -E "BOOT-INF/lib/protocol-[^/]+\\.jar"; \
    jar tf /verify/ai-event-gateway-netty.jar | grep -E "BOOT-INF/lib/gateway-core-[^/]+\\.jar"; \
    jar tf /verify/ai-event-gateway-netty.jar | grep -E "BOOT-INF/lib/transport-server-[^/]+\\.jar"; \
    jar tf /verify/ai-event-gateway-netty.jar | grep -E "BOOT-INF/lib/admin-api-[^/]+\\.jar"; \
    jar tf /verify/ai-event-gateway-netty.jar | grep "com/opensocket/aievent/gateway/netty/AiEventGatewayNettyApplication.class"

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/* \
  && addgroup --system --gid 10001 app \
  && adduser --system --uid 10001 --ingroup app app

COPY --from=verify /verify/ai-event-gateway-netty.jar /app/ai-event-gateway-netty.jar

USER app
EXPOSE 18081 19090 19091
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS http://127.0.0.1:18081/api/admin/health >/dev/null || curl -fsS http://127.0.0.1:18081/actuator/health >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/ai-event-gateway-netty.jar"]
