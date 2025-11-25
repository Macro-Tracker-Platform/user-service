FROM maven:3-openjdk-21 AS build
WORKDIR /app
COPY . .
COPY opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
RUN mvn clean install -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar macro-tracker-user-service.jar
COPY --from=build /app/opentelemetry-javaagent.jar /opt/opentelemetry/opentelemetry-javaagent.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "macro-tracker-user-service.jar"]