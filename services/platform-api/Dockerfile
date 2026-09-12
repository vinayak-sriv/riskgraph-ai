FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY services/platform-api/pom.xml .
COPY services/platform-api/src ./src
COPY contracts /contracts
COPY infrastructure/db/migrations /infrastructure/db/migrations
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/target/platform-api-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
