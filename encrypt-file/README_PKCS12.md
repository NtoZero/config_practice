# JASYPT + PKCS#12 키스토어 기반 암호화 프로젝트

> **플레이북 v0.6** 기준으로 구현된 Spring Boot 애플리케이션

## 🎯 프로젝트 개요

PKCS#12 키스토어를 기반으로 한 JASYPT 암호화 시스템을 구현합니다.

### 🔐 주요 특징

- **PKCS#12 키스토어**: 4096-bit RSA 키를 사용한 강력한 암호화
- **JASYPT 통합**: Spring Boot와 완벽하게 통합된 암호화 서비스
- **MySQL 연동**: 암호화된 민감 정보를 데이터베이스에 안전하게 저장
- **REST API**: 암호화/복호화 및 사용자 관리 API 제공
- **멀티 프로파일**: local, prod 환경별 설정 분리

## 🚀 빠른 시작

### 1. 키스토어 생성

```bash
cd secrets
chmod +x create-keystore.sh
./create-keystore.sh
```

### 2. 환경변수 설정

```bash
export JASYPT_STOREPASS=$(cat secrets/.keystore_pass)
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

## 📚 API 사용법

### 헬스체크
```bash
curl http://localhost:8080/api/health
```

### 암호화/복호화
```bash
# 암호화
curl -X POST http://localhost:8080/api/encrypt \
  -H "Content-Type: application/json" \
  -d '{"plainText": "my-secret"}'

# 복호화
curl -X POST http://localhost:8080/api/encrypt/decrypt \
  -H "Content-Type: application/json" \
  -d '{"encryptedText": "ENC(encrypted-value)"}'
```

### 사용자 관리
```bash
# 사용자 생성
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "fullName": "테스트 사용자"
  }'
```

## 🔧 트러블슈팅

1. **키스토어 파일을 찾을 수 없음**: 경로 확인
2. **JASYPT_STOREPASS 미설정**: 환경변수 설정 확인
3. **데이터베이스 연결 실패**: MySQL 서비스 상태 확인

---

**⚠️ 보안 주의사항**

- 키스토어 파일과 비밀번호를 절대 버전 관리에 포함하지 마세요
- 운영 환경에서는 적절한 접근 권한을 설정하세요
