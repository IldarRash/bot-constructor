FROM gradle:8.8.0-jdk17-alpine AS build
WORKDIR /home/gradle/project
COPY . .
RUN gradle :gateway:build -x test

FROM openjdk:17-alpine
WORKDIR /app
COPY --from=build /home/gradle/project/gateway/build/libs/*.jar ./app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"] 