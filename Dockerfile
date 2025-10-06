# Stage 1: Build the application using a JDK image
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY . .

# Grant execute permission to the Gradle wrapper
RUN chmod +x ./gradlew

# Build the application, skipping tests. Cache Gradle dependencies.
RUN --mount=type=cache,target=/root/.gradle ./gradlew build -x test

# Create a directory for the GeoIP database and move it if it exists
RUN mkdir -p /geodb && find /workspace/src/main/resources -name "GeoLite2-City.mmdb" -exec mv {} /geodb/ \;


# Stage 2: Create the final, lean production image using a JRE image
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create a non-root user and group for the application
RUN groupadd --system --gid 1001 appgroup && \
    useradd --system --uid 1001 --gid appgroup appuser

# Copy required artifacts from the build stage
# We no longer need to copy the truststore, as it will be provided by a volume mount.
COPY --from=build /geodb /app/geodb
COPY --from=build /workspace/build/libs/*.jar app.jar

# Set ownership of the app directory to the new user
RUN chown -R appuser:appgroup /app

# Switch to the non-root user
USER appuser

# Expose the port the application runs on
EXPOSE 8080

# The command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]