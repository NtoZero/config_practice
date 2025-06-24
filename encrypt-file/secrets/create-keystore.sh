#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# create-keystore.sh — Linux/macOS용 .p12 키스토어 생성 스크립트
# -----------------------------------------------------------------------------
set -euo pipefail   # 스크립트 오류·파이프 실패 시 즉시 종료

echo "🔐 PKCS#12 키스토어 생성을 시작합니다..."

# ✅ 1) 32바이트 난수 기반 STOREPASS 생성 (평문 노출 금지)
STOREPASS="$(openssl rand -base64 32)"

# ✅ 2) 비밀번호를 안전한 파일에 저장 (현재 디렉터리 기준)
echo "$STOREPASS" > .keystore_pass
chmod 400 .keystore_pass   # 소유자 읽기 전용

echo "📝 키스토어 비밀번호를 .keystore_pass 파일에 저장했습니다."

# ✅ 3) keytool로 4096‑bit RSA 키 + 자기서명 인증서 생성
echo "🔧 키스토어를 생성 중입니다..."
keytool -genkeypair \
  -alias jasypt-key \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -storepass "$STOREPASS" \
  -keyalg RSA -keysize 4096 \
  -validity 3650 \
  -dname "CN=jasypt, OU=Dev, O=YourOrg, L=Seoul, C=KR"

unset STOREPASS   # 메모리 해제
printf '\n✅ [완료] keystore.p12 생성 완료 — 비밀번호는 .keystore_pass 파일에 저장되었습니다.\n'

echo ""
echo "🔧 다음 단계:"
echo "1. export JASYPT_STOREPASS=\$(cat .keystore_pass) 명령으로 환경변수 설정"
echo "2. 애플리케이션 시작: cd .. && ./gradlew bootRun --args='--spring.profiles.active=local'"
echo ""
echo "🛡️  보안 주의사항:"
echo "- .keystore_pass 파일과 keystore.p12 파일은 절대 버전 관리에 포함하지 마세요"
echo "- 운영 환경에서는 적절한 권한 설정을 하세요"
