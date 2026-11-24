# Dockerfile du Gateway — S2-11.
# Derive du modele fourni par Student 7 (branche origin/config).
# Le contexte de build attendu est la RACINE du depot :
#   docker build -f gateway/Dockerfile -t soa-gateway:s2-11 .
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY gateway/pom.xml ./gateway/pom.xml
COPY gateway/src ./gateway/src

WORKDIR /workspace/gateway
RUN mvn -B -ntp -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# curl est utilise par le healthcheck du docker-compose.yml (Student 7).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/gateway/target/*.jar /app/app.jar

# Le port d'ecoute est fourni par SERVER_PORT : 8080 dans le compose Student 7,
# 8090 par defaut en local (voir src/main/resources/application.yml).
EXPOSE 8080 8090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
