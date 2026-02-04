# Multi-stage build for CarDetector
FROM maven:3.8-openjdk-8 AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM openjdk:8-jre-slim

WORKDIR /app

# Copy the built JAR
COPY --from=builder /app/target/myapp-1.0-SNAPSHOT.jar app.jar

# Default to CarDetector, can be overridden
ENTRYPOINT ["java", "-cp", "app.jar", "org.example.basicapp.CarDetector"]

