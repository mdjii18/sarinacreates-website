# Stage 1: Build Java Application using Maven
FROM maven:3.9.8-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build production executable JAR
RUN mvn clean package -DskipTests

# Stage 2: Runtime image using Lightweight JDK
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy compiled jar from builder stage
COPY --from=builder /app/target/sarina-backend-1.0.0.jar app.jar

# Expose default port
EXPOSE 3000

# Run Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]
