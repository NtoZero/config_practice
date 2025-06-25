# Encloader 우선순위 리팩토링 플랜

> **작성일:** 2025년 6월 26일  
> **대상:** encloader keystore-property-source-spring-boot-starter  
> **목표:** 운영환경 안전성을 위한 핵심 개선사항  
> **개발 환경:** Windows 기준, 리눅스 배포

## 1. 개요

현재 encloader의 운영환경 사용을 위해 필요한 핵심 보안 및 안정성 개선사항들을 우선순위별로 정리했습니다. 복잡한 기능보다는 실제 운영에 필요한 필수 요소들에 집중합니다.

**주요 개선 목표:**
- p12encload 플래그를 통한 선택적 활성화
- KeyExtractor 보안 강화 (프로덕션 환경 차단)
- Jasypt 의존성 최적화 (선택적 사용)
- 안정적인 에러 처리 및 재시도 로직
- 메모리 보안 강화

## 2. 우선순위별 작업 계획

### **Phase 1: 보안 강화 (최우선 - Week 1)**

#### 1.1 p12encload 플래그 지원
**목표**: 필요한 환경에서만 키스토어 기능 활성화

```java
// KeystoreEnvironmentPostProcessor 수정
@Override
public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
    // p12encload 플래그 확인 (기본값: false)
    String p12encloadFlag = env.getProperty("p12encload", "false");
    boolean isEnabled = Boolean.parseBoolean(p12encloadFlag);
    
    if (!isEnabled) {
        logger.info("Keystore loading skipped: p12encload flag is disabled");
        return; // 플래그가 false이면 전체 기능 비활성화
    }
    
    // 기존 키스토어 로딩 로직 수행
    performKeystoreLoading(env);
}
```

**설정 예시**:
```yaml
# 프로덕션 환경
p12encload: true

# 개발 환경 (기본값 false로 비활성화)
# p12encload: false
```

#### 1.2 KeyExtractor 보안 강화
**목표**: 프로덕션 환경에서 디버그 기능 완전 차단

**Option 1: 런타임 차단 (즉시 적용 가능)**
```java
@Component
@ConditionalOnProperty(name = "keystore.debug.enabled", havingValue = "true")
public class KeyExtractor {
    
    private final Environment environment;
    
    public KeyExtractor(Environment environment) {
        this.environment = environment;
        validateDebugAllowed();
    }
    
    private void validateDebugAllowed() {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProduction = Arrays.stream(activeProfiles)
            .anyMatch(profile -> profile.contains("prod"));
            
        if (isProduction) {
            throw new DebugFeatureBlockedException("KeyExtractor", 
                "Debug features are not allowed in production environment");
        }
    }
    
    public void extractKeys() {
        validateDebugAllowed(); // 매번 실행 시 검증
        // 기존 키 추출 로직
    }
}
```

**Option 2: 빌드 시 제외 (권장)**
```gradle
// build.gradle
sourceSets {
    main {
        java {
            if (project.hasProperty('production')) {
                exclude '**/KeyExtractor.java'
            }
        }
    }
}

// 프로덕션 빌드 태스크
task productionJar(type: Jar) {
    archiveClassifier = 'production'
    from sourceSets.main.output
    exclude '**/KeyExtractor.class'
}
```

#### 1.3 Jasypt 의존성 최적화
**목표**: Jasypt 없이도 기본 기능 동작하도록 개선

```gradle
// build.gradle - 의존성을 optional로 변경
dependencies {
    // 기존: implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
    // 변경: optional로 선언
    compileOnly 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
    testImplementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
}
```

```java
// EncFileValidator - Jasypt 선택적 지원
@Component
@ConditionalOnProperty(name = "keystore.validation.validateEncFiles", havingValue = "true")
public class EncFileValidator {
    
    private final boolean jasyptAvailable;
    
    public EncFileValidator() {
        this.jasyptAvailable = isJasyptAvailable();
    }
    
    private boolean isJasyptAvailable() {
        try {
            Class.forName("org.jasypt.encryption.pbe.StandardPBEStringEncryptor");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    public ValidationResult validateEncFiles(ConfigurableEnvironment env) {
        if (jasyptAvailable) {
            return validateWithJasypt(env);
        } else {
            return validateFormatOnly(env);
        }
    }
    
    private ValidationResult validateFormatOnly(ConfigurableEnvironment env) {
        // ENC(base64) 형식만 검증
        ValidationResult result = new ValidationResult();
        List<String> encProperties = findEncProperties(env);
        
        for (String property : encProperties) {
            String value = env.getProperty(property);
            if (isValidEncryptedFormat(value)) {
                result.addSuccess("Format validation passed: " + property);
            } else {
                result.addError("Invalid encrypted format: " + property);
            }
        }
        
        if (!encProperties.isEmpty()) {
            result.addWarning("Jasypt not available - performed format-only validation");
        }
        
        return result;
    }
}
```

