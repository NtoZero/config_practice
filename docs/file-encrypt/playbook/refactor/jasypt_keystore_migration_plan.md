# 키스토어 개인키 기반 JASYPT 암호화 전환 플랜

## 🎯 목표
키스토어 비밀번호 대신 키스토어 내부의 개인키를 추출하여 JASYPT 암호화에 사용

## 📋 수정 플랜

### Phase 1: 핵심 인프라 구축

#### 1.1 KeyStoreService 생성 (새 파일)
**파일**: `src/main/java/demo/encryptfile/service/KeyStoreService.java`
- 키스토어 로딩 및 검증 로직
- 개인키 추출 메서드
- 개인키를 문자열로 변환하는 유틸리티

```java
@Service
@Slf4j
public class KeyStoreService {
    
    public String extractPrivateKeyAsPassword(String keystorePath, String keystorePassword, String alias) {
        // 키스토어에서 개인키 추출 로직
    }
    
    public void validateKeyStore(String keystorePath, String keystorePassword, String alias) {
        // 키스토어 검증 로직
    }
}
```

#### 1.2 JasyptConfig 전면 수정
**파일**: `src/main/java/demo/encryptfile/config/JasyptConfig.java`
- KeyStoreService 의존성 추가
- 개인키 기반 암호화 설정으로 변경
- 기존 키스토어 비밀번호 직접 사용 로직 제거

```java
@Configuration
@EnableEncryptableProperties
public class JasyptConfig {
    
    @Autowired
    private KeyStoreService keyStoreService;
    
    @Bean(name = "jasyptStringEncryptor")
    @Primary
    public StringEncryptor stringEncryptor() {
        // 개인키 추출하여 JASYPT 비밀번호로 사용
        String privateKeyPassword = keyStoreService.extractPrivateKeyAsPassword(...);
        config.setPassword(privateKeyPassword);
    }
}
```

### Phase 2: 설정 및 환경변수 변경

#### 2.1 application.yml 수정
**파일**: `src/main/resources/application.yml`

**현재 구조:**
```yaml
spring:
  jasypt:
    encryptor:
      key-store:
        password: ${JASYPT_STOREPASS}
```

**변경 후 구조:**
```yaml
spring:
  jasypt:
    encryptor:
      key-store:
        location: ${KEYSTORE_LOCATION:file:secrets/keystore.p12}
        password: ${KEYSTORE_PASSWORD}  # 키스토어 열기용만
        alias: jasypt-secret-key
```

#### 2.2 test 설정 파일 수정
**파일**: `src/test/resources/application-test.properties`

**현재:**
```properties
spring.jasypt.encryptor.key-store.password=MySecurePassword123!
```

**변경 후:**
```properties
spring.jasypt.encryptor.key-store.password=gw3MfHMEDu5KSrk7av92rIwsmQbFCR872OaHAdzBdVw=
```

### Phase 3: 테스트 코드 수정

#### 3.1 JasyptEncryptionTest 수정
**파일**: `src/test/java/demo/encryptfile/util/JasyptEncryptionTest.java`

**현재 문제:**
```java
@TestPropertySource(properties = {
    "spring.jasypt.encryptor.key-store.password=MySecurePassword123!",  // 하드코딩된 비밀번호
})
```

**해결 방안:**
```java
@TestPropertySource(properties = {
    "spring.jasypt.encryptor.key-store.password=gw3MfHMEDu5KSrk7av92rIwsmQbFCR872OaHAdzBdVw=",
})
```

#### 3.2 JasyptConfigTest 수정
**파일**: `src/test/java/demo/encryptfile/config/JasyptConfigTest.java`
- 새로운 KeyStoreService 테스트 추가
- 개인키 추출 로직 테스트

```java
@Test
void 키스토어에서_개인키_추출_테스트() {
    String privateKeyPassword = keyStoreService.extractPrivateKeyAsPassword(...);
    assertThat(privateKeyPassword).isNotNull();
    assertThat(privateKeyPassword).isNotEqualTo(keystorePassword);
}
```

### Phase 4: 유틸리티 및 문서 정리

#### 4.1 JasyptEncryptionUtil 검토/수정
**파일**: `src/main/java/demo/encryptfile/util/JasyptEncryptionUtil.java`
- 새로운 키 추출 방식과 호환성 확인
- 필요시 유틸리티 메서드 추가

#### 4.2 키스토어 생성 스크립트 업데이트
**파일**: `secrets/create-keystore.ps1`
- 새로운 환경변수 구조에 맞게 안내 메시지 수정

```powershell
Write-Host "🔧 다음 단계:" -ForegroundColor Cyan
Write-Host "1. `$env:KEYSTORE_PASSWORD = Get-Content keystore_pass.txt 명령으로 환경변수 설정"
Write-Host "2. JASYPT 암호화는 키스토어 내부 개인키를 자동으로 사용합니다"
```

#### 4.3 쉘 스크립트 생성/수정
**파일**: `secrets/create-keystore.sh` (Linux/Mac용)
- PowerShell 스크립트와 동일한 기능의 Bash 버전
- 크로스 플랫폼 지원을 위한 스크립트

```bash
#!/bin/bash
# Linux/Mac용 키스토어 생성 스크립트
echo "🔐 PKCS#12 키스토어 생성을 시작합니다..."

