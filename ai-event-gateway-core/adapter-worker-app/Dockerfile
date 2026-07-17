FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/adapter-worker-app-*.jar /app/adapter-worker.jar
EXPOSE 18090
ENTRYPOINT ["java","-jar","/app/adapter-worker.jar"]
