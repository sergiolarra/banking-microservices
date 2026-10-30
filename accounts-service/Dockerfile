# syntax=docker/dockerfile:1
# Multi-stage build (ADR-019): Maven compiles the jar, a JRE-only image runs it.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Dependencies first, so this layer is cached until the pom changes
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY lombok.config .
COPY src src
# Tests already run in CI (mvn verify); integration tests also need Docker
RUN mvn -B -ntp package -DskipTests && mv target/accounts-service-*.jar target/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/app.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
