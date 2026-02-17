# Stage 1: Build with Temurin (More stable ARM64 support)
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .

# Download dependencies
RUN --mount=type=cache,target=/root/.m2/repository mvn dependency:go-offline

COPY src src

# Package the application
# We add -Dmaven.compiler.release=17 to explicitly force the version
RUN --mount=type=cache,target=/root/.m2/repository mvn package -Dmaven.compiler.release=17 

# Stage 2: Runner Stage (Use JRE for a smaller, faster image)
FROM eclipse-temurin:17-jre
ENV TZ=America/Chicago
WORKDIR /app

# Copy the JAR from builder 
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]