### **Phase 2: 안정성 개선 (높음 - Week 2)**

#### 2.1 메모리 보안 강화
**목표**: 메모리에서 민감 정보 노출 최소화

```java
// SecureString 클래스 구현
public class SecureString implements AutoCloseable {
    private char[] data;
    private final Object lock = new Object();
    private volatile boolean cleared = false;
    
    public SecureString(String value) {
        if (value != null) {
            this.data = value.toCharArray();
        }
    }
    
    public String getAndClear() {
        synchronized (lock) {
            if (cleared || data == null) return null;
            String result = new String(data);
            Arrays.fill(data, '\0'); // 메모리에서 즉시 소거
            data = null;
            cleared = true;
            return result;
        }
    }
    
    @Override
    public void close() {
        synchronized (lock) {
            if (!cleared && data != null) {
                Arrays.fill(data, '\0');
                data = null;
                cleared = true;
            }
        }
    }
}
```

```java
// KeystorePropertySource 수정
public class KeystorePropertySource extends PropertySource<KeyStore> {
    private final Map<String, SecureString> secureValues = new ConcurrentHashMap<>();
    
    @Override
    public Object getProperty(String name) {
        SecureString secureValue = secureValues.get(name);
        return secureValue != null ? secureValue.peek() : null;
    }
    
    // 리소스 정리
    public void destroy() {
        secureValues.values().forEach(SecureString::close);
        secureValues.clear();
    }
}
```

#### 2.2 예외 처리 개선
**목표**: 구체적인 에러 타입과 적절한 대응

```java
// 예외 계층 구조
public class KeystoreException extends RuntimeException {
    protected final String location;
    
    public KeystoreException(String message, String location, Throwable cause) {
        super(message, cause);
        this.location = location;
    }
}

public class KeystoreNotFoundException extends KeystoreException {
    public KeystoreNotFoundException(String location, Throwable cause) {
        super("Keystore file not found", location, cause);
    }
}

public class KeystorePasswordException extends KeystoreException {
    public KeystorePasswordException(String location, Throwable cause) {
        super("Invalid keystore password", location, cause);
    }
}

public class DebugFeatureBlockedException extends KeystoreException {
    public DebugFeatureBlockedException(String feature, String reason) {
        super("Debug feature '" + feature + "' is blocked: " + reason, "debug", null);
    }
}
```

#### 2.3 재시도 로직
**목표**: 일시적 오류에 대한 복원력 제공

```java
@Component
public class KeystoreLoader {
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    public KeystorePropertySource loadWithRetry(String location, String password) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return KeystorePropertySource.from(location, password);
                
            } catch (KeystoreException e) {
                lastException = e;
                if (attempt < MAX_RETRIES && isRetryable(e)) {
                    logger.warn("Keystore load attempt {} failed: {}", attempt, e.getMessage());
                    sleepWithBackoff(RETRY_DELAY_MS * attempt);
                } else {
                    break;
                }
            }
        }
        
        throw new KeystoreLoadFailedException("Failed after " + MAX_RETRIES + " attempts", 
                                            location, lastException);
    }
    
    private boolean isRetryable(KeystoreException e) {
        // 비밀번호 오류나 형식 오류는 재시도하지 않음
        return !(e instanceof KeystorePasswordException || e instanceof KeystoreFormatException);
    }
}
```

### **Phase 3: 설정 및 검증 (중간 - Week 3)**

#### 3.1 설정 클래스 확장

```java
@ConfigurationProperties(prefix = "keystore")
@Data
@Validated
public class KeystoreProperties {
    
    private String path = "file:secrets/keystore.p12";
    private String password;
    private Validation validation = new Validation();
    private Debug debug = new Debug();
    
    @Data
    public static class Validation {
        private boolean validateOnStartup = true;
        private boolean validateEncFiles = true;
        private boolean failOnEncValidationError = false;
        private Set<String> requiredAliases = new HashSet<>();
    }
    
    @Data
    public static class Debug {
        private boolean enabled = false;
        private boolean allowInProduction = false;
        private Set<String> allowedProfiles = Set.of("dev", "test", "local");
    }
    
    public boolean isEnabled(Environment env) {
        return Boolean.parseBoolean(env.getProperty("p12encload", "false"));
    }
    
    public boolean isDebugAllowed(Environment env) {
        if (!debug.enabled) return false;
        
        String[] activeProfiles = env.getActiveProfiles();
        boolean isProduction = Arrays.stream(activeProfiles)
            .anyMatch(profile -> profile.contains("prod"));
            
        return !isProduction || debug.allowInProduction;
    }
}
```

