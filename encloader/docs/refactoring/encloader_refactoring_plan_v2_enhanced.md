# Encloader 운영환경 리팩토링 플랜 v2.0 (향상판)

> **작성일:** 2025년 6월 26일  
> **대상:** encloader keystore-property-source-spring-boot-starter  
> **목표:** 운영환경에서 안전하고 안정적인 사용을 위한 핵심 개선  
> **버전:** 2.0 Enhanced (KeyExtractor 보안 강화 및 Jasypt 의존성 최적화 포함)  
> **개발 환경:** Windows 환경 기준 최적화

## 1. 개요

현재 encloader는 개발/테스트 환경에서는 잘 동작하지만, 운영환경에서 사용하기 위해서는 보안, 안정성, 모니터링 측면에서 개선이 필요합니다. 

**v2.0 Enhanced 주요 변경사항:**
- `p12encload` 플래그를 통한 선택적 enc 파일 검증 기능 추가
- 플래그가 `true`일 때만 키스토어 로딩 및 검증 수행
- 플래그가 `false`이거나 없으면 기능 비활성화
- **KeyExtractor 보안 강화**: 프로덕션 빌드에서 제외 방안 적용
- **Jasypt 의존성 최적화**: 선택적 의존성으로 변경하여 충돌 방지

본 플랜은 클라우드 환경 지원이나 복잡한 캐싱 로직은 제외하고, 핵심적이고 실용적인 개선사항에 집중합니다.

## 2. 현재 구조 분석

### 2.1 주요 클래스 역할
- **KeystoreEnvironmentPostProcessor**: Spring Boot 초기화 시점에 키스토어 로딩 및 PropertySource 등록
- **KeystorePropertySource**: PKCS#12 키스토어에서 비밀 값 읽기 및 UTF-8 문자열 복원
- **KeystoreCreator**: 개발/테스트용 키스토어 생성 (keytool 대체)
- **KeyExtractor**: 디버깅용 키 추출 유틸리티 ⚠️ **보안 검토 필요**

### 2.2 현재 한계점
- 항상 키스토어 로딩을 시도함 (선택적 비활성화 불가)
- 메모리에 평문 비밀번호 저장
- 단순한 에러 처리
- 설정 검증 부족
- 운영환경 모니터링 기능 없음
- **KeyExtractor가 프로덕션 환경에 노출되어 보안 위험 존재**
- **Jasypt 의존성이 강제적으로 포함되어 충돌 가능성 존재**

### 2.3 새로운 요구사항: p12encload 플래그
- `p12encload=true`: 키스토어 로딩 및 enc 파일 검증 활성화
- `p12encload=false` 또는 미설정: 모든 keystore 관련 기능 비활성화
- 이를 통해 필요한 환경에서만 선택적으로 기능 사용 가능

## 3. 보안 강화 계획

### 3.1 KeyExtractor 처리 방안 ⭐ **NEW**

**문제점**: `KeyExtractor`는 "디버깅용 키 추출 유틸리티"로 명시되어 있지만, 운영 환경에서는 보안상 위험할 수 있습니다.

**해결 방안**:

#### Option 1: 테스트 소스셋으로 이동 (권장)
```gradle
// build.gradle 수정
sourceSets {
    main {
        java {
            exclude '**/KeyExtractor.java'
        }
    }
    test {
        java {
            srcDirs = ['src/main/java', 'src/test/java']
            exclude 'src/main/java/**/KeyExtractor.java'
        }
    }
    debug {
        java {
            srcDirs = ['src/main/java']
            include '**/KeyExtractor.java'
        }
    }
}

// 디버그 전용 태스크 추가
task debugJar(type: Jar) {
    classifier = 'debug'
    from sourceSets.debug.output
    from sourceSets.main.output
}
```

### 3.2 Jasypt 의존성 최적화 ⭐ **NEW**

**문제점**: 현재 Jasypt 라이브러리가 필수 의존성으로 포함되어 있어, 이를 사용하지 않는 프로젝트에서도 강제로 포함됩니다.

**해결 방안**:

#### Option 1: Optional 의존성으로 변경 (권장)
```gradle
// build.gradle
dependencies {
    // 기존
    // implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
    
    // 변경 - optional로 선언
    compileOnly 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
    testImplementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
}
```

#### Option 3: 모듈 분리
```
encloader/
├── encloader-core/               # 핵심 keystore 기능
│   └── KeystorePropertySource
├── encloader-jasypt/            # Jasypt 연동 모듈
│   ├── EncFileValidator
│   └── JasyptAutoConfiguration
└── encloader-spring-boot-starter/ # 통합 스타터
    └── 두 모듈을 조건부로 포함
```

