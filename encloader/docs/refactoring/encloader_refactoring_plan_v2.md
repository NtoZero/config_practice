# Encloader 운영환경 리팩토링 플랜 v2.0

> **작성일:** 2025년 6월 26일  
> **대상:** encloader keystore-property-source-spring-boot-starter  
> **목표:** 운영환경에서 안전하고 안정적인 사용을 위한 핵심 개선  
> **버전:** 2.0 (p12encload 플래그 지원 추가)

## 1. 개요

현재 encloader는 개발/테스트 환경에서는 잘 동작하지만, 운영환경에서 사용하기 위해서는 보안, 안정성, 모니터링 측면에서 개선이 필요합니다. 

**v2.0 주요 변경사항:**
- `p12encload` 플래그를 통한 선택적 enc 파일 검증 기능 추가
- 플래그가 `true`일 때만 키스토어 로딩 및 검증 수행
- 플래그가 `false`이거나 없으면 기능 비활성화

본 플랜은 클라우드 환경 지원이나 복잡한 캐싱 로직은 제외하고, 핵심적이고 실용적인 개선사항에 집중합니다.

## 2. 현재 구조 분석

### 2.1 주요 클래스 역할
- **KeystoreEnvironmentPostProcessor**: Spring Boot 초기화 시점에 키스토어 로딩 및 PropertySource 등록
- **KeystorePropertySource**: PKCS#12 키스토어에서 비밀 값 읽기 및 UTF-8 문자열 복원
- **KeystoreCreator**: 개발/테스트용 키스토어 생성 (keytool 대체)
- **KeyExtractor**: 디버깅용 키 추출 유틸리티

### 2.2 현재 한계점
- 항상 키스토어 로딩을 시도함 (선택적 비활성화 불가)
- 메모리에 평문 비밀번호 저장
- 단순한 에러 처리
- 설정 검증 부족
- 운영환경 모니터링 기능 없음

### 2.3 새로운 요구사항: p12encload 플래그
- `p12encload=true`: 키스토어 로딩 및 enc 파일 검증 활성화
- `p12encload=false` 또는 미설정: 모든 keystore 관련 기능 비활성화
- 이를 통해 필요한 환경에서만 선택적으로 기능 사용 가능

## 3. 리팩토링 계획

### Phase 0: p12encload 플래그 지원 (우선순위: 최고)

#### 0.1 플래그 기반 조건부 활성화

**목표**: p12encload 플래그에 따른 선택적 기능 활성화

**구현 계획**:

```java
// KeystoreEnvironmentPostProcessor 수정
@Override
public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
    // 1단계: p12encload 플래그 확인
    String p12encloadFlag = env.getProperty("p12encload", "false");
    boolean isEnabled = Boolean.parseBoolean(p12encloadFlag);
    
    if (!isEnabled) {
        securityLogger.logKeystoreSkipped("p12encload flag is disabled");
        return; // 플래그가 false이면 전체 기능 비활성화
    }
    
    // 2단계: 기존 로직 수행 (플래그가 true일 때만)
    String path = env.getProperty("keystore.path", "file:secrets/keystore.p12");
    String password = env.getProperty("keystore.password");
    
    if (password == null) {
        securityLogger.logKeystoreSkipped("keystore.password not provided");
        return;
    }

    try {
        KeystorePropertySource src = keystoreLoader.loadWithRetry(path, password);
        
        // 3단계: enc 파일 검증 (p12encload=true일 때만)
        if (properties.getValidation().isValidateEncFiles()) {
            validateEncFiles(env, src);
        }
        
        env.getPropertySources().addFirst(src);
        securityLogger.logKeystoreLoaded(path, src.getPropertyNames().length);
        
    } catch (Exception e) {
        handleKeystoreLoadFailure(e, path);
    }
}

private void validateEncFiles(ConfigurableEnvironment env, KeystorePropertySource propertySource) {
    // enc 파일 검증 로직
    EncFileValidator encValidator = new EncFileValidator(propertySource);
    ValidationResult result = encValidator.validateEncFiles(env);
    
    if (result.hasErrors() && properties.getValidation().isFailOnEncValidationError()) {
        throw new EncFileValidationException("Enc file validation failed", result.getErrors());
    }
}
```

