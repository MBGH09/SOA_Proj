FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY gateway/pom.xml ./gateway/pom.xml
COPY gateway/src ./gateway/src

WORKDIR /workspace/gateway
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/gateway/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
