#!/bin/bash

CONFIG_SERVER_URL="http://localhost:8888"

echo "Testing Config Server endpoints..."
echo "================================="

# Health check
echo "1. Health Check:"
curl -s "$CONFIG_SERVER_URL/actuator/health" | jq '.'
echo -e "\n"

# Orders Service Dev
echo "2. Orders Service (dev profile):"
curl -s "$CONFIG_SERVER_URL/orders-service/dev" | jq '.propertySources[0].source'
echo -e "\n"

# Orders Service Prod
echo "3. Orders Service (prod profile):"
curl -s "$CONFIG_SERVER_URL/orders-service/prod" | jq '.propertySources[0].source'
echo -e "\n"

# User Service Dev
echo "4. User Service (dev profile):"
curl -s "$CONFIG_SERVER_URL/user-service/dev" | jq '.propertySources[0].source'
echo -e "\n"

# Application Default
echo "5. Application Default:"
curl -s "$CONFIG_SERVER_URL/application/default" | jq '.propertySources[0].source'
echo -e "\n"

echo "Test completed!"