#### 0.2 EncFile 검증 컴포넌트

**목표**: p12encload=true일 때 enc 파일의 복호화 가능성 검증

**구현 계획**:

```java
// 새 클래스: EncFileValidator
@Component
public class EncFileValidator {
    
    private final KeystorePropertySource keystorePropertySource;
    private final SecurityLogger securityLogger;
    
    public EncFileValidator(KeystorePropertySource keystorePropertySource) {
        this.keystorePropertySource = keystorePropertySource;
    }
    
    /**
     * 환경에서 enc 파일들을 찾아 검증
     */
    public ValidationResult validateEncFiles(ConfigurableEnvironment env) {
        ValidationResult result = new ValidationResult();
        
        // 1. 환경에서 .enc로 끝나는 속성들 찾기
        List<String> encProperties = findEncProperties(env);
        
        if (encProperties.isEmpty()) {
            securityLogger.logEncValidation("No .enc properties found");
            return result;
        }
        
        // 2. 각 enc 속성에 대해 복호화 테스트
        for (String encProperty : encProperties) {
            validateSingleEncProperty(encProperty, env, result);
        }
        
        return result;
    }
    
    private List<String> findEncProperties(ConfigurableEnvironment env) {
        List<String> encProperties = new ArrayList<>();
        
        for (PropertySource<?> propertySource : env.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource) {
                EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) propertySource;
                for (String propertyName : enumerable.getPropertyNames()) {
                    if (propertyName.endsWith(".enc") || isEncryptedValue(env.getProperty(propertyName))) {
                        encProperties.add(propertyName);
                    }
                }
            }
        }
        
        return encProperties;
    }
    
    private void validateSingleEncProperty(String propertyName, ConfigurableEnvironment env, ValidationResult result) {
        try {
            String encValue = env.getProperty(propertyName);
            if (encValue == null) {
                result.addError("Enc property " + propertyName + " has null value");
                return;
            }
            
            // Jasypt 복호화 시도 (실제 복호화는 하지 않고 형식만 검증)
            if (isValidEncryptedFormat(encValue)) {
                // 키스토어에서 JASYPT_PASSWORD 가져와서 복호화 테스트
                String jasyptPassword = (String) keystorePropertySource.getProperty("JASYPT_PASSWORD");
                if (jasyptPassword != null) {
                    testDecryption(encValue, jasyptPassword, propertyName, result);
                } else {
                    result.addWarning("JASYPT_PASSWORD not found in keystore for property: " + propertyName);
                }
            } else {
                result.addError("Invalid encrypted format for property: " + propertyName);
            }
            
        } catch (Exception e) {
            result.addError("Failed to validate enc property " + propertyName + ": " + e.getMessage());
        }
    }
    
    private boolean isEncryptedValue(String value) {
        return value != null && value.startsWith("ENC(") && value.endsWith(")");
    }
    
    private boolean isValidEncryptedFormat(String encValue) {
        return encValue.matches("^ENC\\([A-Za-z0-9+/=]+\\)$");
    }
    
    private void testDecryption(String encValue, String password, String propertyName, ValidationResult result) {
        try {
            // 간단한 Jasypt 복호화 테스트 (실제 값은 사용하지 않음)
            StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
            encryptor.setPassword(password);
            encryptor.setAlgorithm("PBEWithMD5AndDES");
            
            String encryptedValue = encValue.substring(4, encValue.length() - 1); // ENC(...) 제거
            String decrypted = encryptor.decrypt(encryptedValue);
            
            if (decrypted != null && !decrypted.trim().isEmpty()) {
                result.addSuccess("Successfully validated enc property: " + propertyName);
                securityLogger.logEncValidation("Enc property validated: " + propertyName);
            } else {
                result.addError("Decrypted value is empty for property: " + propertyName);
            }
            
        } catch (Exception e) {
            result.addError("Decryption failed for property " + propertyName + ": " + e.getMessage());
        }
    }
}
```

#### 0.3 설정 클래스 확장

**목표**: p12encload 관련 설정 옵션 추가