```gradle
// encloader-core/build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    // Jasypt 의존성 없음
}

// encloader-jasypt/build.gradle
dependencies {
    implementation project(':encloader-core')
    implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
}

// encloader-spring-boot-starter/build.gradle
dependencies {
    implementation project(':encloader-core')
    compileOnly project(':encloader-jasypt')
}
```

## 4. 리팩토링 계획

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

#### 0.2 EncFile 검증 컴포넌트 (Jasypt Optional)

**목표**: p12encload=true일 때 enc 파일의 복호화 가능성 검증 (Jasypt 선택적 사용)

**구현 계획**:

```java
// 새 클래스: EncFileValidator (Jasypt 선택적 지원)
@Component
@ConditionalOnProperty(name = "keystore.validation.validateEncFiles", havingValue = "true")
public class EncFileValidator {
    
    private final KeystorePropertySource keystorePropertySource;
    private final SecurityLogger securityLogger;
    private final boolean jasyptAvailable;
    
    public EncFileValidator(KeystorePropertySource keystorePropertySource) {
        this.keystorePropertySource = keystorePropertySource;
        this.jasyptAvailable = isJasyptAvailable();
        this.securityLogger = new SecurityLogger();
        
        if (!jasyptAvailable) {
            securityLogger.logEncValidation("Jasypt library not available. Using format-only validation.");
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
        
        // 2. 각 enc 속성에 대해 검증 수행
        for (String encProperty : encProperties) {
            if (jasyptAvailable) {
                validateSingleEncPropertyWithJasypt(encProperty, env, result);
            } else {
                validateSingleEncPropertyFormatOnly(encProperty, env, result);
            }
        }
        
        return result;
    }
    
    private List<String> findEncProperties(ConfigurableEnvironment env) {
        List<String> encProperties = new ArrayList<>();
        
        for (PropertySource<?> propertySource : env.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource) {
                EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) propertySource;
                for (String propertyName : enumerable.getPropertyNames()) {
                    String value = env.getProperty(propertyName);
                    if (propertyName.endsWith(".enc") || isEncryptedValue(value)) {
                        encProperties.add(propertyName);
                    }
                }
            }
        }
        
        return encProperties;
    }
    
    private void validateSingleEncPropertyFormatOnly(String propertyName, ConfigurableEnvironment env, ValidationResult result) {
        try {
            String encValue = env.getProperty(propertyName);
            if (encValue == null) {
                result.addError("Enc property " + propertyName + " has null value");
                return;
            }
            
            if (isValidEncryptedFormat(encValue)) {
                result.addSuccess("Format validation passed for property: " + propertyName);
                securityLogger.logEncValidation("Format validated: " + propertyName);
            } else {
                result.addError("Invalid encrypted format for property: " + propertyName);
            }
            
        } catch (Exception e) {
            result.addError("Failed to validate enc property " + propertyName + ": " + e.getMessage());
        }
    }
    
    private void validateSingleEncPropertyWithJasypt(String propertyName, ConfigurableEnvironment env, ValidationResult result) {
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
            // Jasypt 사용 가능한 경우에만 실행
            Object encryptor = createEncryptor(password);
            String encryptedValue = encValue.substring(4, encValue.length() - 1); // ENC(...) 제거
            String decrypted = decrypt(encryptor, encryptedValue);
            
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
    
    private Object createEncryptor(String password) throws Exception {
        Class<?> encryptorClass = Class.forName("org.jasypt.encryption.pbe.StandardPBEStringEncryptor");
        Object encryptor = encryptorClass.getDeclaredConstructor().newInstance();
        
        Method setPassword = encryptorClass.getMethod("setPassword", String.class);
        setPassword.invoke(encryptor, password);
        
        Method setAlgorithm = encryptorClass.getMethod("setAlgorithm", String.class);
        setAlgorithm.invoke(encryptor, "PBEWithMD5AndDES");
        
        return encryptor;
    }
    
    private String decrypt(Object encryptor, String encryptedValue) throws Exception {
        Method decryptMethod = encryptor.getClass().getMethod("decrypt", String.class);
        return (String) decryptMethod.invoke(encryptor, encryptedValue);
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
    
    @Valid
    private Debug debug = new Debug();
    
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
        private boolean requireJasyptForEncValidation = false; // Jasypt 없어도 허용
    }
    
    @Data
    public static class Debug {
        private boolean enabled = false;
        private boolean allowInProduction = false; // 프로덕션에서 디버그 기능 허용 여부
        private Set<String> allowedProfiles = Set.of("dev", "test", "local");
    }
    
    public boolean isEnabled(Environment env) {
        String p12encloadFlag = env.getProperty("p12encload", "false");
        return Boolean.parseBoolean(p12encloadFlag);
    }
    
    public boolean isDebugAllowed(Environment env) {
        if (!debug.enabled) {
            return false;
        }
        
        String[] activeProfiles = env.getActiveProfiles();
        
        // 프로덕션 환경 체크
        boolean isProduction = Arrays.stream(activeProfiles)
            .anyMatch(profile -> profile.contains("prod"));
            
        if (isProduction && !debug.allowInProduction) {
            return false;
        }
        
        // 허용된 프로파일 체크
        return Arrays.stream(activeProfiles)
            .anyMatch(profile -> debug.allowedProfiles.contains(profile));
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
    
    public void logDebugFeatureBlocked(String feature, String reason) {
        log.warn("Debug feature '{}' blocked: {}", feature, reason);
    }
    
    private String maskLocation(String location) {
        if (location == null) return "null";
        
        // Windows 경로 마스킹: file:C:/secrets/keystore.p12 -> file:C:***/keystore.p12
        if (location.contains(":\\")) {
            return location.replaceAll("([C-Z]:\\\\[^\\\\]+\\\\)[^\\\\]*\\\\", "$1***/");
        }
        
        // Unix 경로 마스킹: file:/app/secrets/keystore.p12 -> file:***/keystore.p12
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

// KeyExtractor 보안 예외
public class DebugFeatureBlockedException extends KeystoreException {
    public DebugFeatureBlockedException(String feature, String reason) {
        super("Debug feature '" + feature + "' is blocked: " + reason, "debug", null);
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
                        sleepWithBackoff(RETRY_DELAY_MS * attempt);
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
    
    private void sleepWithBackoff(long millis) {
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
                .withDetail("jasypt.available", isJasyptAvailable())
                .withDetail("debug.enabled", properties.isDebugAllowed(environment))
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .withDetail("p12encload.enabled", true)
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
    
    private String maskSensitiveInfo(String path) {
        if (path == null) return null;
        // Windows 경로 고려: C:\app\secrets\keystore.p12 -> C:\***/keystore.p12
        return path.replaceAll("(password=)[^&]*", "$1***")
                   .replaceAll("([C-Z]:\\\\[^\\\\]+\\\\)[^\\\\]*\\\\", "$1***/");
    }
}
```

