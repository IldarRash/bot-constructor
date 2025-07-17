FROM gradle:8.8.0-jdk17-alpine AS build
WORKDIR /home/gradle/project
COPY . .
RUN gradle :auth-server:build -x test

FROM openjdk:17-alpine
WORKDIR /app
COPY --from=build /home/gradle/project/auth-server/build/libs/*.jar ./app.jar
EXPOSE 8081
CMD ["java", "-jar", "app.jar"] 