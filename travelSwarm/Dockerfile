# Stage 1: Build the application
FROM gradle:8.7.0-jdk17 AS build_image

WORKDIR /workspace/app

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle .
# COPY settings.gradle . # Uncomment if you have settings.gradle

RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon || ./gradlew --stop

COPY src ./src
RUN ./gradlew bootJar --no-daemon || ./gradlew --stop

# --- DEBUG LINE: Ensure this is active ---
RUN echo "Listing contents of /workspace/app/build/libs/ after bootJar:" && \
    ls -l /workspace/app/build/libs/
# ---------------------------------------

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build_image /workspace/app/build/libs/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]