```java
@ConfigurationProperties(prefix = "keystore")
@Data
@Validated
public class KeystoreProperties {
    
    @NotBlank(message = "Keystore path must not be blank")
    private String path = "file:secrets/keystore.p12";
    
    @NotBlank(message = "Keystore password must not be blank")
    private String password;
    
    @Valid
    private Validation validation = new Validation();
    
    @Data
    public static class Validation {
        private boolean failOnMissingAlias = false;
        private Set<String> requiredAliases = new HashSet<>();
        private boolean validateOnStartup = true;
        
        // 새로운 enc 파일 검증 관련 설정
        private boolean validateEncFiles = true;
        private boolean failOnEncValidationError = false;
        private Set<String> encFileExtensions = Set.of(".enc");
        private boolean skipEncValidationOnMissingJasyptPassword = true;
    }
    
    public boolean isEnabled(Environment env) {
        String p12encloadFlag = env.getProperty("p12encload", "false");
        return Boolean.parseBoolean(p12encloadFlag);
    }
    
    public boolean isValid() {
        return StringUtils.hasText(path) && StringUtils.hasText(password);
    }
    
    public List<String> getValidationErrors() {
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasText(path)) {
            errors.add("Keystore path is required");
        }
        if (!StringUtils.hasText(password)) {
            errors.add("Keystore password is required");
        }
        return errors;
    }
}
```

### Phase 1: 보안 강화 (우선순위: 높음)

#### 1.1 메모리 보안 개선

**목표**: 메모리에서 민감 정보 노출 최소화

**구현 계획**:
```java
// 새 클래스: SecureString
public class SecureString {
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
    
    public String peek() {
        synchronized (lock) {
            if (cleared || data == null) return null;
            return new String(data);
        }
    }
    
    public boolean isCleared() {
        return cleared;
    }
    
    @Override
    protected void finalize() throws Throwable {
        if (!cleared && data != null) {
            Arrays.fill(data, '\0');
        }
        super.finalize();
    }
}
```

**수정 파일**:
- `KeystorePropertySource.java`: values Map을 SecureString으로 변경
- 새 파일: `com/example/keystore/security/SecureString.java`

#### 1.2 로깅 보안

**목표**: 민감 정보가 로그에 노출되지 않도록 방지

**구현 계획**:
```java
// 새 클래스: SecurityLogger
@Component
public class SecurityLogger {
    private static final Logger log = LoggerFactory.getLogger(SecurityLogger.class);
    
    public void logKeystoreSkipped(String reason) {
        log.info("Keystore loading skipped: {}", reason);
    }
    
    public void logKeystoreLoaded(String location, int aliasCount) {
        log.info("Keystore loaded successfully from location: {} with {} aliases", 
                maskLocation(location), aliasCount);
    }
    
    public void logEncValidation(String message) {
        log.debug("Enc file validation: {}", message);
    }
    
    public void logRetryAttempt(int attempt, String location, String errorMessage) {
        log.warn("Keystore load attempt {} failed for location: {} - {}", 
                attempt, maskLocation(location), errorMessage);
    }
    
    private String maskLocation(String location) {
        if (location == null) return "null";
        // file:C:/secrets/keystore.p12 -> file:***/keystore.p12
        return location.replaceAll("([^/\\\\]+)[/\\\\]([^/\\\\]+\\.p12)", "***/$2");
    }
}
```

**수정 파일**:
- 새 파일: `com/example/keystore/logging/SecurityLogger.java`
- `KeystorePropertySource.java`: 로깅 추가

### Phase 2: 에러 처리 및 안정성 개선 (우선순위: 높음)

#### 2.1 구체적인 예외 타입 정의

**목표**: 에러 원인 파악과 적절한 대응을 위한 예외 체계 구축

