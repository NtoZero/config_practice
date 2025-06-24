# JASYPT + PKCS#12 키스토어 구현 플랜 (멀티모듈 프로젝트)

## 📋 구현 개요
기존 멀티모듈 Spring Boot 프로젝트(`encrypt-project`)에서 PKCS#12 키스토어를 사용한 JASYPT 설정과 MySQL 연동을 구현합니다.

## 🎯 구현 목표
1. **encrypt-file 모듈 개선** - PKCS#12 키스토어 기반 JASYPT 설정 강화
2. **MySQL DB 연동** - 암호화된 설정으로 데이터베이스 연결
3. **멀티모듈 환경 최적화** - 각 모듈별 역할 분리 및 설정 관리

## 📁 현재 프로젝트 구조 분석
```
encrypt-project/                    # 루트 프로젝트
├── encrypt-core/                   # 공통 핵심 모듈
│   ├── build.gradle                # Spring Boot 3.5.3, Lombok
│   └── src/main/java/
├── encrypt-configure/              # Spring Cloud Config Server 모듈
│   ├── build.gradle                # Spring Boot 3.3.1, Config Server
│   └── src/main/java/
├── encrypt-file/                   # JASYPT 암호화 모듈 (기존)
│   ├── build.gradle                # Spring Boot 3.1.2, JASYPT 3.0.5
│   ├── src/main/
│   │   ├── java/demo/encryptfile/
│   │   │   └── EncryptFileApplication.java
│   │   └── resources/
│   │       ├── application.yml     # 기존 JASYPT 설정
│   │       └── keystore.p12        # 기존 키스토어 파일
│   └── .env                        # 환경변수 파일
├── docs/                           # 문서 디렉터리
│   └── file-encrypt/PKCS#12/       # 기존 플레이북
├── config-file/                    # 설정 파일 디렉터리
└── settings.gradle                 # 멀티모듈 설정
```

## 🔄 개선할 모듈별 구조
```
encrypt-project/
├── encrypt-core/                   # 공통 라이브러리 모듈
│   ├── src/main/java/demo/core/
│   │   ├── config/
│   │   │   └── JasyptConfig.java   # 공통 JASYPT 설정
│   │   ├── entity/
│   │   │   └── BaseEntity.java     # 공통 엔티티
│   │   └── util/
│   │       └── EncryptUtil.java    # 암호화 유틸리티
│   └── build.gradle                # 공통 의존성 정의
├── encrypt-file/                   # JASYPT + DB 연동 모듈 (개선)
│   ├── src/main/
│   │   ├── java/demo/encryptfile/
│   │   │   ├── EncryptFileApplication.java
│   │   │   ├── config/
│   │   │   │   ├── DatabaseConfig.java
│   │   │   │   └── JasyptKeyStoreConfig.java
│   │   │   ├── entity/
│   │   │   │   └── User.java
│   │   │   ├── repository/
│   │   │   │   └── UserRepository.java
│   │   │   └── controller/
│   │   │       ├── UserController.java
│   │   │       └── HealthController.java
│   │   └── resources/
│   │       ├── application.yml           # 공통 설정
│   │       ├── application-local.yml     # 로컬 개발 설정
│   │       ├── application-prod.yml      # 운영 환경 설정
│   │       └── schema.sql               # DB 스키마
│   ├── secrets/                    # 키스토어 관리 (gitignore)
│   │   ├── keystore.p12           # PKCS#12 키스토어
│   │   ├── .keystore_pass         # 키스토어 비밀번호
│   │   └── create-keystore.sh     # 키스토어 생성 스크립트
│   └── build.gradle               # JASYPT, MySQL, JPA 의존성
└── encrypt-configure/             # Config Server 모듈 (기존 유지)
    └── src/main/resources/
        └── application.yml        # Config Server 설정
```

## 🔧 구현 단계 (멀티모듈 기반)

### 1단계: encrypt-core 모듈 개선
- 공통 JASYPT 설정 클래스 추가
- 암호화 유틸리티 클래스 구현
- 공통 엔티티 및 예외 처리 클래스

### 2단계: encrypt-file 모듈 확장
- **기존 JASYPT 설정 개선** - PKCS#12 키스토어 기반으로 업그레이드
- **MySQL 연동 추가** - JPA, MySQL 드라이버 의존성 추가
- **REST API 엔드포인트** - 사용자 관리 및 헬스체크 API

### 3단계: 키스토어 및 보안 설정
- PKCS#12 키스토어 생성 스크립트 개선
- 환경별 설정 파일 분리 (local, dev, prod)
- 민감정보 암호화 및 검증

