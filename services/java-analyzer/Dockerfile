FROM maven:3.9.16-eclipse-temurin-21@sha256:a972570be789ee5c9fa23446a8914ac7327560b5c022f662cfa9452aef829f18 AS build
WORKDIR /workspace
COPY services/java-analyzer/pom.xml .
COPY services/java-analyzer/src ./src
COPY config/checkstyle.xml /config/checkstyle.xml
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21.0.12_8-jre-noble@sha256:d35199d74a3b2dff1bfb435d9adde4ef974e74a7d2a4fcb3079063c55926edb5
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && groupadd --system --gid 10001 riskgraph \
    && useradd --system --uid 10001 --gid riskgraph --no-create-home riskgraph \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build --chown=riskgraph:riskgraph /workspace/target/java-analyzer-0.1.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
