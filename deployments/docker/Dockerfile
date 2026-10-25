FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY pom.xml .
COPY proto proto
COPY src src
RUN apt-get update && apt-get install -y --no-install-recommends maven \
    && mvn -q -DskipTests package \
    && rm -rf /var/lib/apt/lists/*

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/target/helios-0.1.0.jar /app/helios.jar
RUN printf '#!/bin/sh\nexec java -jar /app/helios.jar "$@"\n' > /app/helios-api \
    && printf '#!/bin/sh\nexport HELIOS_ROLE=shard\nexec java -jar /app/helios.jar "$@"\n' > /app/helios-shard \
    && chmod +x /app/helios-api /app/helios-shard
EXPOSE 8080 9000 9100
ENTRYPOINT ["/app/helios-api"]
