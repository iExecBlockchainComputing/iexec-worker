FROM openjdk:11.0.7-jre-slim

RUN apt-get update && apt-get install -y --no-install-recommends bash coreutils openssl zip

ENV IEXEC_DECRYPT_FILE_PATH "/decrypt-dataset.sh"
ENV IEXEC_ENCRYPT_FILE_PATH "/encrypt-result.sh"

COPY build/resources/main/decrypt-dataset.sh    /decrypt-dataset.sh
COPY build/resources/main/encrypt-result.sh     /encrypt-result.sh
COPY build/resources/main/entrypoint.sh         entrypoint.sh

RUN chmod +x /decrypt-dataset.sh && \
    chmod +x /encrypt-result.sh && \
    chmod +x entrypoint.sh

COPY build/libs/iexec-worker-@projectversion@.jar iexec-worker.jar

ENTRYPOINT ["./entrypoint.sh"]