### 4단계: 테스트 및 검증
- 멀티모듈 환경에서 JASYPT 동작 확인
- MySQL 연결 테스트
- 암호화/복호화 기능 검증

## 🔄 기존 파일 개선 계획

### encrypt-file/build.gradle 업그레이드
- Spring Boot 버전 통일 (3.5.3)
- MySQL, JPA 의존성 추가
- JASYPT 버전 업그레이드 (3.1.1)
- encrypt-core 모듈 의존성 추가

### application.yml 구조 개선
- 환경별 설정 분리
- PKCS#12 키스토어 경로 설정
- MySQL 연결 설정 (암호화된 값)
- 로깅 레벨 조정

### 키스토어 관리 개선
- 기존 keystore.p12 백업
- 새로운 키스토어 생성 스크립트
- 비밀번호 파일 분리 관리

## 🛡️ 보안 고려사항
- 키스토어 파일과 비밀번호 파일의 권한 설정
- 환경별 설정 분리 (local, dev, prod)
- 비밀번호 환경변수 주입 방식

## 🚀 실행 가이드 (멀티모듈 환경)

### 1. 키스토어 생성 (encrypt-file 모듈)
```bash
cd encrypt-file

# 기존 키스토어 백업 (선택사항)
mkdir -p backup
cp src/main/resources/keystore.p12 backup/keystore_backup_$(date +%Y%m%d).p12

# 새 키스토어 생성
chmod +x secrets/create-keystore.sh
./secrets/create-keystore.sh
```

### 2. 환경변수 설정
```bash
# Linux/macOS (encrypt-file 디렉터리에서)
export JASYPT_STOREPASS=$(cat secrets/.keystore_pass)

# Windows PowerShell
$env:JASYPT_STOREPASS = Get-Content secrets\keystore_pass.txt

# .env 파일 업데이트 (encrypt-file/.env)
JASYPT_STOREPASS=<generated_password>
MYSQL_ROOT_PASSWORD=ChangeMeRoot!
```

### 3. MySQL 데이터베이스 준비
```sql
-- 데이터베이스 생성
CREATE DATABASE demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 테스트 테이블 생성 (선택사항 - JPA가 자동 생성)
USE demo;
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 4. 민감정보 암호화 도구 실행
```bash
# encrypt-file 모듈에서 실행
./gradlew bootRun --args='--jasypt.encryptor.password=$(cat secrets/.keystore_pass) --spring.profiles.active=local'

