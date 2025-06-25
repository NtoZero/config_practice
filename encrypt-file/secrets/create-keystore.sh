#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# create-keystore.sh — Linux/macOS용 .p12 키스토어 생성 스크립트 v2.0
# 마이그레이션 완료: 개인키 기반 JASYPT 암호화 구조
# -----------------------------------------------------------------------------
set -euo pipefail   # 스크립트 오류·파이프 실패 시 즉시 종료

echo "🔐 PKCS#12 키스토어 생성을 시작합니다... (v2.0 - 개인키 기반)"

# ✅ 1) 32바이트 난수 기반 키스토어 비밀번호 생성 (평문 노출 금지)
KEYSTORE_PASSWORD="$(openssl rand -base64 32)"

# ✅ 2) 비밀번호를 안전한 파일에 저장 (현재 디렉터리 기준)
echo "$KEYSTORE_PASSWORD" > .keystore_pass
chmod 400 .keystore_pass   # 소유자 읽기 전용

echo "📝 키스토어 비밀번호를 .keystore_pass 파일에 저장했습니다."

# ✅ 3) keytool로 4096‑bit RSA 키 + 자기서명 인증서 생성 (마이그레이션된 별칭 사용)
echo "🔧 키스토어를 생성 중입니다..."
keytool -genkeypair \
  -alias jasypt-secret-key \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -storepass "$KEYSTORE_PASSWORD" \
  -keyalg RSA -keysize 4096 \
  -validity 3650 \
  -dname "CN=jasypt-v2, OU=Dev, O=YourOrg, L=Seoul, C=KR"

unset KEYSTORE_PASSWORD   # 메모리 해제
printf '\n✅ [완료] keystore.p12 생성 완료 — 비밀번호는 .keystore_pass 파일에 저장되었습니다.\n'

echo ""
echo "🔧 다음 단계 (v2.0 마이그레이션):"
echo "1. export KEYSTORE_PASSWORD=\$(cat .keystore_pass) 명령으로 환경변수 설정"
echo "2. 애플리케이션 시작: cd .. && ./gradlew bootRun --args='--spring.profiles.active=local'"
echo ""
echo "🔐 v2.0 마이그레이션 특징:"
echo "✅ 키스토어 비밀번호: P12 파일 열기용만 사용"
echo "✅ JASYPT 암호화: 키스토어 내부 개인키 자동 추출 사용"
echo "✅ 키 분리 완료: KEYSTORE_PASSWORD ≠ JASYPT 암호화 키"
echo "✅ 보안 강화: 키스토어 비밀번호 노출되어도 암호화 키는 안전"
echo ""
echo "🛡️  보안 주의사항:"
echo "- .keystore_pass 파일과 keystore.p12 파일은 절대 버전 관리에 포함하지 마세요"
echo "- 운영 환경에서는 적절한 권한 설정을 하세요"
echo "- 기존 JASYPT_STOREPASS 환경변수는 더 이상 사용하지 않습니다"
