# 🔐 Keystore Property Source Spring Boot Starter (Refactored v1.2)

PKCS#12 키스토어에서 비밀 값을 읽어와 Spring Boot Environment에 동적으로 주입하는 스타터입니다.

> **⚠️ 중요:** 이 버전은 데이터 불일치 문제를 해결한 리팩토링된 버전입니다.  
> 원본 비밀번호 문자열이 Spring Environment에 안정적으로 주입됩니다.

## ✨ 특징

- **PKCS#12 키스토어 지원**: `.p12` 파일에서 alias별 비밀 값 자동 로드
- **Spring Boot 통합**: `EnvironmentPostProcessor`를 통한 자동 설정
- **플레이스홀더 해석**: `${JASYPT_PASSWORD}` 같은 플레이스홀더가 키스토어 값으로 자동 치환
- **데이터 무결성**: UTF-8 문자열 복원으로 원본 비밀번호 보장
- **Fat JAR 호환**: `ResourceLoader` 사용으로 모든 리소스 위치 지원
- **재사용 가능**: 여러 서비스에서 의존성만 추가하면 바로 사용

## 🔧 리팩토링 주요 변경사항 (v1.2)

### 문제 해결
- **기존 문제**: keytool로 저장된 키와 Base64 인코딩 방식으로 인한 데이터 불일치
- **해결책**: 
  - `KeystoreCreator.java`로 원본 UTF-8 문자열을 SecretKeySpec으로 저장
  - `KeystorePropertySource.java`에서 UTF-8 바이트 배열을 원본 문자열로 복원
  - 쓰기/읽기 로직의 완벽한 대칭성 확보

### 핵심 수정사항
```java
// AS-IS (문제 있던 코드)
values.put(alias, Base64.getEncoder().encodeToString(secret));

// TO-BE (수정된 코드)
values.put(alias, new String(secret, StandardCharsets.UTF_8));
```

## 🚀 빠른 시작

### 1. 키스토어 생성 (NEW!)

#### 방법 1: KeystoreCreator 직접 사용 (권장)
```bash
# 프로젝트 빌드
./gradlew build

# 데모 키스토어 생성 - 새로운 방식!
java -cp build/libs/encloader-0.0.1-SNAPSHOT.jar \
     com.keyloader.KeystoreCreator \
     secrets/keystore.p12 mypassword demo
```

#### 방법 2: 편의 스크립트 사용
```bash
# Linux/Mac
./scripts/create-keystore-refactored.sh mypassword

# Windows
scripts\create-keystore-refactored.bat mypassword
```

> **⚠️ 중요**: 기존 `keytool` 기반 스크립트는 **폐기(deprecated)**되었습니다.

### 2. 애플리케이션 설정

`application.yml`:
```yaml
spring:
  jasypt:
    encryptor:
      password: ${JASYPT_PASSWORD}  # 키스토어에서 원본 문자열로 자동 로드됨

keystore:
  path: file:secrets/keystore.p12
  password: ${KEYSTORE_PASSWORD}

# 데모용 설정들
demo:
  encrypted-value: ${DEMO_SECRET}
  database-password: ${DB_PASSWORD}
  api-key: ${API_KEY}
```

### 3. 애플리케이션 실행

```bash
java -Dkeystore.path=file:secrets/keystore.p12 \
     -Dkeystore.password=mypassword \
     -jar build/libs/encloader-0.0.1-SNAPSHOT.jar
```

### 4. 데이터 무결성 검증

실행 시 다음과 같은 출력을 확인할 수 있습니다:

```
🔍 Data Integrity Verification:
   ✅ JASYPT_PASSWORD: Valid UTF-8 string (not Base64)
   ✅ DEMO_SECRET: Valid UTF-8 string (not Base64)
   ✅ DB_PASSWORD: Valid UTF-8 string (not Base64)
   ✅ API_KEY: Valid UTF-8 string (not Base64)

✅ SUCCESS: Keystore properties successfully loaded!
✅ SUCCESS: Original UTF-8 strings properly restored!
✅ SUCCESS: Jasypt will receive actual password strings!
```

## 📋 설정 옵션

| 속성 | 기본값 | 설명 |
|------|--------|------|
| `keystore.path` | `file:secrets/keystore.p12` | 키스토어 파일 경로 |
| `keystore.password` | (필수) | 키스토어 패스워드 |

### 지원하는 리소스 위치

