FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY service-cnts/pom.xml ./service-cnts/pom.xml
COPY service-cnts/src ./service-cnts/src
COPY contracts/wsdl-xsd ./contracts/wsdl-xsd

WORKDIR /workspace/service-cnts
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/service-cnts/target/*.jar /app/app.jar

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
