# Spring Boot + Jasypt (.p12) 암호화 데모

이 프로젝트는 PKCS#12 KeyStore를 사용한 Jasypt 암호화/복호화 구현 예제입니다.

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
│   │   └── JasyptConfig.java          # Jasypt 설정
│   ├── service/
│   │   └── EncryptionTestService.java # 암호화 테스트 서비스
│   ├── util/
│   │   └── JasyptEncryptionUtil.java  # 암호화 유틸리티
│   └── EncryptFileApplication.java    # 메인 애플리케이션
├── src/main/resources/
│   ├── application.yml                # 애플리케이션 설정
│   └── keystore.p12                   # PKCS#12 키스토어 (생성 후)
├── create-keystore.sh                 # 키스토어 생성 스크립트 (Unix)
├── create-keystore.bat                # 키스토어 생성 스크립트 (Windows)
└── README.md
```

## 🔧 설정 설명

### application.yml
- Jasypt 키스토어 설정
- 암호화된 예제 프로퍼티
- 디버그 로깅 설정

### JasyptConfig.java
- PKCS#12 키스토어 연동 설정
- StringEncryptor 빈 구성

### EncryptionTestService.java
- 암호화된 설정값 주입 테스트
- 복호화된 값 출력

### JasyptEncryptionUtil.java
- 프로그래밍 방식 암호화/복호화
- ENC() 형식 처리

## 🔐 보안 주의사항

1. **키스토어 파일 보안**
   - `keystore.p12` 파일을 Git에 커밋하지 마세요
   - 프로덕션에서는 안전한 위치에 저장하세요

2. **환경 변수 관리**
   - 개발: `.env` 파일 또는 IDE 설정
   - 프로덕션: CI/CD 비밀 저장소 또는 AWS Secrets Manager 등

3. **비밀번호 정책**
   - 강력한 비밀번호 사용
   - 주기적인 비밀번호 변경
   - 접근 로그 모니터링

## 🧪 테스트

### 1. 기본 단위 테스트
키스토어 없이도 실행 가능한 테스트들:

```bash
./gradlew test
```

- Context Loading 테스트
- Bean 주입 테스트  
- 기본 암호화/복호화 로직 테스트

### 2. 데모 테스트 (키스토어 필요)
실제 키스토어를 사용한 통합 테스트:

```bash
# 환경 변수 설정 후
./gradlew test --tests JasyptDemoTest
```

- 실제 키스토어를 사용한 암호화/복호화
- 설정 파일의 암호화된 값 복호화
- 실용적인 사용 예제

## 📋 요구사항

- Java 17+
- Spring Boot 3.1.2
- Jasypt Spring Boot Starter 3.0.5

## 🛠️ 추가 기능

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
# 애플리케이션을 실행하여 JasyptEncryptionUtil을 사용하거나
# 별도의 CLI 도구를 구현할 수 있습니다
```

## 🔄 트러블슈팅

### 키스토어를 찾을 수 없는 경우
- `src/main/resources/keystore.p12` 파일 존재 확인
- 환경 변수 설정 확인

### 복호화 실패
- 환경 변수 값 확인
- 키스토어 비밀번호 확인
- 암호화된 값의 형식 확인 (ENC(...))

### 의존성 오류
- Java 17+ 사용 확인
- Gradle 빌드 상태 확인