#### 3.2 보안 로깅

```java
@Component
public class SecurityLogger {
    private static final Logger log = LoggerFactory.getLogger(SecurityLogger.class);
    
    public void logKeystoreSkipped(String reason) {
        log.info("Keystore loading skipped: {}", reason);
    }
    
    public void logKeystoreLoaded(String location, int aliasCount) {
        log.info("Keystore loaded from: {} with {} aliases", 
                maskLocation(location), aliasCount);
    }
    
    public void logDebugFeatureBlocked(String feature, String reason) {
        log.warn("Debug feature '{}' blocked: {}", feature, reason);
    }
    
    private String maskLocation(String location) {
        if (location == null) return "null";
        
        // Windows: file:C:/secrets/keystore.p12 -> file:C:***/keystore.p12
        if (location.contains(":/")) {
            return location.replaceAll("([A-Z]:/[^/]+/)[^/]*(/[^/]*\\.p12)", "$1***$2");
        }
        
        // Unix: file:/app/secrets/keystore.p12 -> file:***/keystore.p12
        return location.replaceAll("(/[^/]+/)[^/]*(/[^/]*\\.p12)", "/***/keystore.p12");
    }
}
```

### **Phase 4: 헬스체크 (낮음 - Week 4)**

#### 4.1 Spring Boot Actuator 연동

```java
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(name = "p12encload", havingValue = "true")
public class KeystoreHealthIndicator implements HealthIndicator {
    
    private final KeystoreProperties properties;
    private final Environment environment;
    
    @Override
    public Health health() {
        if (!properties.isEnabled(environment)) {
            return Health.up()
                .withDetail("status", "disabled")
                .withDetail("reason", "p12encload flag is false")
                .build();
        }
        
        try {
            KeystorePropertySource propertySource = KeystorePropertySource.from(
                properties.getPath(), properties.getPassword());
            
            return Health.up()
                .withDetail("location", maskSensitiveInfo(properties.getPath()))
                .withDetail("aliases", propertySource.getPropertyNames().length)
                .withDetail("jasypt.available", isJasyptAvailable())
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }
    
    private boolean isJasyptAvailable() {
        try {
            Class.forName("org.jasypt.encryption.pbe.StandardPBEStringEncryptor");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

## 3. 구현 순서 및 일정

### Week 1: 보안 강화 (최우선)
- [ ] p12encload 플래그 지원 구현
- [ ] KeyExtractor 보안 강화 (런타임 차단)
- [ ] Jasypt 의존성 최적화
- [ ] 기본 단위 테스트 작성

### Week 2: 안정성 개선
- [ ] SecureString 메모리 보안 적용
- [ ] 예외 계층 구조 구현
- [ ] KeystoreLoader 재시도 로직 구현
- [ ] 통합 테스트 작성

### Week 3: 설정 및 검증
- [ ] KeystoreProperties 확장
- [ ] SecurityLogger 구현
- [ ] EncFileValidator 완성
- [ ] 윈도우 환경 테스트

### Week 4: 헬스체크 및 최종 검증
- [ ] KeystoreHealthIndicator 구현
- [ ] 전체 시스템 통합 테스트
- [ ] 보안 검증 및 문서화
- [ ] 프로덕션 배포 가이드 작성

## 4. 설정 예시

### 4.1 프로덕션 환경 설정

```yaml
# application-prod.yml
spring:
  profiles:
    active: production

# 키스토어 기능 활성화
p12encload: true

keystore:
  path: ${PROD_KEYSTORE_PATH:file:/app/secrets/keystore.p12}
  password: ${PROD_KEYSTORE_PASSWORD}
  validation:
    validateOnStartup: true
    validateEncFiles: true
    failOnEncValidationError: true
    requiredAliases: 
      - JASYPT_PASSWORD
      - DB_PASSWORD
  debug:
    enabled: false  # 프로덕션에서 디버그 완전 비활성화
    allowInProduction: false

# 헬스체크 활성화
management:
  endpoints:
    web:
      exposure:
        include: health
  health:
    keystore:
      enabled: true
