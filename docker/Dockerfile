FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw mvnw
RUN chmod +x mvnw

COPY pom.xml pom.xml
COPY invoice-worker/pom.xml invoice-worker/pom.xml

RUN ./mvnw -pl invoice-worker -am dependency:go-offline

COPY invoice-worker/src invoice-worker/src

RUN ./mvnw clean verify

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ghostscript \
        ocrmypdf \
        tesseract-ocr \
        tesseract-ocr-deu \
        tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 10001 invoice \
    && useradd --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin invoice \
    && mkdir -p /app /config /data/input /data/ocr /data/archive /data/database /data/logs \
    && chown -R invoice:invoice /app /config /data

COPY --from=build /workspace/invoice-worker/target/invoice-worker-0.2.0-SNAPSHOT.jar /app/invoice-worker.jar
COPY docker/docker-entrypoint.sh /app/docker-entrypoint.sh
COPY scripts/container-self-check.sh /app/container-self-check.sh

RUN chmod 0555 /app/docker-entrypoint.sh /app/container-self-check.sh \
    && chmod 0444 /app/invoice-worker.jar \
    && chown invoice:invoice /app/invoice-worker.jar /app/docker-entrypoint.sh /app/container-self-check.sh

WORKDIR /app
USER invoice

ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["process", "--profile", "production", "--config", "/config/application.properties"]