FROM maven:3.9.5-openjdk-17 AS build
WORKDIR /app
COPY . .
COPY opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
RUN mvn clean install -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/macro-tracker-user-service-0.0.1-SNAPSHOT.jar macro-tracker-user-service.jar
COPY --from=build /app/opentelemetry-javaagent.jar /opt/opentelemetry/opentelemetry-javaagent.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "macro-tracker-user-service.jar"]