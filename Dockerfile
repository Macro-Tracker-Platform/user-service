FROM olehprukhnytskyi/base-java-otel:21
WORKDIR /app
COPY target/macro-tracker-user-service-0.0.1-SNAPSHOT.jar macro-tracker-user-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "macro-tracker-user-service.jar"]