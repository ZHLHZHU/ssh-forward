FROM maven:3.8.1-jdk-11 AS maven-build
USER root
COPY ./ /app/build
RUN cd /app/build \
    && mvn -T 1C clean install -e -U -e \
    && mvn -T 1C clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true

FROM openjdk:11-jdk-oracle
WORKDIR /app
COPY --from=maven-build /app/build/target/sshforward*.jar /app/sshforward.jar
EXPOSE 22
ENTRYPOINT ["java","-jar","/app/sshforward.jar"]
