# Stage 1: Build the application with Maven
FROM maven:3.9.6-amazoncorretto-17 as builder

# 1. Create a volume to hold cached Maven dependencies
# This is a key trick: it tells Docker to use a separate layer for 'm2'
# which is not copied into the final image, but is persisted between builds.
# However, for CI/CD environments, this is often unnecessary as the platform handles the cache.
# The standard way is to rely on Docker's layer cache, as shown below.

# 2. Set the working directory
WORKDIR /app

# 3. Copy only the Project Object Model (POM) file(s)
# This is the most critical step for caching dependencies.
# If pom.xml doesn't change, the next layer (dependency download) is cached.
COPY pom.xml .

# 4. Download Dependencies (If this step is cached, you save huge time)
# 'dependency:go-offline' downloads all transitive dependencies without compiling code.
# The 'install' goal (used below) will also work but 'go-offline' is often cleaner.
RUN --mount=type=cache,target=/root/.m2/repository mvn dependency:go-offline

# 5. Copy the rest of the source code
# This will invalidate the cache only if source files change.
COPY src src

# 6. Compile and Package the Application
# Since dependencies are already downloaded and cached, this step is fast.
RUN --mount=type=cache,target=/root/.m2/repository mvn package

# ==================================
# Stage 2: RUNNER STAGE (For Execution)
# ==================================
FROM eclipse-temurin:17-jre-focal

# Set necessary environment variables
ENV TZ America/Chicago
ENV SPRING_OUTPUT_ANSI_ENABLED ALWAYS
ENV JAVA_TOOL_OPTIONS "-Xms128m -Xmx512m"

# Set the working directory
WORKDIR /app

# Copy the built JAR from the builder stage
# Uses the 'builder' stage alias to access the packaged application.
COPY --from=builder /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# The command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]