# 또는 JASYPT CLI 도구 사용
java -cp ~/.gradle/caches/modules-2/files-2.1/org.jasypt/jasypt/1.9.3/*/jasypt-1.9.3.jar \
org.jasypt.intf.cli.JasyptPBEStringEncryptionCLI \
algorithm=PBEWITHHMACSHA512ANDAES_256 \
keyObtentionIterations=100000 \
input="ChangeMeRoot!" \
password="$(cat secrets/.keystore_pass)"
```

### 5. 애플리케이션 실행 (루트에서)
```bash
# 전체 프로젝트 빌드
./gradlew build

# encrypt-file 모듈만 실행 (로컬 환경)
./gradlew :encrypt-file:bootRun --args='--spring.profiles.active=local'

# encrypt-configure (Config Server) 실행
./gradlew :encrypt-configure:bootRun

# 특정 프로파일로 실행
./gradlew :encrypt-file:bootRun --args='--spring.profiles.active=prod'
```

## 📊 테스트 엔드포인트 (encrypt-file 모듈)

### 사용자 관리 API
- `GET /api/users` - 모든 사용자 조회
- `POST /api/users` - 새 사용자 생성
  ```json
  {
    "username": "testuser",
    "email": "test@example.com"
  }
  ```
- `GET /api/users/{id}` - 특정 사용자 조회
- `PUT /api/users/{id}` - 사용자 정보 수정
- `DELETE /api/users/{id}` - 사용자 삭제

### 암호화 테스트 API
- `POST /api/encrypt` - 문자열 암호화
  ```json
  {
    "plainText": "test-password"
  }
  ```
- `POST /api/decrypt` - 문자열 복호화
  ```json
  {
    "encryptedText": "ENC(encrypted-value)"
  }
  ```

### 헬스체크 API
- `GET /actuator/health` - 애플리케이션 상태 확인
- `GET /api/health/db` - 데이터베이스 연결 상태 확인
- `GET /api/health/jasypt` - JASYPT 암호화 상태 확인

### 설정 확인 API
- `GET /api/config/encrypted` - 암호화된 설정 값들 확인 (마스킹)
- `GET /api/config/database` - 데이터베이스 설정 상태 확인

## 🔍 트러블슈팅 (멀티모듈 환경)

### 멀티모듈 관련 문제들

1. **모듈 간 의존성 문제**
   ```
   오류: Could not resolve all dependencies for configuration ':encrypt-file:compileClasspath'
   해결: settings.gradle과 각 모듈의 build.gradle 의존성 확인
   ```

2. **키스토어 경로 문제**
   ```
   오류: keystore.p12 파일을 찾을 수 없음
   해결: 각 모듈별 상대 경로 확인 및 절대 경로 사용 고려
   ```

3. **환경변수 주입 실패**
   ```
   오류: JASYPT_STOREPASS 환경변수 미설정
   해결: 각 모듈별 .env 파일 확인 및 IDE 환경변수 설정
   ```

### 기존 파일 관련 문제들

1. **키스토어 버전 호환성**
   ```
   오류: 기존 keystore.p12가 새 설정과 호환되지 않음
   해결: 기존 키스토어 백업 후 새로 생성
   ```

2. **JASYPT 버전 충돌**
   ```
   오류: jasypt-spring-boot-starter 버전 3.0.5와 새 설정 충돌
   해결: 3.1.1로 업그레이드 및 설정 호환성 확인
   ```

### 디버깅 팁
- `--debug` 플래그로 상세 로그 확인
- `application-debug.yml`에서 JASYPT 관련 로그 레벨 조정
- 키스토어 파일 권한 및 경로 확인

## 📝 주의사항 (멀티모듈 환경)

1. **보안**
   - 키스토어 파일과 비밀번호 파일을 절대 버전 관리에 포함하지 마세요
   - `.gitignore`에 `*/secrets/`, `**/.keystore_pass`, `**/*.p12` 추가 필수
   - 각 모듈별 독립적인 시크릿 관리 구조 유지

2. **멀티모듈 의존성**
   - encrypt-core의 공통 설정이 다른 모듈에 영향을 주지 않도록 주의
   - 각 모듈은 독립적으로 실행 가능하도록 구성
   - 공통 라이브러리는 encrypt-core에, 특화 기능은 각 모듈에 분리

3. **백업 및 복구**
   - 기존 encrypt-file/src/main/resources/keystore.p12 백업 필수
   - 새 키스토어로 기존 암호화 값들 재암호화 필요
   - 단계적 마이그레이션 계획 수립

4. **키 로테이션**
   - 멀티모듈 환경에서 키 변경 시 모든 관련 모듈 동시 업데이트
   - Config Server(encrypt-configure)와의 연동 고려
   - 무중단 배포를 위한 키 로테이션 전략 수립

## 🔄 기존 코드 마이그레이션 체크리스트

### ✅ 수정이 필요한 파일들
- [ ] `encrypt-file/build.gradle` - 의존성 업그레이드
- [ ] `encrypt-file/src/main/resources/application.yml` - 키스토어 경로 수정
- [ ] `encrypt-file/.env` - 환경변수 추가
- [ ] 기존 암호화된 값들 재암호화

### ✅ 새로 추가할 파일들
- [ ] `encrypt-core/src/main/java/demo/core/config/JasyptConfig.java`
- [ ] `encrypt-file/src/main/java/demo/encryptfile/config/DatabaseConfig.java`
- [ ] `encrypt-file/src/main/resources/application-local.yml`
- [ ] `encrypt-file/secrets/create-keystore.sh`

### ✅ 백업이 필요한 파일들
- [ ] `encrypt-file/src/main/resources/keystore.p12`
- [ ] `encrypt-file/src/main/resources/application.yml`
- [ ] `encrypt-file/.env`

## 📚 참고 자료

- [Spring Boot Multi-Module Projects](https://spring.io/guides/gs/multi-module/)
- [Spring Boot JASYPT Documentation](https://github.com/ulisesbocchio/jasypt-spring-boot)
- [PKCS#12 Keystore Format](https://tools.ietf.org/html/rfc7292)
- [Spring Boot Security Best Practices](https://spring.io/guides/gs/securing-web/)
- [MySQL Connector/J Documentation](https://dev.mysql.com/doc/connector-j/8.0/en/)
- [Gradle Multi-Project Builds](https://docs.gradle.org/current/userguide/multi_project_builds.html)

---

**작성일**: 2025-06-24  
**작성자**: Claude AI Assistant  
**버전**: v2.0 (멀티모듈 대응)  
**프로젝트**: encrypt-project