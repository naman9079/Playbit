FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 playbit
COPY --from=build /app/target/playbit-1.0.0.jar app.jar
USER playbit
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
