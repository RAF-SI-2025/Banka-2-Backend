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
#
# Mirror fallback strategija (GHA Azure runner debugging 04.05.2026):
# 1. Probamo default mirror (archive.ubuntu.com:80)
# 2. Fallback na azure.archive.ubuntu.com — INTERNI Azure datacenter mirror,
#    ne pati od outbound port 80 firewall-a koji povremeno hvata GHA runner-e
#    ("Connection refused" ka archive.ubuntu.com:80 na svim IP-ovima)
# 3. Last-resort fallback na HTTPS varijantu archive.ubuntu.com:443
# Svaki pokusaj ima Acquire::Retries=5 (interno apt retry po paketu) plus
# 3-step bash retry sa exponential backoff za pocetni handshake.
RUN set -eux; \
    install_curl() { \
        for i in 1 2 3; do \
            if apt-get -o Acquire::Retries=5 update \
               && apt-get -o Acquire::Retries=5 install -y --no-install-recommends curl; then \
                return 0; \
            fi; \
            [ "$i" = "3" ] && return 1; \
            echo "apt-get attempt $i failed, retrying in $((i * 10))s..."; \
            sleep $((i * 10)); \
        done; \
    }; \
    switch_mirror() { \
        from="$1"; to="$2"; \
        if [ -f /etc/apt/sources.list.d/ubuntu.sources ]; then \
            sed -i "s|${from}|${to}|g" /etc/apt/sources.list.d/ubuntu.sources; \
        fi; \
        if [ -f /etc/apt/sources.list ]; then \
            sed -i "s|${from}|${to}|g" /etc/apt/sources.list; \
        fi; \
    }; \
    if ! install_curl; then \
        echo "Default Ubuntu mirror failed — switching to Azure mirror"; \
        switch_mirror 'http://archive.ubuntu.com' 'http://azure.archive.ubuntu.com'; \
        switch_mirror 'http://security.ubuntu.com' 'http://azure.archive.ubuntu.com'; \
        if ! install_curl; then \
            echo "Azure mirror failed — switching to HTTPS upstream"; \
            switch_mirror 'http://azure.archive.ubuntu.com' 'https://archive.ubuntu.com'; \
            install_curl || { echo "All mirrors failed — apt-get cannot reach any Ubuntu repository" >&2; exit 1; }; \
        fi; \
    fi; \
    rm -rf /var/lib/apt/lists/*

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
