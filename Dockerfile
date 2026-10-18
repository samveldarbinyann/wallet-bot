# syntax=docker/dockerfile:1

# ---- Stage 1: dependency resolution, cached separately from source ----
FROM eclipse-temurin:21-jdk AS deps
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# ---- Stage 2: build ----
FROM deps AS build
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# ---- Stage 3: runtime ----
FROM eclipse-temurin:21-jre AS runtime
RUN groupadd -r wallet && useradd -r -g wallet wallet \
    && apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN mkdir -p /app/uploads/broadcasts && chown -R wallet:wallet /app
USER wallet
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "/app/app.jar"]