# syntax=docker/dockerfile:1

# --- Build stage -----------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the whole repo so the Gradle settings + all required modules are present.
COPY . .

# Make the wrapper executable (it may lose its bit on Windows checkouts) and build the bootJar.
# Normalize line endings (gradlew is checked out with CRLF on Windows) then build.
RUN sed -i 's/\r$//' gradlew && chmod +x ./gradlew && ./gradlew :bot-api:bootJar -x test --no-daemon

# --- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/bot-api/build/libs/*.jar /app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app.jar"]
