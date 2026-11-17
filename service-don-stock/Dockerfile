FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY service-don-stock/pom.xml ./service-don-stock/pom.xml
COPY service-don-stock/src ./service-don-stock/src

WORKDIR /workspace/service-don-stock
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/service-don-stock/target/*.jar /app/app.jar

EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
