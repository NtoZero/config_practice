#!/bin/bash

# =================================================================
# .env 파일로부터 키스토어 생성 스크립트 (Shell)
# 비밀번호 자동 생성 및 파일 저장 버전
# =================================================================

# --- 설정: 키스토어 파일과 .env 파일 경로를 지정합니다. ---
KEYSTORE_FILE="secrets/keystore.p12"
PASSWORD_FILE="secrets/keystore_password.txt"
ENV_FILE="secrets.env"

echo "================================================================="
echo " Creating a secure keystore from .env file (Shell)"
echo " (Keystore password will be auto-generated)"
echo "================================================================="

# keytool 명령어가 존재하는지 확인합니다.
if ! command -v keytool &> /dev/null; then
    echo "[ERROR] 'keytool' command not found. Please install Java Development Kit (JDK)."
    exit 1
fi

# 1. .env 파일이 존재하는지 확인합니다.
if [ ! -f "$ENV_FILE" ]; then
    echo "[ERROR] .env file not found: $ENV_FILE"
    exit 1
fi

# 2. 임의의 키스토어 비밀번호를 생성합니다.
KEYSTORE_PASSWORD="auto-gen-pass-$RANDOM-$RANDOM"
echo "[INFO] A random keystore password has been generated."

# 3. 'secrets' 폴더를 생성하고, 생성된 비밀번호를 파일에 저장합니다.
mkdir -p secrets
echo "$KEYSTORE_PASSWORD" > "$PASSWORD_FILE"
echo "[INFO] The generated password has been saved to: $PASSWORD_FILE"

# 기존 키스토어 파일이 있으면 삭제합니다.
if [ -f "$KEYSTORE_FILE" ]; then
    echo "[INFO] Deleting existing keystore file: $KEYSTORE_FILE..."
    rm -f "$KEYSTORE_FILE"
fi

# 4. .env 파일을 한 줄씩 읽어 키-값 쌍을 키스토어에 추가합니다.
echo "[INFO] Reading '$ENV_FILE' and creating the keystore..."
while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ -z "$line" || "$line" == \#* ]]; then
        continue
    fi
    
    KEY="${line%%=*}"
    VALUE="${line#*=}"

    if [ -n "$KEY" ]; then
        echo "  + Adding alias: $KEY..."
        
        # [수정된 부분] 변수 끝의 오타 '%'를 제거했습니다.
        echo "$VALUE" | keytool -importpass -alias "$KEY" -keystore "$KEYSTORE_FILE" -storetype PKCS12 -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD" -noprompt
        
        if [ $? -ne 0 ]; then
            echo "[ERROR] Failed to add alias: $KEY"
            exit 1
        fi
    fi
done < "$ENV_FILE"

# 5. 최종적으로 생성된 키스토어의 내용을 확인합니다.
echo ""
echo "[INFO] Verifying final keystore contents..."
keytool -list -v -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASSWORD"

# 검증 단계에서 오류가 발생했는지 다시 확인합니다.
if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] Final verification of the keystore failed. Please check the logs above."
    exit 1
fi

echo ""
echo "================================================================="
echo "[SUCCESS] Keystore file has been created: $KEYSTORE_FILE"
echo "[IMPORTANT] The keystore password is saved in: $PASSWORD_FILE"
echo "================================================================="

exit 0