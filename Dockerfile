FROM openjdk:8u171-jre-alpine

ADD build/libs/iexec-worker-@projectversion@.jar iexec-worker.jar
COPY build/resources/main/entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

ENV IEXEC_WORKER_WALLET_PATH /wallet.json

ENTRYPOINT ["./entrypoint.sh"]