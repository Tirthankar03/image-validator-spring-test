# Stage 1: Build the Spring Boot app
FROM maven:3.8.4-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and resolve dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/image-validator-0.0.1-SNAPSHOT.jar .

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/image-validator-0.0.1-SNAPSHOT.jar"]
