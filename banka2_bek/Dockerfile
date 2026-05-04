# syntax=docker/dockerfile:1.7

# ---- Build stage ----
# Java 25 LTS bumpovan 03.05.2026. BuildKit cache mount za .m2 — Maven dep
# fetch se ne ponavlja izmedju no-cache rebuild-ova ako pom.xml nije menjan.
# Rebuild time: ~120s (no cache) → ~25s (warm .m2 cache).
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline -DskipTests

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests -q

# ---- Layer extraction stage ----
# Spring Boot layered jar: razdvaja fat jar u 4 layer-a (dependencies,
# spring-boot-loader, snapshot-dependencies, application). Docker tako
# kesh-ira deps zasebno od app code-a — promena 1 reda u kodu rebuild-uje
# samo `application` layer (~5MB), ne 80MB fat jar.
FROM eclipse-temurin:25-jre AS layers
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# curl je potreban samo za docker-compose `healthcheck:` koji proverava
# /v3/api-docs i tako sinhronizuje seed servis (ceka backend healthy).
# Bez ovog, healthcheck puca jer ni curl ni wget nisu prisutni u temurin:25-jre.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Kopiramo layer-e u tacno odredjenom redosledu — najmanje promenljiv prvo,
# najvise promenljiv (application) zadnji. Docker cesh-ira u poretku COPY
# linija, pa se rebuild dotice samo poslednje promenjenog layer-a.
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./

EXPOSE 8080
# `JarLauncher` je Spring Boot bootstrapper koji ucitava layered jar.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
