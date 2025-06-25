# Keystore Property Source 리팩토링 계획

## 📋 문제 분석

### 현재 상태
- ✅ 스프링 애플리케이션은 정상 실행됨
- ❌ 키스토어에서 프로퍼티 값을 읽어오지 못함
- ❌ 테스트 7개 실패 (KeyExtractor 누락, 키스토어 구조 문제)
- ❌ 설계와 구현 간의 불일치

### 핵심 문제점
1. **키스토어 사용법의 오해**: AES 비밀키 ≠ 문자열 프로퍼티 값
2. **KeyExtractor 클래스 누락**: 테스트에서 참조하지만 실제 구현 없음
3. **잘못된 키 생성 방식**: `keytool -genseckey`는 랜덤 바이트 생성
4. **Base64 인코딩 문제**: 키 바이트를 인코딩해도 의미있는 문자열이 되지 않음

---

## 🎯 리팩토링 목표

### 주요 목표
- [x] 키스토어에서 실제 문자열 프로퍼티 값 저장/조회 가능
- [x] 모든 테스트 통과
- [x] Spring Property Placeholder 정상 동작
- [x] 보안성 확보 (암호화된 프로퍼티 저장)

### 기술적 목표
- [x] PKCS#12 키스토어 표준 준수
- [x] Spring Boot 3.x 호환성
- [x] 명확한 에러 처리 및 로깅

---

## 🔧 리팩토링 전략

### 전략 A: 키스토어 + 암호화 하이브리드 (권장)

**개념:**
- 키스토어에는 마스터 암호화 키 저장
- 실제 프로퍼티 값들은 별도 암호화 파일에 저장
- 키스토어의 마스터 키로 프로퍼티 파일 복호화

**장점:**
- ✅ 표준적인 키스토어 사용법
- ✅ 강력한 보안성
- ✅ 확장성 좋음
- ✅ 키 관리와 데이터 관리 분리

**구현 방식:**
```
keystore.p12 (마스터 암호화 키)
  └── MASTER_ENCRYPTION_KEY

encrypted-properties.dat (암호화된 프로퍼티)
  ├── JASYPT_PASSWORD (encrypted)
  ├── DEMO_SECRET (encrypted)
  ├── DB_PASSWORD (encrypted)
  └── API_KEY (encrypted)
```

### 전략 B: 키스토어 Private Data 활용

**개념:**
- PrivateKey의 user attributes나 certificate extensions 활용
- 비표준적이지만 기술적으로 가능

**장점:**
- ✅ 순수 키스토어만 사용
- ✅ 단일 파일 관리

**단점:**
- ❌ 비표준적 접근법
- ❌ 호환성 문제 가능성
- ❌ 복잡한 구현

### 전략 C: Properties 파일 + 키스토어 서명

**개념:**
- 일반 properties 파일 사용
- 키스토어로 무결성 검증 (서명)

**장점:**
- ✅ 단순한 구현
- ✅ 표준적인 접근법

**단점:**
- ❌ 평문 저장 (보안성 낮음)
- ❌ 설계 목표와 거리 있음

---

## 📊 권장 전략: A (하이브리드 방식)

### 구현 계획

#### 1단계: 아키텍처 재설계

**새로운 컴포넌트 구조:**
```
com.example.keystore/
├── KeystoreEnvironmentPostProcessor.java (기존 유지)
├── MasterKeyProvider.java (신규)
├── EncryptedPropertySource.java (KeystorePropertySource 대체)
├── PropertyEncryptor.java (신규)
├── KeyExtractor.java (신규 - 테스트용)
└── util/
    ├── CryptoUtils.java (신규)
    └── FileUtils.java (신규)
```

#### 2단계: 핵심 클래스 구현

**MasterKeyProvider.java**
```java
public class MasterKeyProvider {
    private final String keystorePath;
    private final String keystorePassword;
    
    public SecretKey getMasterKey(String alias) {
        // 키스토어에서 마스터 암호화 키 추출
    }
}
```

**PropertyEncryptor.java**
```java
public class PropertyEncryptor {
    private final SecretKey masterKey;
    
    public String encrypt(String plainText) { /* AES 암호화 */ }
    public String decrypt(String encryptedText) { /* AES 복호화 */ }
}
```

**EncryptedPropertySource.java**
```java
public class EncryptedPropertySource extends EnumerablePropertySource<Map<String, String>> {
    private final Map<String, String> decryptedProperties;
    
    public static EncryptedPropertySource from(String keystorePath, String keystorePassword) {
        // 1. 키스토어에서 마스터 키 로드
        // 2. 암호화된 프로퍼티 파일 로드
        // 3. 복호화하여 메모리에 저장
    }
}
```

#### 3단계: 키스토어 생성 도구 개선

**새로운 스크립트 구조:**
```powershell
# 1. 마스터 키 생성
keytool -genseckey -alias MASTER_KEY -keyalg AES -keysize 256 ...

# 2. 프로퍼티 암호화 및 저장
java -cp encloader.jar com.example.keystore.tool.PropertyEncryptorTool \
  --keystore keystore.p12 \
  --keystore-password password123 \
  --property JASYPT_PASSWORD=jasyptSecret456! \
  --property DEMO_SECRET=demoValue789! \
  --output encrypted-properties.dat
```

#### 4단계: 테스트 수정

**KeyExtractor.java 구현**
```java
public class KeyExtractor {
    public static Map<String, String> extractKeys(String keystorePath, String password) {
        // 테스트를 위한 키 추출 로직
    }
}
```

