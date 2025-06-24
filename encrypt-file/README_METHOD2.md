# Spring Boot + Jasypt 암호화 데모 (방안 2: @EnableEncryptableProperties)

이 프로젝트는 `@EnableEncryptableProperties`를 사용한 Jasypt 암호화/복호화 구현 예제입니다.

## 🚀 빠른 시작 (방안 2: @EnableEncryptableProperties)

### 1. 환경 변수 설정

**Windows:**
```bash
set JASYPT_PASSWORD=mySecretPassword123!
```

**Linux/macOS:**
```bash
export JASYPT_PASSWORD=mySecretPassword123!
```

### 2. 테스트 실행

```bash
# 기본 테스트 실행
./gradlew test

# 데모 테스트 실행 (환경 변수 필요)
./gradlew test --tests JasyptDemoTest

# 애플리케이션 실행
./gradlew bootRun
```

## 📁 프로젝트 구조

```
encrypt-file/
├── src/main/java/demo/encryptfile/
│   ├── config/
│   │   └── JasyptConfig.java          # @EnableEncryptableProperties 설정
│   ├── service/
│   │   └── EncryptionTestService.java # 암호화 테스트 서비스
│   ├── util/
│   │   └── JasyptEncryptionUtil.java  # 암호화 유틸리티
│   └── EncryptFileApplication.java    # 메인 애플리케이션
├── src/main/resources/
│   └── application.yml                # 애플리케이션 설정
└── README_METHOD2.md
```

## 🔧 설정 설명 (방안 2)

### application.yml
- Jasypt 커스텀 빈 설정 (`jasyptStringEncryptor`)
- 암호화된 예제 프로퍼티
- 디버그 로깅 설정

### JasyptConfig.java
- `@EnableEncryptableProperties` 전역 암호화 활성화
- 커스텀 `PooledPBEStringEncryptor` 빈 구성
- 보안 알고리즘 및 파라미터 설정

### EncryptionTestService.java
- 암호화된 설정값 주입 테스트
- 복호화된 값 출력

### JasyptEncryptionUtil.java
- 프로그래밍 방식 암호화/복호화
- ENC() 형식 처리

## 🧪 테스트

### 1. 기본 단위 테스트
환경 변수 없이도 실행 가능한 테스트들:

```bash
./gradlew test
```

- Context Loading 테스트
- Bean 주입 테스트  
- 커스텀 Encryptor 테스트
- 암호화/복호화 로직 테스트

### 2. 데모 테스트 (환경 변수 필요)
실제 환경 변수를 사용한 통합 테스트:

```bash
# 환경 변수 설정 후
./gradlew test --tests JasyptDemoTest
```

- 커스텀 encryptor 빈 확인
- 실제 암호화/복호화
- 설정 파일의 암호화된 값 복호화
- Salt 랜덤화 데모

## 🔐 방안 2의 특징

### ✅ 장점
1. **명시적 제어**: Encryptor 설정을 완전히 제어 가능
2. **커스텀 알고리즘**: 원하는 암호화 알고리즘 선택 가능
3. **엔터프라이즈 환경**: 보안 요구사항이 높은 환경에 적합
4. **디버깅 용이**: 설정 문제 추적이 쉬움
5. **성능 최적화**: Pool 사이즈 등 성능 파라미터 조정 가능

### 📊 설정 가능한 파라미터
```java
config.setPassword(jasyptPassword);              // 암호화 비밀번호
config.setAlgorithm("PBEWithMD5AndDES");         // 암호화 알고리즘
config.setKeyObtentionIterations("1000");        // 키 생성 반복 횟수
config.setPoolSize("1");                         // 연결 풀 크기
config.setProviderName("SunJCE");                // 암호화 제공자
config.setSaltGeneratorClassName(               // Salt 생성기
    "org.jasypt.salt.RandomSaltGenerator");
config.setStringOutputType("base64");            // 출력 형식
```

## 📊 다른 방안과의 비교

| 항목 | 방안 1 (자동설정) | 방안 2 (@EnableEncryptableProperties) |
|------|------------------|----------------------------------------|
| 복잡도 | ⭐ 매우 간단 | ⭐⭐ 간단 |
| 설정 제어 | ⭐⭐ 제한적 | ⭐⭐⭐⭐ 완전 제어 |
| 보안 커스터마이징 | ⭐⭐ 기본 설정만 | ⭐⭐⭐⭐ 모든 파라미터 |
| 디버깅 | ⭐⭐ 어려움 | ⭐⭐⭐⭐ 매우 쉬움 |
| 성능 튜닝 | ⭐ 불가능 | ⭐⭐⭐⭐ 완전 가능 |
| 엔터프라이즈 적합성 | ⭐⭐ 보통 | ⭐⭐⭐⭐ 매우 높음 |
| 유지보수성 | ⭐⭐⭐ 높음 | ⭐⭐⭐ 높음 |