**구현 계획**:
```java
// 예외 계층 구조
public class KeystoreException extends RuntimeException {
    protected final String location;
    
    public KeystoreException(String message, String location, Throwable cause) {
        super(message, cause);
        this.location = location;
    }
    
    public String getLocation() { return location; }
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

public class KeystoreFormatException extends KeystoreException {
    public KeystoreFormatException(String location, Throwable cause) {
        super("Invalid keystore format", location, cause);
    }
}

// 새로운 Enc 파일 검증 예외
public class EncFileValidationException extends KeystoreException {
    private final List<String> validationErrors;
    
    public EncFileValidationException(String message, List<String> errors) {
        super(message, "enc-files", null);
        this.validationErrors = new ArrayList<>(errors);
    }
    
    public List<String> getValidationErrors() { 
        return new ArrayList<>(validationErrors); 
    }
}
```

**수정 파일**:
- 새 패키지: `com/example/keystore/exception/`
- `KeystorePropertySource.java`: 구체적 예외 타입 사용

#### 2.2 재시도 로직

**목표**: 일시적 오류에 대한 복원력 제공

**구현 계획**:
```java
@Component
public class KeystoreLoader {
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    private final SecurityLogger securityLogger;
    private final KeystoreMetrics keystoreMetrics;
    
    public KeystorePropertySource loadWithRetry(String location, String password) {
        Exception lastException = null;
        Timer.Sample sample = keystoreMetrics.startLoadTimer();
        
        try {
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    KeystorePropertySource result = KeystorePropertySource.from(location, password);
                    keystoreMetrics.recordLoadSuccess();
                    return result;
                    
                } catch (KeystoreException e) {
                    lastException = e;
                    if (attempt < MAX_RETRIES && isRetryable(e)) {
                        securityLogger.logRetryAttempt(attempt, location, e.getMessage());
                        sleep(RETRY_DELAY_MS * attempt);
                    } else {
                        break;
                    }
                }
            }
            
            keystoreMetrics.recordLoadFailure(lastException.getClass().getSimpleName());
            throw new KeystoreLoadFailedException("Failed to load after " + MAX_RETRIES + " attempts", 
                                                location, lastException);
        } finally {
            sample.stop(keystoreMetrics.getLoadTimer());
        }
    }
    
    private boolean isRetryable(KeystoreException e) {
        // 비밀번호 오류나 형식 오류는 재시도하지 않음
        return !(e instanceof KeystorePasswordException || e instanceof KeystoreFormatException);
    }
    
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry delay", e);
        }
    }
}

public class KeystoreLoadFailedException extends KeystoreException {
    private final int attemptCount;
    
    public KeystoreLoadFailedException(String message, String location, Throwable cause) {
        super(message, location, cause);
        this.attemptCount = MAX_RETRIES;
    }
    
    public int getAttemptCount() { return attemptCount; }
}
```

**수정 파일**:
- 새 파일: `com/example/keystore/loader/KeystoreLoader.java`
- `KeystoreEnvironmentPostProcessor.java`: KeystoreLoader 사용

### Phase 3: 설정 검증 강화 (우선순위: 중간)

#### 3.1 필수 Alias 검증

**목표**: 운영에 필요한 키가 모두 존재하는지 확인

**구현 계획**:
```java
@Component
public class KeystoreValidator {
    
    private final SecurityLogger securityLogger;
    
    public ValidationResult validate(KeystorePropertySource propertySource, KeystoreProperties properties) {
        ValidationResult result = new ValidationResult();
        
        if (properties.getValidation().isValidateOnStartup()) {
            validateRequiredAliases(propertySource, properties.getValidation().getRequiredAliases(), result);
            validateAliasValues(propertySource, result);
        }
        
        return result;
    }
    
    private void validateRequiredAliases(KeystorePropertySource propertySource, 
                                       Set<String> requiredAliases, 
                                       ValidationResult result) {
        for (String alias : requiredAliases) {
            if (!propertySource.containsProperty(alias)) {
                result.addError("Required alias not found: " + alias);
                securityLogger.logEncValidation("Missing required alias: " + alias);
            }
        }
    }
    
    private void validateAliasValues(KeystorePropertySource propertySource, ValidationResult result) {
        for (String alias : propertySource.getPropertyNames()) {
            Object value = propertySource.getProperty(alias);
            if (value == null || value.toString().trim().isEmpty()) {
                result.addWarning("Empty value for alias: " + alias);
            }
        }
    }
}

// ValidationResult 클래스
public class ValidationResult {
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> successes = new ArrayList<>();
    
    public void addError(String error) { errors.add(error); }
    public void addWarning(String warning) { warnings.add(warning); }
    public void addSuccess(String success) { successes.add(success); }
    
    public boolean hasErrors() { return !errors.isEmpty(); }
    public boolean hasWarnings() { return !warnings.isEmpty(); }
    
    public List<String> getErrors() { return new ArrayList<>(errors); }
    public List<String> getWarnings() { return new ArrayList<>(warnings); }
    public List<String> getSuccesses() { return new ArrayList<>(successes); }
    
    public String getSummary() {
        return String.format("Validation Result - Errors: %d, Warnings: %d, Successes: %d", 
                           errors.size(), warnings.size(), successes.size());
    }
}
```

