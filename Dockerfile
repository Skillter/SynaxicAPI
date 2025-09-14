# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-jammy as build
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew build -x test

# Stage 2: Create the final, lean image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the truststore into the classpath of the final image
COPY --from=build /workspace/redis/tls/truststore.p12 /app/
COPY --from=build /workspace/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]