# Build stage
FROM eclipse-temurin:22-jdk-jammy AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:22-jre-jammy
WORKDIR /app
COPY --from=build /app/target/trustvault-1.0.0.jar app.jar
RUN mkdir -p uploads
ENV PORT=10000
EXPOSE 10000
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xss512k", "-Xms128m", "-Xmx256m", "-XX:MaxMetaspaceSize=192m", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
