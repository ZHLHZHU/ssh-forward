FROM maven:3.8.1-jdk-11 AS maven-build
USER root
COPY ./ /app/build
RUN cd /app/build \
    && mvn -B dependency:resolve-plugins dependency:resolve \
    && mvn -T 1C clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true

FROM openjdk:11-jdk-oracle
WORKDIR /app
COPY --from=maven-build /app/build/target/sshforward*.jar /app/sshforward.jar
COPY --from=maven-build /app/build/src/main/resources/jmx_prometheus_javaagent-0.16.1.jar /app/jmx_exporter.jar
COPY --from=maven-build /app/build/src/main/resources/jmx-config.yml /app/jmx-config.yml
EXPOSE 22
ENTRYPOINT ["java","-javaagent:/app/jmx_exporter.jar=9100:/app/jmx-config.yml","-jar","/app/sshforward.jar"]
