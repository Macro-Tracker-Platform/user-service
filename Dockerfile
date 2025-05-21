FROM maven:3.8.5-openjdk-17 AS build
COPY . .
RUN mvn clean package -DskipTests

FROM openjdk:17-alpine
COPY --from=build /target/macro-tracker-user-service-0.0.1-SNAPSHOT.jar macro-tracker-user-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "macro-tracker-user-service.jar"]