# 32바이트 난수 기반 STOREPASS 생성
STOREPASS=$(openssl rand -base64 32)

# 비밀번호를 안전한 파일에 저장
echo "$STOREPASS" > keystore_pass.txt
chmod 600 keystore_pass.txt

echo "🔧 다음 단계:"
echo "1. export KEYSTORE_PASSWORD=\$(cat keystore_pass.txt) 명령으로 환경변수 설정"
echo "2. JASYPT 암호화는 키스토어 내부 개인키를 자동으로 사용합니다"
```

## 🔄 작업 순서 (권장)

### 단계 1: 서비스 레이어 구축
1. ✅ **KeyStoreService.java** 생성
2. ✅ **JasyptConfig.java** 수정
3. ✅ 컴파일 오류 해결

### 단계 2: 설정 변경
4. ✅ **application.yml** 수정
5. ✅ **application-test.properties** 수정

### 단계 3: 테스트 수정
6. ✅ **JasyptEncryptionTest.java** 수정
7. ✅ **JasyptConfigTest.java** 수정
8. ✅ 테스트 실행 및 검증

### 단계 4: 최종 정리
9. ✅ **JasyptEncryptionUtil.java** 검토
10. ✅ **create-keystore.ps1** 업데이트 (Windows용)
11. ✅ **create-keystore.sh** 생성/업데이트 (Linux/Mac용)
12. ✅ 전체 테스트 및 문서화

## 🚨 주의사항

### 보안 관련
- **백업**: 기존 키스토어와 설정 파일 백업 필수
- **환경변수**: 새로운 환경변수 구조 적용 시 기존 값과 충돌 방지
- **키 노출**: 개발 과정에서 개인키가 로그에 노출되지 않도록 주의

### 호환성 관련
- **기존 데이터**: 기존에 암호화된 데이터와의 호환성 확인
- **테스트**: 각 단계마다 암호화/복호화 테스트 실행
- **롤백**: 문제 발생 시 즉시 롤백할 수 있도록 준비

### 개발 환경
- **IDE 캐시**: IntelliJ IDEA 캐시 클리어 (Build > Clean)
- **Gradle**: `./gradlew clean build` 실행하여 완전 재빌드
- **Spring Context**: 애플리케이션 컨텍스트 재시작

## 📝 예상 변경 파일 목록

### 수정 파일
```
├── src/main/java/demo/encryptfile/config/JasyptConfig.java
├── src/main/resources/application.yml
├── src/test/resources/application-test.properties
├── src/test/java/demo/encryptfile/util/JasyptEncryptionTest.java
├── src/test/java/demo/encryptfile/config/JasyptConfigTest.java
├── secrets/create-keystore.ps1
└── secrets/create-keystore.sh
```

### 신규 파일
```
├── src/main/java/demo/encryptfile/service/KeyStoreService.java
└── secrets/create-keystore.sh (Linux/Mac용 - 기존에 없다면)
```

### 검토 파일
```
└── src/main/java/demo/encryptfile/util/JasyptEncryptionUtil.java
```

## 🔧 환경변수 변경 사항

### 현재 환경변수
**Windows (PowerShell):**
```powershell
$env:JASYPT_STOREPASS = "gw3MfHMEDu5KSrk7av92rIwsmQbFCR872OaHAdzBdVw="
```

**Linux/Mac (Bash):**
```bash
export JASYPT_STOREPASS="gw3MfHMEDu5KSrk7av92rIwsmQbFCR872OaHAdzBdVw="
```

### 변경 후 환경변수
**Windows (PowerShell):**
```powershell
$env:KEYSTORE_PASSWORD = "gw3MfHMEDu5KSrk7av92rIwsmQbFCR872OaHAdzBdVw="
# JASYPT 암호화는 키스토어 내부 개인키 사용 (별도 환경변수 불필요)
```

**Linux/Mac (Bash):**
```bash
export KEYSTORE_PASSWORD="gw3MfHMEDu5KSrk7av92rIwsmQbFCR872OaHAdzBdVw="
# JASYPT 암호화는 키스토어 내부 개인키 사용 (별도 환경변수 불필요)
```

## 🎯 기대 효과

### 보안 강화
- 키스토어 비밀번호와 JASYPT 암호화 키 완전 분리
- 키스토어 비밀번호 노출되어도 암호화 키는 안전
- 실제 암호화 키가 환경변수로 노출되지 않음

### 운영 편의성
- 키 로테이션 시 유연성 증가
- 키스토어 비밀번호 변경해도 기존 암호화 데이터 영향 없음
- 더 강력한 키 기반 암호화 구조

### 아키텍처 개선
- 키 관리 로직의 명확한 분리
- 테스트 환경과 운영 환경의 일관된 키 관리
- PKCS#12 표준을 완전히 활용하는 구조

---

**작업 시작 전 체크리스트:**
- [ ] 현재 키스토어 파일 백업 완료
- [ ] 기존 암호화된 데이터 백업 완료
- [ ] 개발 환경 준비 (Java, Gradle, IDE)
- [ ] 테스트 계획 수립 완료