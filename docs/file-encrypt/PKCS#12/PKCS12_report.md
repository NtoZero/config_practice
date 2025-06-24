# PKCS#12(개인 정보 교환 표준)

## 개요
PKCS#12(또는 PFX)는 RSA Laboratories에서 정의한 개인 정보 교환 표준으로, 개인 키(private key), 공개 키 인증서(certificate), 인증서 체인(chain) 등을 하나의 암호화된 바이너리 파일에 저장하고 안전하게 전송할 수 있도록 설계되었습니다.

## 원리
1. **구조적 컨테이너**  
   - 개인 키, 인증서, CA 체인, 기타 비밀 정보 등을 하나의 파일에 포함합니다.  
2. **암호화 및 무결성**  
   - **암호화(Privacy):** 패스워드 기반 키 유도 함수(PBES, PBKDF)를 사용하여 파일 전체 또는 각 엔트리를 암호화합니다.  
   - **무결성 검증(Integrity):** MAC(Message Authentication Code)을 통해 파일 변조 여부를 검증합니다.  
3. **이식성 및 상호 운용성**  
   - 다양한 운영체제, 브라우저, 서버, 클라이언트 애플리케이션에서 폭넓게 지원됩니다.

## 사용 예시

### 1) OpenSSL을 활용한 PKCS#12 생성
```bash
openssl pkcs12 -export \
  -out certificate.p12 \
  -inkey privateKey.key \
  -in certificate.crt \
  -certfile CACert.crt \
  -name "myalias"
```

### 2) Java Keytool을 활용한 PKCS#12 변환
```bash
keytool -importkeystore \
  -srckeystore keystore.jks \
  -destkeystore keystore.p12 \
  -deststoretype PKCS12 \
  -srcalias myalias
```

### 3) Java 코드에서 PKCS#12 로드 예시
```java
KeyStore pkcs12Store = KeyStore.getInstance("PKCS12");
try (InputStream fis = new FileInputStream("keystore.p12")) {
    pkcs12Store.load(fis, passwordCharArray);
}
// SSLContext 초기화 등에서 사용
```

## 파일 보관 방법 (Best Practices)
1. **강력한 패스워드**  
   - 최소 16자 이상의 복잡한 무작위 문자열 사용 및 주기적 변경  
2. **파일 권한 제한**  
   ```bash
   chmod 600 keystore.p12
   chown root:root keystore.p12
   ```  
3. **비밀 관리 시스템 연동**  
   - HSM, Azure Key Vault, AWS KMS, GCP KMS, HashiCorp Vault 등  
4. **버전 관리 제외**  
   - `.gitignore` 등에 `.p12`, `.pfx` 파일 및 패스워드 제외  
5. **안전한 전송**  
   - HTTPS, SFTP, SCP 등 암호화된 채널 사용  
6. **백업 및 폐기 정책**  
   - 주기적 백업 후 안전 보관, 수명 지난 파일은 안전 폐기

## 유출 시 영향도 (Impact)
1. **인증서 위조 및 중간자 공격(MITM)**  
   - 개인 키가 유출될 경우 정상 서비스로 위장한 악의적 서버를 구성 가능  
2. **데이터 복호화 위험**  
   - 암호화된 통신(HTTPS, TLS 등)의 종단 간 보안이 무력화  
3. **서비스 신뢰도 하락**  
   - 사용자 및 파트너사로부터 신뢰 상실, 법적·금융적 책임 발생 가능  
4. **추가 확산 위험**  
   - 동일 키가 여러 시스템에서 사용 중일 경우 다수 시스템이 동시에 위험에 노출

## 대응 방법 (Mitigation & 대응 절차)
1. **긴급 키 폐기 및 교체**  
   - 유출된 키를 즉시 폐기(revocation)하고 신규 키 발급  
2. **인증서 재발급 및 배포**  
   - Certificate Authority(CA)를 통해 새 인증서 발급 및 시스템에 배포  
3. **취약 기간 로그 분석**  
   - 유출 시점 전후의 서버·네트워크 로그를 분석하여 이상 활동 탐지  
4. **통제된 롤링 업데이트**  
   - 서비스 중단 최소화 방안으로 순차적 키 변경  
5. **보안 정책 강화**  
   - 키 관리 담당자 교육, 절차 문서화, 정기 보안 감사 실시  
6. **침해 사고 보고 및 공지**  
   - 관련 내부·외부 기관 및 사용자에게 사고 사실 및 대응 조치 공지

## 참고자료
- IETF RFC 7292 - PKCS #12: Personal Information Exchange Syntax v1.1  
  https://tools.ietf.org/html/rfc7292  
- RFC Editor 개요 페이지  
  https://www.rfc-editor.org/rfc/rfc7292
- Stack Overflow: OpenSSL로 PKCS#12 파일 생성하기  
  https://stackoverflow.com/questions/2131259/how-to-create-a-pkcs-12-file-using-openssl
