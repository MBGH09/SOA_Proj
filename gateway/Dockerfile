FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY gateway/pom.xml gateway/pom.xml
COPY gateway/src gateway/src

WORKDIR /workspace/gateway
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && apk add --no-cache wget
USER app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/gateway/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=12 CMD wget -q -O - http://127.0.0.1:8080/actuator/health | grep -q UP
ENTRYPOINT ["java", "-jar", "app.jar"]