**필요한 의존성**:
```gradle
// build.gradle에 추가
implementation 'org.springframework.boot:spring-boot-starter-actuator'

// Jasypt 의존성을 optional로 변경
compileOnly 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
testImplementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
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
    private final Counter debugBlockedCounter;
    
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
        this.debugBlockedCounter = Counter.builder("keystore.debug.blocked")
            .description("Blocked debug feature attempts")
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
    
    public void recordDebugBlocked(String feature, String reason) {
        debugBlockedCounter.increment(Tags.of("feature", feature, "reason", reason));
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

## 6. Jasypt 의존성 최적화 구현

### 6.1 선택적 의존성 설정

**build.gradle 수정**:
```gradle
dependencies {
    // 기존 필수 의존성들
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.springframework.boot:spring-boot-autoconfigure'
    implementation 'org.springframework.boot:spring-boot-configuration-processor'
    
    // Jasypt를 optional로 변경
    compileOnly 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
    
    // 테스트에서는 포함
    testImplementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    
    // Actuator는 선택적 포함
    compileOnly 'org.springframework.boot:spring-boot-starter-actuator'
    compileOnly 'io.micrometer:micrometer-core'
}
```

### 6.3 Format-Only Validator

**새 파일**: `FormatOnlyEncFileValidator.java`
```java
@Component
public class FormatOnlyEncFileValidator extends EncFileValidator {
    
    private static final Logger log = LoggerFactory.getLogger(FormatOnlyEncFileValidator.class);
    
    public FormatOnlyEncFileValidator(KeystorePropertySource keystorePropertySource, 
                                    SecurityLogger securityLogger) {
        super(keystorePropertySource, securityLogger);
        log.info("Format-only enc file validator initialized (Jasypt not available)");
    }
    
    @Override
    public ValidationResult validateEncFiles(ConfigurableEnvironment env) {
        ValidationResult result = new ValidationResult();
        
        List<String> encProperties = findEncProperties(env);
        
        if (encProperties.isEmpty()) {
            getSecurityLogger().logEncValidation("No .enc properties found");
            return result;
        }
        
        log.info("Validating {} enc properties (format-only mode)", encProperties.size());
        
        for (String encProperty : encProperties) {
            validateFormatOnly(encProperty, env, result);
        }
        
        result.addWarning("Jasypt library not available - performed format-only validation");
        return result;
    }
    
    private void validateFormatOnly(String propertyName, ConfigurableEnvironment env, ValidationResult result) {
        try {
            String encValue = env.getProperty(propertyName);
            if (encValue == null) {
                result.addError("Enc property " + propertyName + " has null value");
                return;
            }
            
            if (isValidEncryptedFormat(encValue)) {
                result.addSuccess("Format validation passed for property: " + propertyName);
                getSecurityLogger().logEncValidation("Format validated: " + propertyName);
            } else {
                result.addError("Invalid encrypted format for property: " + propertyName + 
                              " (expected ENC(base64) format)");
            }
            
        } catch (Exception e) {
            result.addError("Failed to validate enc property " + propertyName + ": " + e.getMessage());
        }
    }
    
