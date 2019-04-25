FROM openjdk:8u171-jre-alpine

RUN apk add --no-cache bash coreutils openssl zip

ENV IEXEC_DECRYPT_FILE_PATH "/decrypt-dataset.sh"
ENV IEXEC_ENCRYPT_FILE_PATH "/encrypt-result.sh"

COPY build/libs/iexec-worker-@projectversion@.jar iexec-worker.jar

COPY build/resources/main/entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

COPY build/resources/main/decrypt-dataset.sh    /decrypt-dataset.sh
COPY build/resources/main/encrypt-result.sh     /encrypt-result.sh

RUN chmod +x /decrypt-dataset.sh && \
    chmod +x /encrypt-result.sh

ENTRYPOINT ["./entrypoint.sh"]