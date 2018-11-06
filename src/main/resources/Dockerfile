FROM openjdk:8u171-jre-alpine

ADD build/libs/iexec-worker-new-@projectversion@.jar iexec-worker.jar

ENTRYPOINT ["java","-jar","/iexec-worker.jar"]