- `file:path/to/keystore.p12` - 파일 시스템
- `classpath:keystore.p12` - 클래스패스
- `https://example.com/keystore.p12` - HTTP(S) URL

## 🛠️ 키스토어 관리

### KeystoreCreator 사용 (권장)

```java
Map<String, String> secrets = Map.of(
    "JASYPT_PASSWORD", "my-secret-password",
    "DB_PASSWORD", "database-password",
    "API_KEY", "api-key-value"
);

KeystoreCreator.createKeystore(
    "path/to/keystore.p12", 
    "keystore-password", 
    "key-password", 
    secrets
);
```

### ~~keytool 사용~~ (폐기됨)

> **⚠️ 폐기됨**: keytool 기반 방식은 데이터 불일치 문제로 인해 더 이상 사용하지 않습니다.

## 🧪 테스트

### 전체 테스트 실행
```bash
./gradlew test
```

### 수동 테스트 (End-to-End)
```bash
# 1. 키스토어 생성
java -cp build/libs/encloader-0.0.1-SNAPSHOT.jar \
     com.keyloader.KeystoreCreator \
     secrets/keystore.p12 testpass demo

# 2. 데모 애플리케이션 실행
java -Dkeystore.path=file:secrets/keystore.p12 \
     -Dkeystore.password=testpass \
     -jar build/libs/encloader-0.0.1-SNAPSHOT.jar
```

### 성공 시 기대 결과
- ✅ 모든 속성이 "Valid UTF-8 string (not Base64)"로 표시
- ✅ Jasypt 패스워드가 원본 문자열로 정상 로드
- ✅ 플레이스홀더가 올바르게 해석됨

## 📚 동작 원리 (리팩토링됨)

### 쓰기 과정 (KeystoreCreator)
1. **문자열 → UTF-8 바이트**: 원본 비밀번호를 UTF-8 바이트 배열로 변환
2. **SecretKeySpec 생성**: 바이트 배열을 SecretKeySpec으로 감쌈
3. **키스토어 저장**: PKCS#12 형태로 안전하게 저장

### 읽기 과정 (KeystorePropertySource)
1. **초기화**: `KeystoreEnvironmentPostProcessor`가 Spring Boot 시작 시 실행
2. **키스토어 로드**: 설정된 경로에서 PKCS#12 키스토어 파일 읽기
3. **키 추출**: SecretKeySpec에서 `.getEncoded()`로 UTF-8 바이트 배열 획득
4. **문자열 복원**: `new String(bytes, StandardCharsets.UTF_8)`로 원본 문자열 복원
5. **속성 주입**: `MapPropertySource`로 Spring Environment에 최우선 순위로 추가
6. **플레이스홀더 해석**: `${ALIAS_NAME}` 형태의 플레이스홀더가 원본 문자열로 치환

### 대칭성 확보
```
원본 문자열 → UTF-8 바이트 → SecretKeySpec → 키스토어 저장
                                                    ↓
키스토어 로드 → SecretKeySpec → UTF-8 바이트 → 원본 문자열
```

## 🔒 보안 고려사항

- 키스토어 패스워드를 환경변수나 시스템 속성으로 전달
- 프로덕션에서는 키스토어 파일을 안전한 위치에 저장
- 로그에 비밀 값이 노출되지 않도록 주의 (데모 앱은 마스킹 처리됨)
- 키스토어 파일의 파일 권한 적절히 설정

## 🔄 마이그레이션 가이드

기존 keytool 기반 키스토어를 사용 중이라면:

1. **기존 키스토어 백업**
2. **KeystoreCreator로 새 키스토어 생성**
3. **동일한 alias와 값으로 재생성**
4. **테스트 후 기존 키스토어 교체**

## 📖 문서

자세한 내용은 다음 문서들을 참고하세요:

- [리팩토링 가이드 v1.2](docs/keystore_property_source_refactoring_guide_v_1_2.md)
- [설계서 v1.1](docs/backup/keystore_property_source_plan_v1.1.md)
- [사용 가이드](docs/usage/keystore_property_source_usage_guide_v1.1.md)

## 🤝 기여

이슈나 개선 제안이 있으시면 언제든 GitHub Issues를 통해 알려주세요.

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.

---

> **💡 참고**: 이 리팩토링으로 Jasypt 등 후속 로직이 정상적으로 동작하며, 관련 단위 테스트 및 통합 테스트가 모두 통과합니다.
