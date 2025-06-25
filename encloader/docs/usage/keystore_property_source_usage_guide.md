
# keystore-property-source-spring-boot-starter **사용 가이드**

> **버전:** 1.0.0  
> **작성일:** 2025-06-25  
> **대상:** 스타터 의존성을 **소비**(main module)하는 개발자  
> **환경:** Spring Boot 3.x · Java 17+

---

## 1. 개요

`keystore-property-source-spring-boot-starter` 는  
**PKCS#12 keystore**(예: `secrets/keystore.p12`)의 alias 값을 Spring Boot `Environment` 에
`PropertySource` 로 삽입합니다.  
`application.yml` / `application.properties` 내부에서 `${{JASYPT_PASSWORD}}` 처럼
플레이스홀더를 선언하면 keystore 값으로 자동 치환됩니다.

---

## 2. 설치

### 2.1 Gradle (`build.gradle.kts`)

```kotlin
dependencies {{
    implementation("com.example:keystore-property-source-spring-boot-starter:1.0.0")
}}
```

### 2.2 Maven (`pom.xml`)

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>keystore-property-source-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

---

## 3. keystore 준비

1. PKCS#12 파일 생성(없다면):
   ```bash
   keytool -genkey -alias temp -keystore secrets/keystore.p12 \
           -storetype PKCS12 -storepass "$STORE_PASS" \
           -dname "CN=temp" -keyalg RSA
   ```
2. 비밀번호 등 **문자 값** 저장:
   ```bash
   keytool -importpass -alias JASYPT_PASSWORD \
           -keystore secrets/keystore.p12 -storetype PKCS12 \
           -storepass "$STORE_PASS" -keypass "$ENTRY_PASS" \
           -keyalg AES -keysize 256
   ```
3. alias 이름은 **플레이스홀더 키와 동일**해야 합니다.

---

## 4. 애플리케이션 구성

### 4.1 `application.yml`

```yaml
spring:
  jasypt:
    encryptor:
      password: ${{JASYPT_PASSWORD}}   # ← keystore alias 와 동일
custom:
  api-key: ${{MY_API_KEY}}   # 예시: alias=MY_API_KEY
```

별도 설정 파일 수정은 필요 없습니다.

### 4.2 실행 파라미터

| 옵션 | 설명 | 예시 |
|------|------|------|
| `keystore.path` | keystore 경로(필수) | `-Dkeystore.path=file:secrets/keystore.p12` |
| `keystore.password` | keystore 비밀번호(필수) | `-Dkeystore.password=$STORE_PASS` |

```bash
java -Dkeystore.path=file:secrets/keystore.p12 \
     -Dkeystore.password="$STORE_PASS" \
     -jar main-module.jar
```

**TIP:** 비밀번호 노출이 걱정된다면 `--spring.config.import=parameterstore:` 등
비밀 관리 서비스를 사용해도 됩니다.

---

## 5. 프로파일별 keystore 분리

여러 환경을 사용한다면 프로파일마다 다른 파일을 지정하면 됩니다:

```bash
# dev
java -Dspring.profiles.active=dev \
     -Dkeystore.path=file:keystore-dev.p12 \
     …
# prod
java -Dspring.profiles.active=prod \
     -Dkeystore.path=file:/opt/secure/keystore-prod.p12 \
     …
```

---

## 6. 테스트 코드에서 사용

통합 테스트 시 **메모리 keystore** 를 생성해 `DynamicPropertySource` 로 주입하면
별도 파일이 없어도 됩니다.

```java
@DynamicPropertySource
static void registerKeystore(DynamicPropertyRegistry registry) {{
    String p12 = InMemoryKeystore.create()
                  .withAlias("JASYPT_PASSWORD", "test123!".toCharArray())
                  .build();
    registry.add("keystore.path", () -> "data:application/x-pkcs12;base64," + p12);
    registry.add("keystore.password", () -> "");
}}
```

> 자세한 예시는 `samples/boot3-demo` 모듈 참고

---

## 7. 문제 해결 FAQ

| 증상 | 원인 / 해결 |
|------|-------------|
| `java.security.UnrecoverableKeyException` | alias 또는 entry password 불일치 → `keytool -list -v` 로 확인 |
| 플레이스홀더가 `null` 로 해석 | ① alias 존재 여부 ② PropertySource 우선순위 확인 |
| `EnvironmentPostProcessor` 미동작 | **spring.factories** 누락 또는 스타터 버전 불일치 확인 |

---

## 8. 버전 호환성

| Spring Boot | Java | 스타터 최소 버전 |
|-------------|------|-----------------|
| 3.2.x | 17 | 1.0.0 |
| 3.1.x | 17 | 1.0.0 |

> Boot 3.0.x 이하 또는 Java 11 이하에서는 동작 보장하지 않습니다.

---

## 9. 라이선스 & 기여

- **Apache‑2.0**  
- 이슈 및 PR은 GitHub Issues / Pull Requests 로 부탁드립니다.

---

## 10. 연락처

궁금한 점은 GitHub Discussions 또는 Slack #keystore‑starter 채널에
문의해 주세요.  
행복한 개발 되시길 바랍니다, **대장님**! 🚀
