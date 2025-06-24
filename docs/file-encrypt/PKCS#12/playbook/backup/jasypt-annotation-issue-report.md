# Jasypt @KeyStorePropertySource 애노테이션 문제 해결 보고서

## 📋 개요

Spring Boot + Jasypt (.p12) 플레이북 구현 중 `@KeyStorePropertySource` 애노테이션을 인식하지 못하는 문제가 발생했습니다. 본 보고서는 문제의 원인 분석과 해결 방안을 제시합니다.

---

## 🔍 문제 상황

### 발생한 오류
```java
import com.github.ulisesbocchio.jasyptspringboot.annotation.KeyStorePropertySource;
```
- **오류 메시지**: "Cannot resolve symbol 'github'" 및 "Cannot resolve symbol 'KeyStorePropertySource'"
- **사용 버전**: jasypt-spring-boot-starter 3.0.5
- **Spring Boot 버전**: 3.1.2

### 문제가 발생한 코드
```java
@Configuration
@KeyStorePropertySource(
    name = "jasyptKeyStore",
    location = "classpath:keystore.p12",
    type = "PKCS12",
    provider = "SUN",
    password = "${spring.jasypt.encryptor.key-store.secret}",
    alias = "${spring.jasypt.encryptor.key-store.alias}",
    keyPassword = "${spring.jasypt.encryptor.key-store.key-password}"
)
public class JasyptConfig {
    // ...
}
```

---

## 🔬 원인 분석

### 1. 애노테이션 존재 여부 확인
jasypt-spring-boot-starter 3.0.5의 공식 문서 및 GitHub 레포지토리 확인 결과:

**❌ 존재하지 않는 애노테이션**
- `@KeyStorePropertySource` - **jasypt-spring-boot-starter에 존재하지 않음**

**✅ 실제 제공되는 애노테이션들**
- `@EncryptablePropertySource` - 개별 프로퍼티 소스 암호화
- `@EncryptablePropertySources` - 다중 프로퍼티 소스 암호화
- `@EnableEncryptableProperties` - 전역 암호화 활성화

### 2. 플레이북 검증 결과
제공된 SPRING_JASYPT_P12_PLAYBOOK.md에 포함된 `@KeyStorePropertySource` 애노테이션은 **jasypt-spring-boot-starter에 존재하지 않는 잘못된 정보**입니다.

### 3. 의존성 확인
```gradle
dependencies {
    implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
}
```
- 의존성은 정상적으로 로드됨
- 문제는 존재하지 않는 애노테이션 사용에 있음

---

## 💡 해결 방안

### 방안 1: 자동 설정 사용 (추천 ⭐)

**가장 간단하고 안전한 방법**

```java
@SpringBootApplication
public class EncryptFileApplication {
    public static void main(String[] args) {
        SpringApplication.run(EncryptFileApplication.class, args);
    }
}
```

**특징:**
- `@SpringBootApplication` 사용 시 자동으로 전체 Spring Environment에서 암호화 지원
- 별도 설정 클래스 불필요
- 가장 권장되는 방법

**application.yml 설정:**
```yaml
spring:
  jasypt:
    encryptor:
      password: ${JASYPT_PASSWORD}
      algorithm: PBEWithMD5AndDES
      key-obtention-iterations: 1000
      pool-size: 1
      provider-name: SunJCE
      salt-generator-classname: org.jasypt.salt.RandomSaltGenerator
      string-output-type: base64

example:
  password: ENC(vG3k1Q5DfG2m9wz8YxA==)
```

### 방안 2: @EnableEncryptableProperties 사용

**전역 암호화를 명시적으로 활성화**

```java
@Configuration
@EnableEncryptableProperties
public class JasyptConfig {
    
    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(System.getenv("JASYPT_PASSWORD"));
        config.setAlgorithm("PBEWithMD5AndDES");
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setStringOutputType("base64");
        encryptor.setConfig(config);
        return encryptor;
    }
}
```

### 방안 3: @EncryptablePropertySource 사용

**개별 프로퍼티 파일 암호화**

```java
@Configuration
@EncryptablePropertySource(
    name = "encryptedProperties", 
    value = "classpath:encrypted.properties"
)
public class JasyptConfig {
    
    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor() {
        // 커스텀 encryptor 설정
        return createEncryptor();
    }
}
```

### 방안 4: PKCS#12 키스토어 기반 커스텀 구현

**키스토어를 직접 활용하는 방법**

```java
@Configuration
public class JasyptKeyStoreConfig {
    
    @Value("${JASYPT_STOREPASS}")
    private String storePassword;
    
    @Value("${JASYPT_KEYPASS}")
    private String keyPassword;
    
    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor() {
        try {
            // PKCS#12 키스토어 로드
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream keyStoreStream = 
                 getClass().getClassLoader().getResourceAsStream("keystore.p12")) {
                keyStore.load(keyStoreStream, storePassword.toCharArray());
            }
            
            // 비밀키 추출
            SecretKey secretKey = (SecretKey) keyStore.getKey(
                "jasypt-secret-key", keyPassword.toCharArray());
            
            // Jasypt Encryptor 설정
            PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
            SimpleStringPBEConfig config = new SimpleStringPBEConfig();
            config.setPassword(Base64.getEncoder().encodeToString(secretKey.getEncoded()));
            config.setAlgorithm("PBEWithMD5AndDES");
            config.setKeyObtentionIterations("1000");
            config.setPoolSize("1");
            config.setProviderName("SunJCE");
            config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
            config.setStringOutputType("base64");
            encryptor.setConfig(config);
            
            return encryptor;
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure Jasypt with KeyStore", e);
        }
    }
}
```