**수정 파일**:
- 새 파일: `com/example/keystore/validation/KeystoreValidator.java`
- 새 파일: `com/example/keystore/validation/ValidationResult.java`

### Phase 4: 모니터링 및 헬스체크 (우선순위: 중간)

#### 4.1 Spring Boot Actuator 연동

**목표**: 키스토어 상태 모니터링 및 헬스체크

**구현 계획**:
```java
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(name = "p12encload", havingValue = "true")
public class KeystoreHealthIndicator implements HealthIndicator {
    
    private final KeystoreProperties properties;
    private final KeystoreValidator validator;
    private final Environment environment;
    
    @Override
    public Health health() {
        // p12encload 플래그 확인
        if (!properties.isEnabled(environment)) {
            return Health.up()
                .withDetail("status", "disabled")
                .withDetail("reason", "p12encload flag is false")
                .build();
        }
        
        try {
            KeystorePropertySource propertySource = KeystorePropertySource.from(
                properties.getPath(), 
                properties.getPassword()
            );
            
            ValidationResult validation = validator.validate(propertySource, properties);
            
            Health.Builder builder = validation.hasErrors() ? Health.down() : Health.up();
            
            return builder
                .withDetail("location", maskSensitiveInfo(properties.getPath()))
                .withDetail("aliases.count", propertySource.getPropertyNames().length)
                .withDetail("validation.errors", validation.getErrors())
                .withDetail("validation.warnings", validation.getWarnings())
                .withDetail("p12encload.enabled", true)
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .withDetail("p12encload.enabled", true)
                .build();
        }
    }
    
    private String maskSensitiveInfo(String path) {
        if (path == null) return null;
        return path.replaceAll("(password=)[^&]*", "$1***");
    }
}
```

**필요한 의존성**:
```gradle
// build.gradle에 추가
implementation 'org.springframework.boot:spring-boot-starter-actuator'
// Jasypt 의존성 (enc 파일 검증용)
implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
```

**수정 파일**:
- 새 파일: `com/example/keystore/actuator/KeystoreHealthIndicator.java`

#### 4.2 메트릭 수집

**목표**: 키스토어 로딩 성능 및 실패율 모니터링

**구현 계획**:
```java
@Component
@ConditionalOnClass(MeterRegistry.class)
public class KeystoreMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Counter loadSuccessCounter;
    private final Counter loadFailureCounter;
    private final Timer loadTimer;
    private final Counter encValidationCounter;
    
    public KeystoreMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.loadSuccessCounter = Counter.builder("keystore.load.success")
            .description("Successful keystore loads")
            .register(meterRegistry);
        this.loadFailureCounter = Counter.builder("keystore.load.failure")
            .description("Failed keystore loads")
            .register(meterRegistry);
        this.loadTimer = Timer.builder("keystore.load.duration")
            .description("Keystore load duration")
            .register(meterRegistry);
        this.encValidationCounter = Counter.builder("keystore.enc.validation")
            .description("Enc file validation results")
            .register(meterRegistry);
    }
    
    public void recordLoadSuccess() {
        loadSuccessCounter.increment();
    }
    
    public void recordLoadFailure(String errorType) {
        loadFailureCounter.increment(Tags.of("error.type", errorType));
    }
    
    public void recordEncValidation(String result) {
        encValidationCounter.increment(Tags.of("result", result));
    }
    
    public Timer.Sample startLoadTimer() {
        return Timer.start(meterRegistry);
    }
    
    public Timer getLoadTimer() {
        return loadTimer;
    }
}
```

