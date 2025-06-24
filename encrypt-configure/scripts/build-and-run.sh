#!/bin/bash

echo "Building and Running Spring Cloud Config Server"
echo "=============================================="

# 프로젝트 빌드
echo "1. Building project..."
./gradlew clean bootJar

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

# Docker 이미지 빌드
echo "2. Building Docker image..."
docker build -t config-server .

if [ $? -ne 0 ]; then
    echo "Docker build failed!"
    exit 1
fi

# Docker Compose로 실행
echo "3. Starting services with Docker Compose..."
docker-compose up -d

echo "4. Waiting for services to start..."
sleep 30

# 서비스 상태 확인
echo "5. Checking service status..."
docker-compose ps

echo ""
echo "Services are now running!"
echo "Config Server: http://localhost:8888"
echo "Example Client: http://localhost:8080"
echo ""
echo "To stop services: docker-compose down"
