FROM maven:3.8.1-jdk-11 AS maven-build
USER root
# prepare dependency
ADD pom.xml .
RUN mvn dependency:go-offline

COPY ./ /app/build
RUN cd /app/build \
    && mvn -T 1C clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true \
    && wget -O jmx_prometheus_javaagent.jar https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.16.1/jmx_prometheus_javaagent-0.16.1.jar

FROM openjdk:11-jdk-oracle
WORKDIR /app
COPY --from=maven-build /app/build/target/sshforward*.jar /app/sshforward.jar
COPY --from=maven-build /app/build/jmx_prometheus_javaagent.jar /app/jmx_exporter.jar
COPY --from=maven-build /app/build/jmx-config.yml /app/jmx-config.yml
EXPOSE 22
ENTRYPOINT ["java","-javaagent:/app/jmx_exporter.jar=9100:/app/jmx-config.yml","-jar","/app/sshforward.jar"]