**수정 파일**:
- 새 파일: `com/example/keystore/metrics/KeystoreMetrics.java`
- `KeystoreLoader.java`: 메트릭 수집 로직 추가

## 4. 구현 순서

### Week 1: p12encload 플래그 지원 (최우선)
1. KeystoreEnvironmentPostProcessor에 플래그 확인 로직 추가
2. EncFileValidator 구현
3. KeystoreProperties에 enc 검증 설정 추가
4. 플래그별 동작 테스트 작성

### Week 2: 보안 강화
1. SecureString 클래스 구현
2. SecurityLogger 구현
3. KeystorePropertySource 메모리 보안 적용
4. 단위 테스트 작성

### Week 3: 에러 처리 개선
1. 예외 계층 구조 구현
2. KeystoreLoader 재시도 로직 구현
3. EncFileValidationException 추가
4. 통합 테스트 작성

### Week 4: 설정 검증 및 모니터링
1. KeystoreValidator 구현
2. KeystoreHealthIndicator 구현 (p12encload 조건부)
3. KeystoreMetrics 구현
4. 전체 통합 테스트 및 문서 업데이트

## 5. 테스트 전략

### 5.1 플래그 기반 테스트
```java
@TestMethodOrder(OrderAnnotation.class)
class P12EncloadFlagTest {
    
    @Test
    @Order(1)
    void testP12encloadFalse() {
        // p12encload=false일 때 키스토어 로딩 비활성화 확인
    }
    
    @Test
    @Order(2)
    void testP12encloadTrue() {
        // p12encload=true일 때 정상 동작 확인
    }
    
    @Test
    @Order(3)
    void testP12encloadMissing() {
        // p12encload 속성이 없을 때 기본값(false) 동작 확인
    }
}
```

### 5.2 Enc 파일 검증 테스트
```java
@TestMethodOrder(OrderAnnotation.class)
class EncFileValidationTest {
    
    @Test
    void testValidEncFile() {
        // 유효한 enc 파일 검증 성공 케이스
    }
    
    @Test
    void testInvalidEncFile() {
        // 잘못된 enc 파일 검증 실패 케이스
    }
    
    @Test
    void testMissingJasyptPassword() {
        // JASYPT_PASSWORD가 키스토어에 없을 때
    }
}
```

### 5.3 단위 테스트
- SecureString 메모리 소거 검증
- 예외 타입별 처리 로직 검증
- ValidationResult 로직 검증

### 5.4 통합 테스트
- 실제 키스토어 파일을 사용한 전체 플로우 테스트
- p12encload 플래그별 시나리오 테스트
- enc 파일 검증 시나리오 테스트
- 재시도 로직 시나리오 테스트
- HealthIndicator 상태 검증

### 5.5 보안 테스트
- 메모리 덤프에서 비밀번호 검출 테스트
- 로그 파일에서 민감 정보 누출 검증
- enc 파일 복호화 테스트

## 6. 사용법 예시

### 6.1 기본 사용법 (p12encload=true)

```yaml
# application.yml
spring:
  application:
    name: encloader-demo
  jasypt:
    encryptor:
      password: ${JASYPT_PASSWORD:default-password}
      algorithm: PBEWithMD5AndDES

keystore:
  path: ${KEYSTORE_PATH:file:secrets/keystore.p12}
  password: ${KEYSTORE_PASSWORD:}
  validation:
    validateOnStartup: true
    validateEncFiles: true
    failOnEncValidationError: false
    requiredAliases: [JASYPT_PASSWORD, DB_PASSWORD]

# 암호화된 속성들
database:
  password: ENC(encrypted_db_password_here)
  
api:
  secret: ENC(encrypted_api_secret_here)

demo:
  config.enc: ENC(encrypted_demo_value_here)
```

```bash
# 실행 명령어
java -Dp12encload=true \
     -Dkeystore.path=file:secrets/keystore.p12 \
     -Dkeystore.password="your-keystore-password" \
     -jar your-app.jar
```

