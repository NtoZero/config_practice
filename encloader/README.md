# 🔐 Keystore Property Source Spring Boot Starter

PKCS#12 키스토어에서 비밀 값을 읽어와 Spring Boot Environment에 동적으로 주입하는 스타터입니다.

## ✨ 특징

- **PKCS#12 키스토어 지원**: `.p12` 파일에서 alias별 비밀 값 자동 로드
- **Spring Boot 통합**: `EnvironmentPostProcessor`를 통한 자동 설정
- **플레이스홀더 해석**: `${JASYPT_PASSWORD}` 같은 플레이스홀더가 키스토어 값으로 자동 치환
- **Fat JAR 호환**: `ResourceLoader` 사용으로 모든 리소스 위치 지원
- **재사용 가능**: 여러 서비스에서 의존성만 추가하면 바로 사용

## 🚀 빠른 시작

### 1. 의존성 추가

```gradle
dependencies {
    implementation 'com.example:keystore-property-source-spring-boot-starter'
}
```

### 2. 키스토어 생성

데모용 키스토어를 생성하려면:

```bash
# 프로젝트 빌드
./gradlew build

# 데모 키스토어 생성
java -cp build/classes/java/main com.example.keystore.KeystoreCreator \
     secrets/keystore.p12 mypassword demo
```

### 3. 애플리케이션 설정

`application.yml`:
```yaml
spring:
  jasypt:
    encryptor:
      password: ${JASYPT_PASSWORD}  # 키스토어에서 자동 로드됨

keystore:
  path: file:secrets/keystore.p12
  password: ${KEYSTORE_PASSWORD}
```

### 4. 애플리케이션 실행

```bash
java -Dkeystore.password=mypassword -jar your-app.jar
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

### keytool로 키 추가

```bash
# 패스워드 키 추가
keytool -importpass -alias JASYPT_PASSWORD \
        -keystore secrets/keystore.p12 -storetype PKCS12 \
        -storepass mypassword
```

### 프로그래밍 방식으로 생성

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

## 🧪 테스트

```bash
# 단위 테스트 실행
./gradlew test

# 데모 애플리케이션 실행 (키스토어 생성 후)
java -Dkeystore.password=mypassword -jar build/libs/encloader-0.0.1-SNAPSHOT.jar
```

## 📚 동작 원리

1. **초기화**: `KeystoreEnvironmentPostProcessor`가 Spring Boot 시작 시 실행
2. **키스토어 로드**: 설정된 경로에서 PKCS#12 키스토어 파일 읽기
3. **키 추출**: 모든 key entry를 Base64 인코딩하여 추출
4. **속성 주입**: `MapPropertySource`로 Spring Environment에 최우선 순위로 추가
5. **플레이스홀더 해석**: `${ALIAS_NAME}` 형태의 플레이스홀더가 키스토어 값으로 치환

## 🔒 보안 고려사항

- 키스토어 패스워드를 환경변수나 시스템 속성으로 전달
- 프로덕션에서는 키스토어 파일을 안전한 위치에 저장
- 로그에 비밀 값이 노출되지 않도록 주의
- 키스토어 파일의 파일 권한 적절히 설정

## 📖 문서

자세한 내용은 다음 문서들을 참고하세요:

- [설계서 v1.1](docs/keystore_property_source_plan_v1.1.md)
- [사용 가이드](docs/usage/keystore_property_source_usage_guide_v1.1.md)

## 🤝 기여

이슈나 개선 제안이 있으시면 언제든 GitHub Issues를 통해 알려주세요.

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.
