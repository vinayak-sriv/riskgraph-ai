FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre
LABEL ai.riskgraph.sandbox="1"
WORKDIR /app
COPY --from=build /workspace/target/sandbox-1.0.0.jar app.jar
USER 65532:65532
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:ActiveProcessorCount=1"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