---

## 🎯 권장 구현 방안

### 단계별 마이그레이션 가이드

#### 1단계: 기존 코드 수정
```java
// ❌ 제거할 코드
@KeyStorePropertySource(...)
public class JasyptConfig {
    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor() {
        return null; // 이것도 제거
    }
}
```

#### 2단계: 간단한 자동 설정 적용
```java
// ✅ 새로운 코드 (방안 1 - 권장)
@SpringBootApplication
public class EncryptFileApplication {
    public static void main(String[] args) {
        SpringApplication.run(EncryptFileApplication.class, args);
    }
}
```

#### 3단계: application.yml 업데이트
```yaml
spring:
  jasypt:
    encryptor:
      password: ${JASYPT_PASSWORD:defaultPassword}
      algorithm: PBEWithMD5AndDES

example:
  password: ENC(vG3k1Q5DfG2m9wz8YxA==)
  database:
    url: ENC(your-encrypted-db-url)
    username: ENC(your-encrypted-username)
    password: ENC(your-encrypted-password)
```

#### 4단계: 환경 변수 설정
```bash
# Windows
set JASYPT_PASSWORD=mySecretPassword

# Linux/macOS
export JASYPT_PASSWORD=mySecretPassword
```

---

## 🧪 테스트 및 검증

### 1. 단위 테스트
```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.jasypt.encryptor.password=testPassword"
})
class JasyptConfigTest {
    
    @Autowired
    private StringEncryptor jasyptStringEncryptor;
    
    @Test
    void testEncryptionDecryption() {
        String original = "Hello, World!";
        String encrypted = jasyptStringEncryptor.encrypt(original);
        String decrypted = jasyptStringEncryptor.decrypt(encrypted);
        
        assertEquals(original, decrypted);
    }
}
```

### 2. 통합 테스트
```java
@SpringBootTest
class EncryptedPropertiesTest {
    
    @Value("${example.password}")
    private String decryptedPassword;
    
    @Test
    void testPropertyDecryption() {
        assertNotNull(decryptedPassword);
        assertNotEquals("ENC(vG3k1Q5DfG2m9wz8YxA==)", decryptedPassword);
    }
}
```

---

## 📊 비교 분석

| 방안 | 복잡도 | 보안성 | 유지보수성 | PKCS#12 지원 |
|------|--------|--------|------------|--------------|
| 자동 설정 | ⭐ 낮음 | ⭐⭐⭐ 중간 | ⭐⭐⭐ 높음 | ❌ 없음 |
| @EnableEncryptableProperties | ⭐⭐ 중간 | ⭐⭐⭐ 중간 | ⭐⭐ 중간 | ❌ 없음 |
| @EncryptablePropertySource | ⭐⭐⭐ 높음 | ⭐⭐ 낮음 | ⭐⭐ 중간 | ❌ 없음 |
| 커스텀 KeyStore 구현 | ⭐⭐⭐⭐ 매우 높음 | ⭐⭐⭐⭐ 높음 | ⭐ 낮음 | ✅ 완전 지원 |

---

## 🔒 보안 고려사항

### 1. 환경 변수 보안
- **개발 환경**: `.env` 파일 사용 (Git 제외)
- **프로덕션**: CI/CD 비밀 저장소 또는 AWS Secrets Manager 등 활용

### 2. 키 순환 (Key Rotation)
```bash
# 주기적 비밀번호 변경
export JASYPT_PASSWORD_OLD=oldPassword
export JASYPT_PASSWORD_NEW=newPassword

# 재암호화 스크립트 실행
./scripts/rotate-encryption-keys.sh
```

### 3. 감사 및 모니터링
```yaml
logging:
  level:
    com.github.ulisesbocchio.jasyptspringboot: INFO
    org.jasypt: WARN
```

---

## 🎯 결론 및 권장사항

### 즉시 적용 권장사항
1. **방안 1 (자동 설정)** 사용으로 간단하게 시작
2. PKCS#12 키스토어가 꼭 필요한 경우 **방안 4 (커스텀 구현)** 고려
3. 플레이북의 `@KeyStorePropertySource` 관련 내용 수정 필요

### 장기적 권장사항
1. Spring Cloud Config + Vault 등 엔터프라이즈급 보안 솔루션 검토
2. 정기적인 보안 감사 및 키 순환 프로세스 구축
3. 암호화 알고리즘 업그레이드 (AES-256 등) 고려

### 플레이북 개선사항
- `@KeyStorePropertySource` 제거
- 실제 동작하는 방안들로 대체
- 보안 best practices 추가
- 테스트 케이스 포함

---

## 📚 참고 자료

- [Jasypt Spring Boot GitHub](https://github.com/ulisesbocchio/jasypt-spring-boot)
- [Spring Boot Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Java KeyStore 가이드](https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html#KeyStore)
- [Spring Security Crypto](https://docs.spring.io/spring-security/reference/features/crypto.html)

---

**작성일**: 2025년 6월 24일  
**작성자**: Claude (Anthropic)  
**버전**: 1.0