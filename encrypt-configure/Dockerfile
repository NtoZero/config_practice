FROM openjdk:17-jdk-slim

WORKDIR /app

COPY build/libs/encrypt-configure-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]
