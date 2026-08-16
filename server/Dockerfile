# syntax=docker/dockerfile:1.7
# LinkForge backend modular-monolith image
# - build stage: Maven + JDK17
# - runtime stage: JRE17

ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-17
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre

FROM ${MAVEN_IMAGE} AS build
WORKDIR /app

# Copy Maven reactor modules (keep it explicit for better layer caching)
COPY pom.xml /app/pom.xml
COPY foundation /app/foundation
COPY contracts /app/contracts
COPY accounts /app/accounts
COPY shortlink /app/shortlink
COPY redirect /app/redirect
COPY analytics /app/analytics
COPY platform /app/platform
COPY governance /app/governance
COPY app /app/app

# Build executable jar from the Spring Boot module only.
# BuildKit cache keeps Maven artifacts across image rebuilds and reduces first-run network flakiness.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests -pl app -am package \
 && cp app/target/linkforge-server-*-exec.jar /app/app.jar

FROM ${RUNTIME_IMAGE}
WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

ENV SERVER_PORT=8080
EXPOSE 8080 8081

COPY --from=build /app/app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
