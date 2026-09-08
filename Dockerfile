FROM eclipse-temurin:21.0.12_8-jre-noble AS extractor

ARG jar

RUN test -n "$jar"

WORKDIR /extractor

COPY $jar iexec-worker.jar

RUN java -Djarmode=tools -jar iexec-worker.jar extract --layers

FROM eclipse-temurin:21.0.12_8-jre-noble

RUN apt-get update \
    && apt-get upgrade --no-install-recommends -y \
    && rm -rf /var/lib/apt/lists/*

RUN install -d /app

COPY --from=extractor /extractor/iexec-worker/dependencies/ /app
COPY --from=extractor /extractor/iexec-worker/snapshot-dependencies/ /app
COPY --from=extractor /extractor/iexec-worker/application/ /app

WORKDIR /app
ENTRYPOINT [ "java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "iexec-worker.jar" ]
