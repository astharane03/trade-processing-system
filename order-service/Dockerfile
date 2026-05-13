# Stage 1 — build
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# copy parent pom
COPY pom.xml .

# copy ALL module poms — Maven needs these to resolve the reactor
COPY common/pom.xml common/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY portfolio-service/pom.xml portfolio-service/pom.xml
COPY risk-service/pom.xml risk-service/pom.xml
COPY notifier-service/pom.xml notifier-service/pom.xml

# copy src only for modules we actually need to build
COPY common/src common/src
COPY order-service/src order-service/src

# build
RUN mvn clean package -pl common,order-service -am -DskipTests

# Stage 2 — run
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=builder /app/order-service/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]