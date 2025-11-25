FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/macro-tracker-user-service-0.0.1-SNAPSHOT.jar macro-tracker-user-service.jar
COPY opentelemetry-javaagent.jar /opt/opentelemetry/opentelemetry-javaagent.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "macro-tracker-user-service.jar"]