## 🔒 보안 고려사항

### 1. 환경 변수 보안
- **개발 환경**: IDE 환경 변수 설정
- **프로덕션**: CI/CD 비밀 저장소 또는 AWS Secrets Manager 등 활용

### 2. 알고리즘 선택
```java
// 기본 (호환성 우선)
config.setAlgorithm("PBEWithMD5AndDES");

// 강화된 보안 (JDK 9+ 또는 JCE Unlimited 필요)
config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
```

### 3. 성능 vs 보안 트레이드오프
```java
// 보안 강화 (느림)
config.setKeyObtentionIterations("10000");

// 성능 우선 (빠름)
config.setKeyObtentionIterations("1000");
```

## 🛠️ 실제 사용 예제

### 새로운 값 암호화하기

```java
@Autowired
private JasyptEncryptionUtil jasyptUtil;

// 암호화
String encrypted = jasyptUtil.encrypt("your-secret-value");
// 결과: ENC(암호화된값)

// application.yml에 설정
your:
  secret: ENC(암호화된값)
```

### CLI에서 값 암호화하기

```bash
# 테스트 실행으로 암호화 값 생성
export JASYPT_PASSWORD=yourPassword
./gradlew test --tests JasyptDemoTest
# 콘솔에서 암호화된 값들을 확인할 수 있습니다
```

## 🔄 트러블슈팅

### 환경 변수 설정 확인
```bash
# Windows
echo %JASYPT_PASSWORD%

# Linux/macOS  
echo $JASYPT_PASSWORD
```

### 복호화 실패
- 환경 변수 `JASYPT_PASSWORD` 값 확인
- 암호화 시 사용한 비밀번호와 동일한지 확인
- 암호화된 값의 형식 확인 (ENC(...))

### JCE Unlimited 관련 오류
JDK 8 사용 시 강화된 암호화 알고리즘 사용하려면:
1. JCE Unlimited Strength Jurisdiction Policy Files 설치
2. 또는 JDK 9+ 업그레이드

## 📋 요구사항

- Java 17+
- Spring Boot 3.1.2
- Jasypt Spring Boot Starter 3.0.5

## 🎯 언제 방안 2를 선택해야 할까?

### ✅ 방안 2를 선택하는 경우:
- **엔터프라이즈 환경**에서 보안 정책이 엄격한 경우
- **커스텀 암호화 알고리즘**이 필요한 경우
- **성능 최적화**가 중요한 경우 (Pool 사이즈 조정 등)
- **디버깅과 모니터링**이 중요한 경우
- **보안 감사**가 필요한 경우
- **여러 환경**에서 다른 암호화 설정이 필요한 경우

### ❌ 방안 1을 선택하는 경우:
- **간단한 프로젝트**나 **프로토타입**
- **빠른 개발**이 우선인 경우
- **기본 보안 수준**으로 충분한 경우

## 🚀 다음 단계

### 1. 프로덕션 배포 준비
```yaml
# application-prod.yml
spring:
  jasypt:
    encryptor:
      bean: jasyptStringEncryptor

database:
  url: ENC(실제운영DB암호화된URL)
  username: ENC(실제운영DB사용자명)
  password: ENC(실제운영DB비밀번호)

api:
  key: ENC(실제API키)
  secret: ENC(실제API시크릿)
```

### 2. CI/CD 파이프라인 설정
```yaml
# GitHub Actions 예제
env:
  JASYPT_PASSWORD: ${{ secrets.JASYPT_PASSWORD }}

steps:
  - name: Run Tests
    run: ./gradlew test
    env:
      JASYPT_PASSWORD: ${{ secrets.JASYPT_PASSWORD }}
```

### 3. 보안 강화 옵션
```java
// 고급 보안 설정 예제
@Configuration
@EnableEncryptableProperties
public class ProductionJasyptConfig {
    
    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        
        // 강화된 보안 설정
        config.setPassword(getPasswordFromSecureSource());
        config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        config.setKeyObtentionIterations("10000");
        config.setPoolSize("4");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
        config.setStringOutputType("base64");
        
        encryptor.setConfig(config);
        return encryptor;
    }
    
    private String getPasswordFromSecureSource() {
        // AWS Secrets Manager, HashiCorp Vault 등에서 비밀번호 조회
        return System.getenv("JASYPT_PASSWORD");
    }
}
```

## 📚 참고 자료

- [Jasypt Spring Boot GitHub](https://github.com/ulisesbocchio/jasypt-spring-boot)
- [Spring Boot Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Jasypt 공식 문서](http://www.jasypt.org/)
- [Java Cryptography Architecture](https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html)

---

**작성일**: 2025년 6월 24일  
**구현 방안**: 방안 2 (@EnableEncryptableProperties)  
**버전**: 1.0