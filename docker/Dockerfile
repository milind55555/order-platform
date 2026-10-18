# Shared multi-stage Dockerfile for every service.
# Build from the repository root:  docker build -f docker/Dockerfile --build-arg SERVICE=order-service -t order-service .
FROM maven:3.9-eclipse-temurin-17 AS build
ARG SERVICE
WORKDIR /workspace

# Cache dependencies first (poms only)
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
RUN mvn -B -q -pl ${SERVICE} -am dependency:go-offline || true

COPY common common
COPY ${SERVICE} ${SERVICE}
RUN mvn -B -q -pl ${SERVICE} -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
ARG SERVICE
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/app.jar app.jar
USER 10001
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
