FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar macro-tracker-user-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "macro-tracker-user-service.jar"]
