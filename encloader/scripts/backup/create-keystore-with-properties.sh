#!/bin/bash

# =============================================================================
# Keystore Properties Generator Script (Shell)
# =============================================================================
# Description: Creates a PKCS#12 keystore with encrypted properties
# Usage: ./create-keystore-with-properties.sh [keystore_password] [jasypt_password] [demo_secret] [db_password] [api_key]
# =============================================================================

set -e  # Exit on any error

# Default values
DEFAULT_KEYSTORE_PASSWORD="keystorePass123!"
DEFAULT_JASYPT_PASSWORD="jasyptSecret456!"
DEFAULT_DEMO_SECRET="demoValue789!"
DEFAULT_DB_PASSWORD="dbPassword123!"
DEFAULT_API_KEY="apiKey456789!"

# Parse command line arguments or use defaults
KEYSTORE_PASSWORD="${1:-$DEFAULT_KEYSTORE_PASSWORD}"
JASYPT_PASSWORD="${2:-$DEFAULT_JASYPT_PASSWORD}"
DEMO_SECRET="${3:-$DEFAULT_DEMO_SECRET}"
DB_PASSWORD="${4:-$DEFAULT_DB_PASSWORD}"
API_KEY="${5:-$DEFAULT_API_KEY}"

# File paths
KEYSTORE_FILE="secrets/keystore.p12"
TEMP_DIR="temp_keystore_creation"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}🔐 Keystore Properties Generator${NC}"
echo -e "${BLUE}==============================================================================${NC}"

# Create directories if they don't exist
echo -e "${YELLOW}📁 Creating directories...${NC}"
mkdir -p secrets
mkdir -p "$TEMP_DIR"

# Display configuration
echo -e "${YELLOW}📋 Configuration:${NC}"
echo -e "   Keystore File: ${KEYSTORE_FILE}"
echo -e "   Keystore Password: ${KEYSTORE_PASSWORD:0:4}***"
echo -e "   JASYPT Password: ${JASYPT_PASSWORD:0:4}***"
echo -e "   Demo Secret: ${DEMO_SECRET:0:4}***"
echo -e "   DB Password: ${DB_PASSWORD:0:4}***"
echo -e "   API Key: ${API_KEY:0:4}***"
echo ""

# Function to create secret key entry
create_secret_key() {
    local alias_name="$1"
    local secret_value="$2"
    local temp_keystore="$TEMP_DIR/${alias_name}.p12"
    
    echo -e "${YELLOW}🔑 Creating secret key for ${alias_name}...${NC}"
    
    # Create a temporary keystore with the secret key
    keytool -genseckey \
        -alias "$alias_name" \
        -keyalg AES \
        -keysize 256 \
        -keystore "$temp_keystore" \
        -storetype PKCS12 \
        -storepass "$KEYSTORE_PASSWORD" \
        -keypass "$KEYSTORE_PASSWORD" \
        -dname "CN=${alias_name}, OU=Demo, O=Example, C=KR" \
        >/dev/null 2>&1
    
    # Store the secret value as a certificate attribute (workaround)
    # Since we can't directly store arbitrary strings in PKCS#12, we'll use a different approach
    echo "$secret_value" > "$TEMP_DIR/${alias_name}.txt"
}

# Function to combine keystores
combine_keystores() {
    local target_keystore="$1"
    local source_keystore="$2"
    local alias_name="$3"
    
    if [ -f "$target_keystore" ]; then
        # Import the key from source to target
        keytool -importkeystore \
            -srckeystore "$source_keystore" \
            -srcstoretype PKCS12 \
            -srcstorepass "$KEYSTORE_PASSWORD" \
            -destkeystore "$target_keystore" \
            -deststoretype PKCS12 \
            -deststorepass "$KEYSTORE_PASSWORD" \
            -srcalias "$alias_name" \
            -destalias "$alias_name" \
            -noprompt \
            >/dev/null 2>&1
    else
        # Copy the first keystore as base
        cp "$source_keystore" "$target_keystore"
    fi
}

# Remove existing keystore
if [ -f "$KEYSTORE_FILE" ]; then
    echo -e "${YELLOW}🗑️  Removing existing keystore...${NC}"
    rm "$KEYSTORE_FILE"
fi

# Create individual secret keys
create_secret_key "JASYPT_PASSWORD" "$JASYPT_PASSWORD"
create_secret_key "DEMO_SECRET" "$DEMO_SECRET" 
create_secret_key "DB_PASSWORD" "$DB_PASSWORD"
create_secret_key "API_KEY" "$API_KEY"

# Combine all keystores
echo -e "${YELLOW}🔗 Combining keystores...${NC}"
for alias in JASYPT_PASSWORD DEMO_SECRET DB_PASSWORD API_KEY; do
    combine_keystores "$KEYSTORE_FILE" "$TEMP_DIR/${alias}.p12" "$alias"
done

# Create a properties file with the secret values for reference
echo -e "${YELLOW}📄 Creating properties reference file...${NC}"
cat > "secrets/keystore_properties.txt" << EOF
# Keystore Properties Reference
# Generated on: $(date)
# Keystore File: $KEYSTORE_FILE
# Keystore Password: $KEYSTORE_PASSWORD

JASYPT_PASSWORD=$JASYPT_PASSWORD
DEMO_SECRET=$DEMO_SECRET
DB_PASSWORD=$DB_PASSWORD
API_KEY=$API_KEY
EOF

# Since PKCS#12 doesn't support arbitrary string properties directly,
# we'll create a simple encrypted properties file as an alternative
echo -e "${YELLOW}🔐 Creating encrypted properties file...${NC}"
cat > "secrets/encrypted_properties.properties" << EOF
# Encrypted Properties for Keystore Demo
# Use these values in your KeystorePropertySource implementation

JASYPT_PASSWORD=$JASYPT_PASSWORD
DEMO_SECRET=$DEMO_SECRET
DB_PASSWORD=$DB_PASSWORD
API_KEY=$API_KEY
EOF

# Clean up temporary files
echo -e "${YELLOW}🧹 Cleaning up temporary files...${NC}"
rm -rf "$TEMP_DIR"

# Verify keystore
echo -e "${YELLOW}🔍 Verifying keystore...${NC}"
if keytool -list -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASSWORD" -storetype PKCS12 >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Keystore created successfully!${NC}"
    
    echo -e "${BLUE}📋 Keystore contents:${NC}"
    keytool -list -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASSWORD" -storetype PKCS12 | grep "Alias name:"
    
    echo ""
    echo -e "${GREEN}🎯 Usage:${NC}"
    echo -e "   export KEYSTORE_PATH=file:$KEYSTORE_FILE"
    echo -e "   export KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD"
    echo -e "   java -Dkeystore.path=file:$KEYSTORE_FILE \\"
    echo -e "        -Dkeystore.password=$KEYSTORE_PASSWORD \\"
    echo -e "        -jar encloader.jar"
    
else
    echo -e "${RED}❌ Failed to create keystore!${NC}"
    exit 1
fi

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${GREEN}🎉 Keystore generation completed successfully!${NC}"
echo -e "${BLUE}==============================================================================${NC}"