### 6.2 기능 비활성화 (p12encload=false 또는 미설정)

```bash
# p12encload를 false로 설정하면 모든 keystore 기능 비활성화
java -Dp12encload=false -jar your-app.jar

# 또는 p12encload를 설정하지 않으면 기본값 false로 동작
java -jar your-app.jar
```

### 6.3 환경별 설정 예시

```yaml
# application-dev.yml
p12encload: false  # 개발환경에서는 비활성화

# application-test.yml  
p12encload: true
keystore:
  path: classpath:test-keystore.p12
  password: test-password
  validation:
    validateEncFiles: true
    failOnEncValidationError: true

# application-prod.yml
p12encload: true
keystore:
  path: ${PROD_KEYSTORE_PATH}
  password: ${PROD_KEYSTORE_PASSWORD}
  validation:
    validateOnStartup: true
    validateEncFiles: true
    failOnEncValidationError: true
    requiredAliases: [JASYPT_PASSWORD, DB_PASSWORD, API_KEY]
```

## 7. 배포 고려사항

### 7.1 하위 호환성
- 기존 설정 방식은 유지하되, p12encload 플래그로 선택적 활성화
- `p12encload` 기본값은 `false`로 설정하여 기존 환경에 영향 없음
- 새로운 검증 기능은 opt-in 방식으로 제공

### 7.2 성능 영향
- SecureString 사용으로 인한 GC 영향 최소화
- 재시도 로직의 타임아웃 설정 조정 가능
- enc 파일 검증은 p12encload=true일 때만 수행

### 7.3 윈도우 환경 최적화
```batch
@echo off
REM 윈도우 배치 스크립트 예시
set P12ENCLOAD=true
set KEYSTORE_PATH=file:C:\app\secrets\keystore.p12
set KEYSTORE_PASSWORD=your-secure-password

java -Dp12encload=%P12ENCLOAD% ^
     -Dkeystore.path=%KEYSTORE_PATH% ^
     -Dkeystore.password=%KEYSTORE_PASSWORD% ^
     -jar your-app.jar
```

### 7.4 모니터링 설정
```yaml
# application-prod.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  health:
    keystore:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
      environment: production

logging:
  level:
    com.example.keystore: INFO
    com.example.keystore.security: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

## 8. 예상 효과

### 8.1 선택적 기능 활성화
- p12encload 플래그를 통한 환경별 맞춤 설정
- 불필요한 환경에서는 성능 오버헤드 제거
- 단계적 도입 및 롤백 용이성

### 8.2 보안 개선
- 메모리에서 민감 정보 노출 시간 최소화
- 로그를 통한 정보 누출 방지
- enc 파일 유효성 사전 검증으로 런타임 오류 방지

### 8.3 안정성 향상
- 일시적 오류에 대한 복원력 제공
- 명확한 에러 메시지로 문제 해결 시간 단축
- 시작 시점에서 설정 오류 조기 발견

### 8.4 운영성 개선
- 실시간 상태 모니터링 가능
- 성능 메트릭을 통한 최적화 포인트 식별
- 장애 상황에서 빠른 원인 파악

## 9. 마이그레이션 가이드

### 9.1 기존 환경에서 새 버전 적용

**Step 1: 라이브러리 업데이트**
```gradle
// build.gradle
implementation 'com.example:keystore-property-source-spring-boot-starter:2.0.0'
implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

**Step 2: 설정 검토**
```yaml
# 기존 설정 (그대로 유지됨)
keystore:
  path: file:secrets/keystore.p12
  password: ${KEYSTORE_PASSWORD}

# 새로운 설정 (선택사항)
p12encload: true  # 기능 활성화
keystore:
  validation:
    validateEncFiles: true
    requiredAliases: [JASYPT_PASSWORD]
```

**Step 3: 점진적 활성화**
1. 개발환경: `p12encload: false` (기존과 동일)
2. 테스트환경: `p12encload: true` (새 기능 테스트)
3. 운영환경: `p12encload: true` (검증 후 적용)

### 9.2 문제 해결 가이드

