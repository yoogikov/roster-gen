# Multi-stage image for the TA: docker run -p 8080:8080 <image>
# Default profile is dev (embedded H2). For Postgres, set SPRING_PROFILES_ACTIVE=prod
# and the ROSTER_DB_* / ROSTER_ADMIN_PASSWORD variables (see docker-compose.yml).

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S roster && adduser -S roster -G roster
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER roster
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
