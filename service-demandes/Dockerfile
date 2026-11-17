FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY service-demandes/pom.xml ./service-demandes/pom.xml
COPY service-demandes/src ./service-demandes/src

WORKDIR /workspace/service-demandes
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/service-demandes/target/*.jar /app/app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