    private boolean isValidEncryptedFormat(String encValue) {
        // ENC(base64string) 형식 검증
        if (!encValue.startsWith("ENC(") || !encValue.endsWith(")")) {
            return false;
        }
        
        String encryptedContent = encValue.substring(4, encValue.length() - 1);
        
        // Base64 형식 검증
        try {
            Base64.getDecoder().decode(encryptedContent);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
```

## 7. 구현 순서 (Updated)

### Week 1: 보안 강화 및 플래그 지원 (최우선)
1. **KeyExtractor 보안 강화**
   - 프로덕션 환경 차단 로직 구현
   - 빌드 시 제외 설정 추가
   - 환경별 조건부 활성화
2. **Jasypt 의존성 최적화**
   - Optional 의존성으로 변경
   - 조건부 Auto Configuration 구현
   - Format-only validator 구현
3. **p12encload 플래그 지원**
   - KeystoreEnvironmentPostProcessor에 플래그 확인 로직 추가
   - 플래그별 동작 테스트 작성

### Week 2: 검증 및 로깅 강화
1. EncFileValidator 구현 (Jasypt 선택적 지원)
2. SecurityLogger 구현
3. KeystoreProperties 확장 (디버그 설정 포함)
4. 단위 테스트 작성

### Week 3: 에러 처리 개선
1. 예외 계층 구조 구현 (DebugFeatureBlockedException 포함)
2. KeystoreLoader 재시도 로직 구현
3. SecureString 메모리 보안 적용
4. 통합 테스트 작성

### Week 4: 모니터링 및 최종 검증
1. KeystoreValidator 구현
2. KeystoreHealthIndicator 구현 (Jasypt 상태 포함)
3. KeystoreMetrics 확장 (디버그 차단 메트릭 포함)
4. 전체 통합 테스트 및 보안 검증

## 8. 테스트 전략 (Enhanced)

### 8.1 보안 테스트
```java
@TestMethodOrder(OrderAnnotation.class)
class SecurityEnhancementTest {
    
    @Test
    void testWithoutJasypt() {
        // Jasypt 없이 애플리케이션 시작 확인
        // Format-only validation 동작 확인
    }
    
    @Test
    void testWithJasypt() {
        // Jasypt 포함시 전체 validation 동작 확인
    }
    
    @Test
    void testHealthIndicatorWithoutJasypt() {
        // HealthIndicator가 Jasypt 없이도 정상 동작 확인
    }
}
```

### 8.4 윈도우 환경 테스트
```java
@TestMethodOrder(OrderAnnotation.class)
@EnabledOnOs(OS.WINDOWS)
class WindowsEnvironmentTest {
    
    @Test
    void testWindowsPathHandling() {
        // Windows 경로 형식 테스트: C:\app\secrets\keystore.p12
        String windowsPath = "file:C:\\app\\secrets\\keystore.p12";
        assertDoesNotThrow(() -> {
            KeystorePropertySource.from(windowsPath, "password");
        });
    }
    
    @Test
    void testWindowsPathMasking() {
        // Windows 경로 마스킹 테스트
        String windowsPath = "C:\\app\\secrets\\keystore.p12";
        String masked = securityLogger.maskLocation(windowsPath);
        assertTrue(masked.contains("***"));
        assertFalse(masked.contains("secrets"));
    }
    
    @Test
    void testNTFSPermissions() {
        // NTFS 권한 관련 에러 처리 테스트
    }
}
```

## 9. 사용법 예시 (Enhanced)

### 9.1 프로덕션 환경 설정

**application-prod.yml**:
```yaml
# 프로덕션 환경 설정
spring:
  profiles:
    active: production
  jasypt:
    encryptor:
      password: ${JASYPT_PASSWORD:}
      algorithm: PBEWithMD5AndDES

# p12encload 활성화
p12encload: true

keystore:
  path: ${PROD_KEYSTORE_PATH:file:C:/app/secrets/keystore.p12}
  password: ${PROD_KEYSTORE_PASSWORD:}
  validation:
    validateOnStartup: true
    validateEncFiles: true
    failOnEncValidationError: true
    requireJasyptForEncValidation: false  # Jasypt 없어도 허용
    requiredAliases: 
      - JASYPT_PASSWORD
      - DB_PASSWORD
      - API_SECRET
  debug:
    enabled: false  # 프로덕션에서 디버그 완전 비활성화
    allowInProduction: false

# 모니터링 설정
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
      environment: production
      security-level: high

# 로깅 설정
logging:
  level:
    com.example.keystore: WARN
    com.example.keystore.security: ERROR
    com.example.keystore.debug: OFF  # 디버그 로깅 완전 차단
```

**윈도우 배치 스크립트** (`start-prod.bat`):
```batch
@echo off
setlocal

REM 프로덕션 환경 변수 설정
set SPRING_PROFILES_ACTIVE=production
set P12ENCLOAD=true
set PROD_KEYSTORE_PATH=file:C:\app\secrets\keystore.p12
set PROD_KEYSTORE_PASSWORD=%KEYSTORE_PASSWORD%
set JASYPT_PASSWORD=%ENC_PASSWORD%

REM 보안 강화 옵션
set JAVA_OPTS=-Xms512m -Xmx2g
set JAVA_OPTS=%JAVA_OPTS% -Dspring.profiles.active=%SPRING_PROFILES_ACTIVE%
set JAVA_OPTS=%JAVA_OPTS% -Dp12encload=%P12ENCLOAD%
set JAVA_OPTS=%JAVA_OPTS% -Dkeystore.path=%PROD_KEYSTORE_PATH%
set JAVA_OPTS=%JAVA_OPTS% -Dkeystore.password=%PROD_KEYSTORE_PASSWORD%
set JAVA_OPTS=%JAVA_OPTS% -Dkeystore.debug.enabled=false

REM 프로덕션 JAR 실행 (KeyExtractor 제외된 버전)
java %JAVA_OPTS% -jar app-production.jar

endlocal
```

### 9.2 개발 환경 설정

**application-dev.yml**:
```yaml
# 개발 환경 설정
spring:
  profiles:
    active: dev

# p12encload 비활성화 (개발 시 필요한 경우만 활성화)
p12encload: false

keystore:
  path: classpath:dev-keystore.p12
  password: dev-password
  validation:
    validateOnStartup: false
    validateEncFiles: false
  debug:
    enabled: true  # 개발 환경에서는 디버그 허용
    allowInProduction: false
    allowedProfiles: [dev, test, local]

# 개발용 로깅
logging:
  level:
    com.example.keystore: DEBUG
    com.example.keystore.debug: DEBUG
```

**개발 스크립트** (`start-dev.bat`):
```batch
@echo off
setlocal

REM 개발 환경 설정
set SPRING_PROFILES_ACTIVE=dev
set P12ENCLOAD=false

REM 디버그 옵션 활성화
set JAVA_OPTS=-Xms256m -Xmx1g
set JAVA_OPTS=%JAVA_OPTS% -Dspring.profiles.active=%SPRING_PROFILES_ACTIVE%
set JAVA_OPTS=%JAVA_OPTS% -Dp12encload=%P12ENCLOAD%
set JAVA_OPTS=%JAVA_OPTS% -Dkeystore.debug.enabled=true

REM 개발 JAR 실행 (KeyExtractor 포함된 버전)
java %JAVA_OPTS% -jar app-debug.jar

endlocal
```

### 9.3 Jasypt 없이 사용하는 경우

**build.gradle** (소비자 프로젝트):
```gradle
dependencies {
    implementation 'com.example:encloader-spring-boot-starter:2.0.0'
    // Jasypt 의존성을 명시적으로 추가하지 않음
    
    // 다른 필요한 의존성들...
}
```

**application.yml**:
```yaml
p12encload: true

keystore:
  path: file:secrets/keystore.p12
  password: ${KEYSTORE_PASSWORD}
  validation:
    validateEncFiles: true
    failOnEncValidationError: false  # Jasypt 없어도 실패하지 않음
    requireJasyptForEncValidation: false

# 암호화된 속성들 (Jasypt 없이는 format-only 검증)
database:
  url: jdbc:mysql://localhost:3306/mydb
  username: user
  # 이 속성은 format-only로 검증됨
  password: ENC(OBXxASampleEncryptedValue123==)
```

### 9.4 단계적 마이그레이션 시나리오

**Phase 1: 기존 환경 유지**
```yaml
# 아무 설정도 변경하지 않으면 기존과 동일하게 동작
# p12encload 기본값이 false이므로 keystore 기능 비활성화
```

**Phase 2: 선택적 활성화**
```yaml
# 테스트 환경에서만 새 기능 활성화
spring:
  profiles: test
  
p12encload: true
keystore:
  validation:
    validateEncFiles: true
    failOnEncValidationError: false  # 실패해도 계속 진행
```

**Phase 3: 완전 활성화**
```yaml
# 모든 환경에서 활성화
p12encload: true
keystore:
  validation:
    validateEncFiles: true
    failOnEncValidationError: true  # 검증 실패시 시작 중단
```

## 10. 배포 고려사항 (Enhanced)

### 10.1 빌드 프로파일 전략

**윈도우 환경 빌드 스크립트** (`build-windows.bat`):
```batch
@echo off
setlocal

echo Building Encloader for Windows environment...

REM 프로덕션 빌드 (KeyExtractor 제외)
echo Building production JAR (secure)...
gradlew clean productionJar -Pproduction=true

REM 개발 빌드 (KeyExtractor 포함)
echo Building debug JAR (with debug features)...
gradlew clean debugJar

REM 빌드 결과 확인
echo.
echo Build completed:
dir /b build\libs\

echo.
echo Production JAR: app-production.jar (secure, no debug features)
echo Debug JAR: app-debug.jar (includes debug features)

endlocal
```

### 10.2 보안 검증 체크리스트

**프로덕션 배포 전 체크리스트**:
- [ ] **KeyExtractor 완전 제거 확인**
  - [ ] Production JAR에 KeyExtractor.class 파일 없음
  - [ ] 프로덕션 프로파일에서 debug 기능 차단됨
  - [ ] 메트릭에서 debug 차단 이벤트 모니터링 설정됨

- [ ] **Jasypt 의존성 최적화 확인**
  - [ ] Optional 의존성으로 설정됨
  - [ ] Jasypt 없이도 정상 동작 확인
  - [ ] Format-only validation 동작 확인

- [ ] **p12encload 플래그 설정**
  - [ ] 프로덕션: `p12encload=true`
  - [ ] 개발: `p12encload=false` (필요시에만 true)
  - [ ] 기본값 false로 안전장치 확인

- [ ] **윈도우 환경 최적화**
  - [ ] Windows 경로 형식 지원 확인
  - [ ] NTFS 권한 설정 가이드 준비
  - [ ] 배치 스크립트 동작 검증

### 10.3 모니터링 설정

**Grafana 대시보드 메트릭**:
```yaml
# Prometheus 메트릭 수집 설정
management:
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: encloader
      environment: ${SPRING_PROFILES_ACTIVE:unknown}
      
# 커스텀 메트릭들
# - keystore.load.success (성공한 키스토어 로딩)
# - keystore.load.failure (실패한 키스토어 로딩)
# - keystore.load.duration (키스토어 로딩 시간)
# - keystore.enc.validation (enc 파일 검증 결과)
# - keystore.debug.blocked (차단된 디버그 시도)
```

**알림 규칙**:
```yaml
# AlertManager 규칙 예시
groups:
  - name: encloader.rules
    rules:
      - alert: KeystoreLoadFailure
        expr: increase(keystore_load_failure_total[5m]) > 0
        annotations:
          summary: "Keystore loading failed"
          
      - alert: DebugFeatureAttempted
        expr: increase(keystore_debug_blocked_total[1m]) > 0
        annotations:
          summary: "Debug feature access attempted in production"
          
      - alert: EncValidationFailure
        expr: increase(keystore_enc_validation_total{result="error"}[5m]) > 3
        annotations:
          summary: "Multiple enc file validation failures"
```

## 11. 성능 최적화

### 11.1 메모리 사용량 최적화

**SecureString 개선**:
```java
public class SecureString implements AutoCloseable {
    private char[] data;
    private final Object lock = new Object();
    private volatile boolean cleared = false;
    private final int originalLength;
    
    public SecureString(String value) {
        if (value != null) {
            this.data = value.toCharArray();
            this.originalLength = data.length;
            
            // 원본 문자열 참조 제거를 위한 명시적 가비지 컬렉션 힌트
            value = null;
        } else {
            this.originalLength = 0;
        }
    }
    
    public String getAndClear() {
        synchronized (lock) {
            if (cleared || data == null) return null;
            String result = new String(data);
            clear();
            return result;
        }
    }
    
    public void clear() {
        synchronized (lock) {
            if (!cleared && data != null) {
                Arrays.fill(data, '\0');
                data = null;
                cleared = true;
            }
        }
    }
    
    @Override
    public void close() {
        clear();
    }
    
    public int getOriginalLength() {
        return originalLength;
    }
    
    @Override
    protected void finalize() throws Throwable {
        clear();
        super.finalize();
    }
}
```

### 11.2 로딩 성능 최적화

**병렬 검증 지원**:
```java
@Component
public class ParallelEncFileValidator extends EncFileValidator {
    
    private final ExecutorService validationExecutor;
    
    public ParallelEncFileValidator(KeystorePropertySource keystorePropertySource,
                                  SecurityLogger securityLogger) {
        super(keystorePropertySource, securityLogger);
        
        // CPU 코어 수에 따른 스레드 풀 크기 조정
        int coreCount = Runtime.getRuntime().availableProcessors();
        int threadPoolSize = Math.max(2, Math.min(coreCount, 8));
        
        this.validationExecutor = Executors.newFixedThreadPool(threadPoolSize,
            r -> {
                Thread t = new Thread(r, "enc-validator-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            });
    }
    
    @Override
    public ValidationResult validateEncFiles(ConfigurableEnvironment env) {
        List<String> encProperties = findEncProperties(env);
        
        if (encProperties.isEmpty()) {
            return new ValidationResult();
        }
        
        // 많은 수의 enc 속성이 있을 때만 병렬 처리
        if (encProperties.size() < 5) {
            return super.validateEncFiles(env);
        }
        
        return validateInParallel(encProperties, env);
    }
    
    private ValidationResult validateInParallel(List<String> encProperties, 
                                              ConfigurableEnvironment env) {
        ValidationResult result = new ValidationResult();
        
        List<CompletableFuture<ValidationResult>> futures = encProperties.stream()
            .map(property -> CompletableFuture.supplyAsync(() -> {
                ValidationResult singleResult = new ValidationResult();
                validateSingleEncProperty(property, env, singleResult);
                return singleResult;
            }, validationExecutor))
            .collect(Collectors.toList());
        
        // 모든 검증 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .join();
        
        // 결과 병합
        futures.forEach(future -> {
            try {
                ValidationResult singleResult = future.get();
                result.merge(singleResult);
            } catch (Exception e) {
                result.addError("Parallel validation error: " + e.getMessage());
            }
        });
        
        return result;
    }
    
    @PreDestroy
    public void shutdown() {
        if (validationExecutor != null && !validationExecutor.isShutdown()) {
            validationExecutor.shutdown();
            try {
                if (!validationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    validationExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                validationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

## 12. 문제 해결 가이드 (Enhanced)

### 12.1 KeyExtractor 관련 문제

**문제**: 프로덕션에서 KeyExtractor가 실행되어 보안 경고 발생
```
ERROR: Debug feature 'KeyExtractor' is blocked: Debug features disabled in production environment
```

**해결책**:
1. **즉시 조치**: 애플리케이션 재시작으로 차단 확인
2. **근본 원인**: 프로덕션 빌드에 디버그 코드 포함
3. **해결 방법**:
   ```batch
   # 프로덕션 전용 JAR 재빌드
   gradlew clean productionJar -Pproduction=true
   
   # 빌드 결과 확인
   jar tf app-production.jar | findstr KeyExtractor
   # 결과가 없어야 정상
   ```

### 12.2 Jasypt 의존성 문제

**문제**: Jasypt 없이 실행했는데 enc 파일 검증 실패
```
WARN: Jasypt library not available. Using format-only validation.
ERROR: Decryption failed for property database.password.enc
```

**해결책**:
1. **설정 조정**: 검증 실패시에도 계속 진행하도록 설정
   ```yaml
   keystore:
     validation:
       failOnEncValidationError: false
       requireJasyptForEncValidation: false
   ```

2. **Jasypt 추가**: 완전한 검증이 필요한 경우
   ```gradle
   implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
   ```

### 12.3 윈도우 경로 문제

**문제**: Windows 경로에서 키스토어 로딩 실패
```
ERROR: Keystore file not found: file:C:\app\secrets\keystore.p12
```

**해결책**:
1. **경로 형식 확인**:
   ```yaml
   # 올바른 형식들
   keystore:
     path: "file:C:/app/secrets/keystore.p12"      # 슬래시 사용
     path: "file:C:\\app\\secrets\\keystore.p12"   # 이스케이프된 백슬래시
   ```

2. **권한 확인**:
   ```batch
   # 파일 권한 확인
   icacls C:\app\secrets\keystore.p12
   
   # 필요시 권한 부여
   icacls C:\app\secrets\keystore.p12 /grant "IIS_IUSRS:R"
   ```

### 12.4 p12encload 플래그 문제

**문제**: p12encload=true인데 키스토어가 로딩되지 않음
```
INFO: Keystore loading skipped: p12encload flag is disabled
```

**해결책**:
1. **플래그 값 확인**:
   ```batch
   # 환경변수 확인
   echo %P12ENCLOAD%
   
   # JVM 옵션 확인
   jps -v | findstr p12encload
   ```

2. **설정 우선순위 확인**:
   ```yaml
   # application.yml에서 명시적 설정
   p12encload: true
   ```

## 13. 운영 체크리스트 (Enhanced)

### 13.1 배포 전 보안 점검

**KeyExtractor 제거 확인**:
- [ ] Production JAR에서 `KeyExtractor.class` 파일 부재 확인
- [ ] `jar tf app-production.jar | grep -i debug` 결과 없음
- [ ] 프로덕션 프로파일에서 debug 기능 차단 테스트 완료

**Jasypt 의존성 확인**:
- [ ] Optional 의존성으로 설정됨
- [ ] Jasypt 없이 애플리케이션 정상 시작 확인
- [ ] Format-only validation 동작 확인
- [ ] Health check에서 Jasypt 상태 올바르게 표시됨

**플래그 설정 확인**:
- [ ] 프로덕션: `p12encload=true` 설정됨
- [ ] 개발: `p12encload=false` (기본값) 확인
- [ ] 환경변수 및 JVM 옵션 설정 검증

### 13.2 모니터링 설정 점검

**메트릭 수집 확인**:
- [ ] `keystore.load.success` 메트릭 수집됨
- [ ] `keystore.debug.blocked` 메트릭 설정됨
- [ ] `keystore.enc.validation` 메트릭 동작함
- [ ] Grafana 대시보드 정상 표시

**알림 설정 확인**:
- [ ] 키스토어 로딩 실패 알림 설정
- [ ] 디버그 기능 시도 알림 설정
- [ ] Enc 파일 검증 실패 알림 설정

### 13.3 백업 및 복구

**키스토어 백업**:
- [ ] 키스토어 파일 정기 백업 스케줄 설정
- [ ] 백업 파일 암호화 적용
- [ ] 복구 절차 문서화 및 테스트 완료

**윈도우 환경 고려사항**:
- [ ] NTFS 권한 설정 문서화
- [ ] Windows 서비스 등록 가이드 준비
- [ ] 배치 스크립트 동작 검증

## 14. FAQ (Enhanced)

### Q1: KeyExtractor가 프로덕션에서 차단되는 이유는?
**A:** `KeyExtractor`는 디버깅 목적의 유틸리티로, 키스토어의 모든 비밀 정보를 평문으로 출력할 수 있어 보안상 위험합니다. 프로덕션 환경에서는 다음과 같이 차단됩니다:
- 프로덕션 프로파일에서 자동 비활성화
- 프로덕션 JAR에서 클래스 파일 제외
- 런타임에서 환경 검증 후 차단

### Q2: Jasypt 없이도 사용할 수 있나요?
**A:** 네, 가능합니다. Jasypt가 없으면:
- 기본 키스토어 기능은 정상 동작
- Enc 파일 검증은 format-only로 제한됨 (ENC(base64) 형식만 검증)
- 실제 복호화 테스트는 수행되지 않음
- Health check에서 Jasypt 상태가 표시됨

### Q3: 윈도우 환경에서 경로 설정 시 주의사항은?
**A:** 다음 형식들을 사용할 수 있습니다:
- `file:C:/app/secrets/keystore.p12` (슬래시 사용)
- `file:C:\\app\\secrets\\keystore.p12` (이스케이프된 백슬래시)
- `classpath:keystore.p12` (클래스패스 리소스)
- 상대 경로: `file:secrets/keystore.p12`

### Q4: p12encload 플래그를 언제 사용해야 하나요?
**A:** 다음과 같이 사용하세요:
- **프로덕션**: `p12encload=true` (보안이 필요한 환경)
- **개발**: `p12encload=false` (기본값, 필요시에만 true)
- **테스트**: 테스트 시나리오에 따라 선택적 설정

### Q5: 성능에 미치는 영향은?
**A:** 다음과 같은 최적화가 적용되어 있습니다:
- `p12encload=false`일 때 모든 키스토어 기능 비활성화로 오버헤드 없음
- SecureString으로 메모리 사용량 최적화
- 병렬 enc 파일 검증으로 처리 시간 단축
- 재시도 로직의 지능적 백오프로 불필요한 시도 방지

## 15. 참고 자료 (Enhanced)

### 15.1 보안 관련
- [OWASP Java Security Guidelines](https://owasp.org/www-project-top-ten/)
- [Spring Security Best Practices](https://spring.io/guides/topicals/spring-security-architecture/)
- [Java Cryptography Architecture Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html)

### 15.2 의존성 관리
- [Gradle Optional Dependencies](https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph)
- [Spring Boot Conditional Beans](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration.condition-annotations)
- [Maven Optional Dependencies](https://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html)

### 15.3 윈도우 환경
- [Windows File System Security](https://docs.microsoft.com/en-us/windows/security/threat-protection/security-policy-settings/file-system)
- [Java Path Handling on Windows](https://docs.oracle.com/javase/tutorial/essential/io/pathOps.html)
- [Windows Service Deployment](https://docs.spring.io/spring-boot/docs/current/reference/html/deployment.html#deployment.installing.windows)

---

## 변경 이력

| 버전 | 날짜 | 변경 내용 |
|------|------|-----------|
| 1.0 | 2025-06-26 | 초기 리팩토링 플랜 작성 |
| 2.0 | 2025-06-26 | p12encload 플래그 지원 추가, enc 파일 검증 기능 추가 |
| 2.0 Enhanced | 2025-06-26 | KeyExtractor 보안 강화, Jasypt 의존성 최적화, 윈도우 환경 고려사항 추가 |

---


### 8.2 플래그 기반 테스트


### 8.3 의존성 테스트
