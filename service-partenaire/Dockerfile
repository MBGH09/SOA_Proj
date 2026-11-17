FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY service-partenaire/pom.xml ./service-partenaire/pom.xml
COPY service-partenaire/src ./service-partenaire/src

WORKDIR /workspace/service-partenaire
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/service-partenaire/target/*.jar /app/app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
