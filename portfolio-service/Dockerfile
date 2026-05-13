FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY portfolio-service/pom.xml portfolio-service/pom.xml
COPY risk-service/pom.xml risk-service/pom.xml
COPY notifier-service/pom.xml notifier-service/pom.xml
COPY common/src common/src
COPY portfolio-service/src portfolio-service/src
RUN mvn clean package -pl common,portfolio-service -am -DskipTests
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /app/portfolio-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]