**문제 1: p12encload=true인데 키스토어가 로딩되지 않음**
```bash
# 해결책: 로그 레벨을 DEBUG로 설정하여 원인 파악
-Dlogging.level.com.example.keystore=DEBUG
```

**문제 2: enc 파일 검증 실패**
```yaml
# 해결책: 검증 오류 시 실패하지 않도록 설정
keystore:
  validation:
    failOnEncValidationError: false
```

**문제 3: 성능 저하**
```yaml
# 해결책: 불필요한 검증 비활성화
keystore:
  validation:
    validateEncFiles: false
    validateOnStartup: false
```

## 10. 운영 체크리스트

### 10.1 배포 전 점검
- [ ] p12encload 플래그 설정 확인
- [ ] 키스토어 파일 존재 및 권한 확인 (Windows: NTFS 권한)
- [ ] KEYSTORE_PASSWORD 환경변수 설정 확인
- [ ] 필수 alias들이 키스토어에 존재하는지 확인
- [ ] enc 파일들이 올바른 형식인지 확인
- [ ] 테스트 환경에서 전체 플로우 검증

### 10.2 모니터링 설정
- [ ] /actuator/health/keystore 엔드포인트 확인
- [ ] 키스토어 로딩 실패 알람 설정
- [ ] enc 파일 검증 실패 모니터링
- [ ] 성능 메트릭 대시보드 구성

### 10.3 보안 점검
- [ ] 키스토어 파일 권한 설정 (Windows: 관리자/애플리케이션 계정만 읽기)
- [ ] 비밀번호 환경변수 암호화 확인
- [ ] 로그에서 민감 정보 노출 여부 검토
- [ ] 메모리 덤프 보안 검토

### 10.4 백업 및 복구
- [ ] 키스토어 파일 백업 전략 수립
- [ ] 장애 시 대체 인증 방안 준비
- [ ] 키 순환(rotation) 절차 문서화
- [ ] 복구 테스트 수행

## 11. FAQ

### Q1: p12encload 플래그를 설정하지 않으면 어떻게 동작하나요?
**A:** 기본값이 `false`이므로 모든 keystore 관련 기능이 비활성화됩니다. 기존 애플리케이션과 동일하게 동작합니다.

### Q2: enc 파일 검증에서 실패하면 애플리케이션이 시작되지 않나요?
**A:** `keystore.validation.failOnEncValidationError=false`(기본값)로 설정되어 있으면 경고 로그만 출력하고 애플리케이션은 정상 시작됩니다.

### Q3: 윈도우 환경에서 파일 경로 설정 시 주의사항이 있나요?
**A:** `file:C:/app/secrets/keystore.p12` 또는 `file:C:\\app\\secrets\\keystore.p12` 형식을 사용하세요. 상대 경로도 지원됩니다.

### Q4: 개발 환경에서는 keystore 없이 사용하고 싶어요.
**A:** `p12encload=false`로 설정하거나 아예 설정하지 않으면 됩니다. 이 경우 keystore 관련 기능이 모두 비활성화됩니다.

### Q5: 키스토어에 새로운 키를 추가하려면 어떻게 해야 하나요?
**A:** `KeystoreCreator.java`를 사용하여 새 키스토어를 생성하거나, 기존 키스토어에 키를 추가하는 유틸리티를 개발해야 합니다.

## 12. 참고 자료

- [Spring Boot Reference Documentation - Externalized Configuration](https://spring.io/projects/spring-boot)
- [Spring Boot Actuator Reference Guide](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Jasypt Spring Boot Integration](https://github.com/ulisesbocchio/jasypt-spring-boot)
- [Java KeyStore (PKCS#12) Documentation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html)

---

## 변경 이력

| 버전 | 날짜 | 변경 내용 |
|------|------|-----------|
| 1.0 | 2025-06-26 | 초기 리팩토링 플랜 작성 |
| 2.0 | 2025-06-26 | p12encload 플래그 지원 추가, enc 파일 검증 기능 추가 |

---

**문서 작성자:** BOAT-AI  
**최종 수정일:** 2025년 6월 26일  
**문서 상태:** 승인 대기