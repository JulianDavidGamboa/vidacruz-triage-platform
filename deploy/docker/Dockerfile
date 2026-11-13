#   docker build -f deploy/docker/Dockerfile --build-arg SERVICE=triage-service .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace

COPY pom.xml ./
COPY contracts contracts
COPY services services

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl services/${SERVICE} -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /workspace/services/${SERVICE}/target/*.jar app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