**테스트 케이스 수정**
- 기존 테스트를 새로운 구조에 맞게 수정
- 암호화/복호화 테스트 추가
- 통합 테스트 시나리오 보완

---

## 📅 구현 일정

### Week 1: 설계 및 핵심 구현
- [ ] Day 1-2: 새로운 아키텍처 설계 확정
- [ ] Day 3-4: MasterKeyProvider, PropertyEncryptor 구현
- [ ] Day 5: EncryptedPropertySource 구현

### Week 2: 통합 및 테스트
- [ ] Day 1-2: KeystoreEnvironmentPostProcessor 수정
- [ ] Day 3: KeyExtractor 및 누락된 클래스 구현
- [ ] Day 4-5: 모든 테스트 수정 및 통과 확인

### Week 3: 도구 및 문서화
- [ ] Day 1-2: 키스토어 생성 스크립트 재작성
- [ ] Day 3: 사용법 가이드 업데이트
- [ ] Day 4-5: 성능 테스트 및 보안 검토

---

## 🔒 보안 고려사항

### 암호화 스펙
- **알고리즘**: AES-256-GCM (인증 암호화)
- **키 유도**: PBKDF2WithHmacSHA256
- **솔트**: 랜덤 생성 (파일마다 다름)
- **IV/Nonce**: 랜덤 생성 (암호화마다 다름)

### 메모리 보안
- 복호화된 프로퍼티를 char[] 배열로 관리
- 사용 후 즉시 메모리 클리어 (`Arrays.fill(array, '\0')`)
- WeakReference 활용으로 GC 최적화

### 파일 권한
- 키스토어 파일: 600 (소유자만 읽기/쓰기)
- 암호화 프로퍼티 파일: 600
- 로그에 민감정보 출력 금지

---

## 🧪 테스트 전략

### 단위 테스트
- [ ] MasterKeyProvider 테스트
- [ ] PropertyEncryptor 암호화/복호화 테스트
- [ ] EncryptedPropertySource 테스트
- [ ] 에러 시나리오 테스트 (잘못된 패스워드, 파일 없음 등)

### 통합 테스트
- [ ] Spring Boot 환경에서 전체 플로우 테스트
- [ ] placeholder 해석 테스트
- [ ] 다양한 키스토어 경로 테스트 (classpath, file 등)

### 성능 테스트
- [ ] 대용량 프로퍼티 파일 처리 테스트
- [ ] 애플리케이션 시작 시간 측정
- [ ] 메모리 사용량 모니터링

---

## 🚀 마이그레이션 가이드

### 기존 사용자를 위한 변경사항

**Before:**
```yaml
# application.yml
spring:
  jasypt:
    encryptor:
      password: ${JASYPT_PASSWORD:default-password}
```

```bash
# 실행 방법
java -Dkeystore.path=file:keystore.p12 \
     -Dkeystore.password=password123 \
     -jar app.jar
```

**After:**
```yaml
# application.yml (변경 없음)
spring:
  jasypt:
    encryptor:
      password: ${JASYPT_PASSWORD:default-password}
```

```bash
# 키스토어 생성 (새로운 방식)
./scripts/create-encrypted-properties.ps1 \
  --keystore-password password123 \
  --jasypt-password jasyptSecret456!

# 실행 방법 (동일)
java -Dkeystore.path=file:keystore.p12 \
     -Dkeystore.password=password123 \
     -jar app.jar
```

### 호환성 보장
- 기존 API 인터페이스 유지
- 설정 프로퍼티명 동일 유지
- 점진적 마이그레이션 지원

---

## 📈 예상 효과

### 기능적 개선
- ✅ **100% 테스트 통과**: 모든 기존 테스트 및 신규 테스트
- ✅ **실제 프로퍼티 값 지원**: 문자열 값 저장/조회 가능
- ✅ **강력한 보안**: AES-256 암호화 + 키스토어 보안

### 운영적 개선
- ✅ **명확한 에러 메시지**: 문제 진단 용이
- ✅ **자동화된 도구**: 키스토어 생성/관리 스크립트
- ✅ **문서화**: 상세한 사용법 가이드

### 기술적 개선
- ✅ **표준 준수**: PKCS#12 및 JCA 표준 활용
- ✅ **성능 최적화**: 지연 로딩 및 캐싱
- ✅ **확장성**: 새로운 프로퍼티 추가 용이

---

## 🎯 성공 기준

### 필수 조건 (Must Have)
- [ ] 모든 기존 테스트 통과
- [ ] 실제 문자열 프로퍼티 값 저장/조회 가능
- [ ] Spring placeholder 정상 동작
- [ ] 보안 요구사항 충족

### 권장 조건 (Should Have)
- [ ] 성능 저하 없음 (기존 대비)
- [ ] 사용법 복잡도 증가 없음
- [ ] 명확한 문서화 및 예제

### 선택 조건 (Could Have)
- [ ] GUI 기반 키스토어 관리 도구
- [ ] 다양한 암호화 알고리즘 지원
- [ ] 클라우드 키 관리 서비스 연동

---

## 📞 다음 단계

1. **승인 및 피드백**: 이 계획에 대한 검토 및 승인
2. **상세 설계**: 각 컴포넌트의 상세 인터페이스 설계
3. **프로토타입**: 핵심 기능 검증을 위한 빠른 프로토타입 개발
4. **본격 구현**: 위 일정에 따른 단계별 구현

---

*이 문서는 현재 발견된 문제점들을 체계적으로 해결하기 위한 종합적인 리팩토링 계획입니다. 구현 과정에서 필요에 따라 계획을 조정할 수 있습니다.*