FROM openjdk:11.0.7-jre-slim

COPY iexec-worker.jar iexec-worker.jar

ENTRYPOINT [ "/bin/sh", "-c", "exec java -Djava.security.egd=file:/dev/./urandom -jar iexec-worker.jar" ]