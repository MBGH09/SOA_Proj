FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY service-donneurs/pom.xml ./service-donneurs/pom.xml
COPY service-donneurs/src ./service-donneurs/src

WORKDIR /workspace/service-donneurs
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/service-donneurs/target/*.jar /app/app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
