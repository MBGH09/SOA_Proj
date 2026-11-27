# =============================================================
# service-don-stock — multi-stage Dockerfile
# =============================================================
# Build context must be the REPOSITORY ROOT so that ../contracts/
# is resolvable from the Maven build (pom.xml reads
#   ../../contracts/proto  and  ../../contracts/wsdl-xsd).
#
# From the repo root:
#   docker build -f service-don-stock/service-don-stock/Dockerfile \
#                -t service-don-stock .
# =============================================================

# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy contracts first (proto stubs + JAXB from XSD)
COPY contracts/ contracts/

# Copy the service source
COPY service-don-stock/service-don-stock/ service-don-stock/service-don-stock/

WORKDIR /workspace/service-don-stock/service-don-stock
RUN mvn -B -DskipTests package

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/service-don-stock/service-don-stock/target/*.jar app.jar

# gRPC port 9090 and Actuator/HTTP port 8083
EXPOSE 9090 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
