FROM maven:3.9.16-eclipse-temurin-21@sha256:a972570be789ee5c9fa23446a8914ac7327560b5c022f662cfa9452aef829f18 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21.0.12_8-jre-noble@sha256:d35199d74a3b2dff1bfb435d9adde4ef974e74a7d2a4fcb3079063c55926edb5
LABEL ai.riskgraph.sandbox="1"
WORKDIR /app
COPY --from=build /workspace/target/sandbox-1.0.0.jar app.jar
USER 65532:65532
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:ActiveProcessorCount=1"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
