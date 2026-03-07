# LinkForge backend monolith image
# - build stage: Maven + JDK17
# - runtime stage: JRE17

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml /app/pom.xml
COPY src /app/src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV SERVER_PORT=8080
EXPOSE 8080

COPY --from=build /app/target/linkforge-server-0.1.0-SNAPSHOT.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

