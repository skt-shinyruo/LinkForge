# LinkForge backend modular-monolith image
# - build stage: Maven + JDK17
# - runtime stage: JRE17

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy Maven reactor modules (keep it explicit for better layer caching)
COPY pom.xml /app/pom.xml
COPY foundation /app/foundation
COPY contracts /app/contracts
COPY accounts /app/accounts
COPY shortlink /app/shortlink
COPY redirect /app/redirect
COPY analytics /app/analytics
COPY app /app/app

# Build executable jar from the Spring Boot module only
RUN mvn -q -DskipTests -pl app -am package \
 && cp app/target/linkforge-server-*.jar /app/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV SERVER_PORT=8080
EXPOSE 8080

COPY --from=build /app/app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
