FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY settings.xml .
COPY pom.xml .
RUN mvn -s settings.xml -B -q dependency:go-offline
COPY src ./src
RUN mvn -s settings.xml -B -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/campussafe-api-1.0.0.jar app.jar
EXPOSE 5000
CMD ["java", "-jar", "app.jar"]
