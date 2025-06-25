#!/bin/bash

# =============================================================================
# Keystore Creator Script (Refactored Version 1.2)
# =============================================================================
# Description: Creates a PKCS#12 keystore using KeystoreCreator.java
# Usage: ./create-keystore-refactored.sh [keystore_password]
# Note: This script replaces the deprecated keytool-based approach
# =============================================================================

set -e  # Exit on any error

# Default values
DEFAULT_KEYSTORE_PASSWORD="keystorePass123!"

# Parse command line arguments or use defaults
KEYSTORE_PASSWORD="${1:-$DEFAULT_KEYSTORE_PASSWORD}"

# File paths
KEYSTORE_FILE="secrets/keystore.p12"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}🔐 Keystore Creator - Refactored Version 1.2${NC}"
echo -e "${BLUE}==============================================================================${NC}"

# Create directories if they don't exist
echo -e "${YELLOW}📁 Creating directories...${NC}"
mkdir -p secrets

# Display configuration
echo -e "${YELLOW}📋 Configuration:${NC}"
echo -e "   Keystore File: ${KEYSTORE_FILE}"
echo -e "   Keystore Password: ${KEYSTORE_PASSWORD:0:4}***"
echo ""

# Remove existing keystore
if [ -f "$KEYSTORE_FILE" ]; then
    echo -e "${YELLOW}🗑️  Removing existing keystore...${NC}"
    rm "$KEYSTORE_FILE"
fi

# Check if JAR file exists
JAR_FILE="../build/libs/encloader-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${YELLOW}🔨 Building encloader project...${NC}"
    cd ..
    ./gradlew clean build -x test
    cd scripts
fi

# Create keystore using KeystoreCreator
echo -e "${YELLOW}🔑 Creating keystore using KeystoreCreator...${NC}"
java -cp "$JAR_FILE" com.example.keystore.KeystoreCreator "$KEYSTORE_FILE" "$KEYSTORE_PASSWORD" demo

# Verify keystore was created
if [ -f "$KEYSTORE_FILE" ]; then
    echo -e "${GREEN}✅ Keystore created successfully!${NC}"
    
    echo -e "${BLUE}📋 Keystore verification:${NC}"
    keytool -list -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASSWORD" -storetype PKCS12 2>/dev/null | grep "Alias name:" || echo "   (Unable to list aliases - this is normal)"
    
    echo ""
    echo -e "${GREEN}🎯 Usage Examples:${NC}"
    echo -e "   # Test with demo application:"
    echo -e "   cd .."
    echo -e "   java -Dkeystore.path=file:scripts/$KEYSTORE_FILE \\"
    echo -e "        -Dkeystore.password=$KEYSTORE_PASSWORD \\"
    echo -e "        -jar build/libs/encloader-0.0.1-SNAPSHOT.jar"
    echo ""
    echo -e "   # Or use environment variables:"
    echo -e "   export KEYSTORE_PATH=file:scripts/$KEYSTORE_FILE"
    echo -e "   export KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD"
    echo -e "   java -Dkeystore.path=\$KEYSTORE_PATH \\"
    echo -e "        -Dkeystore.password=\$KEYSTORE_PASSWORD \\"
    echo -e "        -jar build/libs/encloader-0.0.1-SNAPSHOT.jar"
    
    echo ""
    echo -e "${BLUE}🔍 Data Integrity Information:${NC}"
    echo -e "   ✅ Original strings stored as UTF-8 bytes in SecretKeySpec"
    echo -e "   ✅ KeystorePropertySource restores strings from UTF-8 bytes"
    echo -e "   ✅ No Base64 encoding/decoding issues"
    echo -e "   ✅ Jasypt will receive actual password strings"
    
else
    echo -e "${RED}❌ Failed to create keystore!${NC}"
    exit 1
fi

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${GREEN}🎉 Keystore generation completed successfully!${NC}"
echo -e "${BLUE}==============================================================================${NC}"