```

### 4.2 개발 환경 설정

```yaml
# application-dev.yml
spring:
  profiles:
    active: dev

# 키스토어 기능 비활성화 (필요시에만 활성화)
p12encload: false

keystore:
  debug:
    enabled: true
    allowedProfiles: [dev, test, local]
```

### 4.3 윈도우 배치 스크립트

```batch
@echo off
REM start-prod.bat

REM 프로덕션 환경 변수 설정
set SPRING_PROFILES_ACTIVE=production
set P12ENCLOAD=true
set PROD_KEYSTORE_PATH=file:C:/app/secrets/keystore.p12
set PROD_KEYSTORE_PASSWORD=%KEYSTORE_PASSWORD%

REM JVM 옵션
set JAVA_OPTS=-Xms512m -Xmx2g
set JAVA_OPTS=%JAVA_OPTS% -Dspring.profiles.active=%SPRING_PROFILES_ACTIVE%
set JAVA_OPTS=%JAVA_OPTS% -Dp12encload=%P12ENCLOAD%

REM 프로덕션 JAR 실행 (KeyExtractor 제외 버전)
java %JAVA_OPTS% -jar app-production.jar
```

## 5. 테스트 전략

### 5.1 보안 테스트
```java
@Test
void testKeyExtractorBlockedInProduction() {
    // 프로덕션 프로파일에서 KeyExtractor 차단 확인
    System.setProperty("spring.profiles.active", "production");
    
    assertThrows(DebugFeatureBlockedException.class, () -> {
        new KeyExtractor(environment);
    });
}

@Test
void testJasyptOptional() {
    // Jasypt 없이 애플리케이션 시작 확인
    // Format-only validation 동작 확인
}

@Test  
void testP12encloadFlag() {
    // p12encload=false일 때 기능 비활성화 확인
    // p12encload=true일 때 정상 동작 확인
}
```

### 5.2 윈도우 환경 테스트
```java
@EnabledOnOs(OS.WINDOWS)
@Test
void testWindowsPathHandling() {
    String windowsPath = "file:C:\\app\\secrets\\keystore.p12";
    assertDoesNotThrow(() -> {
        KeystorePropertySource.from(windowsPath, "password");
    });
}
```

## 6. 운영 체크리스트

### 배포 전 보안 점검
- [ ] Production JAR에서 KeyExtractor.class 제거 확인
- [ ] p12encload 플래그 설정 확인 (프로덕션: true, 개발: false)
- [ ] Jasypt 의존성 optional 설정 확인
- [ ] 프로덕션 프로파일에서 debug 기능 차단 확인

### 배포 후 동작 확인
- [ ] 키스토어 정상 로딩 확인
- [ ] 헬스체크 상태 확인 (/actuator/health)
- [ ] 로그에서 보안 관련 메시지 확인
- [ ] Jasypt 상태 올바른 표시 확인

## 7. 문제 해결 가이드

### 7.1 일반적인 문제들

**문제**: p12encload=true인데 키스토어가 로딩되지 않음
```
INFO: Keystore loading skipped: p12encload flag is disabled
```
**해결**: 환경변수나 JVM 옵션에서 플래그 값 확인

**문제**: Jasypt 없이 실행했는데 enc 파일 검증 실패
```
WARN: Jasypt library not available. Using format-only validation.
```
**해결**: `failOnEncValidationError: false` 설정으로 계속 진행

**문제**: Windows 경로에서 키스토어 로딩 실패
**해결**: 경로 형식 확인 (`file:C:/app/secrets/keystore.p12` 또는 `file:C:\\app\\secrets\\keystore.p12`)

## 8. 결론

이 우선순위 플랜은 encloader의 운영환경 사용을 위한 필수 보안 및 안정성 개선사항들에 집중합니다. 복잡한 기능보다는 실제로 필요한 핵심 요소들을 단계적으로 구현하여 안전하고 신뢰할 수 있는 라이브러리로 발전시키는 것을 목표로 합니다.

**핵심 가치:**
- **보안 우선**: KeyExtractor 차단, 메모리 보안 강화
- **선택적 사용**: p12encload 플래그로 필요한 곳에서만 활성화
- **의존성 최적화**: Jasypt 없이도 기본 기능 동작
- **안정성**: 재시도 로직과 구체적 예외 처리
- **윈도우 호환**: 개발 환경 고려한 경로 처리

이를 통해 개발팀이 안심하고 프로덕션 환경에서 사용할 수 있는 라이브러리가 될 것입니다.