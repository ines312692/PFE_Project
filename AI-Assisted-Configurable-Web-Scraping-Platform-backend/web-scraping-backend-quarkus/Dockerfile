FROM maven:3.9-eclipse-temurin-21-alpine AS dependencies

WORKDIR /build

COPY pom.xml ./

RUN mvn dependency:go-offline \
      --no-transfer-progress \
      -q

FROM dependencies AS builder

COPY src/ ./src/

RUN mvn package \
      --no-transfer-progress \
      -DskipTests \
      -Dquarkus.package.type=fast-jar \
      -q

FROM maven:3.9-eclipse-temurin-21-alpine AS dev

WORKDIR /app


COPY --from=dependencies /root/.m2 /root/.m2

COPY pom.xml ./
COPY src/    ./src/

ENV QUARKUS_PROFILE=dev \
    QUARKUS_HTTP_PORT=8080 \
    QUARKUS_LIVE_RELOAD_ENABLED=true \
    QUARKUS_DEV_UI_HOSTS=localhost,quarkus-backend \
    MAVEN_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080
EXPOSE 5005

HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/q/health/live || exit 1

CMD ["mvn", "quarkus:dev", \
     "-Ddebug=5005", \
     "--no-transfer-progress", \
     "-Dquarkus.http.host=0.0.0.0"]


FROM eclipse-temurin:21-jre-alpine AS prod

LABEL org.opencontainers.image.title="Scraping Platform — Backend" \
      org.opencontainers.image.description="Quarkus JVM REST API" \
      org.opencontainers.image.version="1.0.0"


RUN addgroup -g 1001 -S appgroup && \
    adduser  -u 1001 -S appuser -G appgroup

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup \
     /build/target/quarkus-app/ ./


RUN chmod -R 550 /app && \
    mkdir  -p /app/logs && \
    chown  appuser:appgroup /app/logs && \
    chmod  750 /app/logs

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/q/health/live || exit 1


ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+UseG1GC \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8" \
    QUARKUS_PROFILE=prod \
    QUARKUS_HTTP_PORT=8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar quarkus-run.jar"]