# Stage 1: Build the Spring Boot app
FROM maven:3.8.4-openjdk-17 AS build
WORKDIR /app
# Copy pom.xml and resolve dependencies
COPY pom.xml .
RUN mvn dependency:go-offline
# Copy source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM openjdk:17-jdk-slim
WORKDIR /app
# Copy the built JAR from the build stage
COPY --from=build /app/target/image-validator-0.0.1-SNAPSHOT.jar .
# Expose port 8080
EXPOSE 8080
# Run the application
ENTRYPOINT ["java", "-jar", "/app/image-validator-0.0.1-SNAPSHOT.jar"]