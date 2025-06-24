#!/bin/bash

echo "Starting Spring Cloud Config Server..."

# JAR 파일 빌드
echo "Building JAR file..."
./gradlew bootJar

# Config Server 실행
echo "Starting Config Server on port 8888..."
java -jar build/libs/encrypt-configure-0.0.1-SNAPSHOT.jar

echo "Config Server started successfully!"
echo "Access the server at: http://localhost:8888"
echo "Health check: http://localhost:8888/actuator/health"
