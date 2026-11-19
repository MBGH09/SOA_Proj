FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY service-donneurs/pom.xml service-donneurs/pom.xml
COPY service-donneurs/src service-donneurs/src

WORKDIR /workspace/service-donneurs
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && apk add --no-cache wget
USER app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/service-donneurs/target/*.jar app.jar
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=12 CMD wget -q -O - http://127.0.0.1:8081/actuator/health | grep -q UP
ENTRYPOINT ["java", "-jar", "app.jar"]
