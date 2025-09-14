FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew build -x test
RUN mkdir -p /geodb && find /workspace/src/main/resources -name "GeoLite2-City.mmdb" -exec mv {} /geodb/ \;

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/redis/tls/truststore.p12 /app/truststore.p12
COPY --from=build /geodb /app/geodb
COPY --from=build /workspace/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]