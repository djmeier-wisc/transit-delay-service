# Stage 1: Build the application with Maven
FROM maven:3.9.6-amazoncorretto-17 as builder

WORKDIR /app

# Copy only the POM file to cache dependencies
COPY pom.xml .

# Download dependencies and build the application
RUN mvn dependency:go-offline

# Copy the entire project and build it
COPY . .
RUN mvn package -DskipTests

# Stage 2: Create the final lightweight image
FROM ghcr.io/graalvm/jdk:java17

WORKDIR /app

# Copy only the JAR file from the builder stage
COPY --from=builder /app/target/transit-delay-service-1.0.0.jar /app/
COPY --from=builder /app/files /app/
# Expose the port that the application will run on
EXPOSE 8080

# Specify the command to run on container start
CMD ["java", "-jar", "transit-delay-service-1.0.0.jar"]
