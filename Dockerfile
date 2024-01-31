FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY files files
COPY target/* target
RUN ./mvnw package
COPY mvnw pom.xml ./
RUN ./mvnw dependency:resolve

CMD ["./java", "-jar target/transit-delay-service-1.0.0.jar"]