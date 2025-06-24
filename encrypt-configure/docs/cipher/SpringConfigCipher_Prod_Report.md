
# Spring Cloud Config Server 운영 환경 `{cipher}` 사용 보고서

## 1. 개요
Spring Cloud Config Server는 마이크로서비스에서 **구성 정보를 중앙 집중식으로 관리**하고, Git·파일시스템·Vault 등 다양한 백엔드에 저장된 설정을 HTTP API로 노출합니다.  
운영 환경에서는 민감한 값을 안전하게 저장·배포하기 위해 `{cipher}` 암호화 메커니즘을 활용합니다.

## 2. `{cipher}` 메커니즘 소개
- 저장소에는 **암호문(Base64)** 형태로 커밋  
- Config Server 또는 클라이언트가 부팅 시 **자동 복호화**  
- 복호화 실패 시 `invalid.*=<n/a>`로 반환되어 이상 여부를 즉시 확인 가능

## 3. 키 관리 전략

| 방식 | 저장 위치 | 설정 예시 | 장점 | 주의사항 |
|------|----------|-----------|------|---------|
| **대칭 키** | 환경 변수, KMS, Vault 등 | `encrypt.key` 또는 `ENCRYPT_KEY` | 배포 간단 | 키 유출 시 평문 노출 위험 |
| **비대칭 키(RSA)** | PKCS12/JKS keystore | `encrypt.keystore.*` | 운영 : 개인키, 개발 : 공개키 → 보안 ↑ | keystore 파일/비밀번호 관리 필요 |

### 3.1 대칭 키 설정 예시
```yaml
encrypt:
  key: ${ENCRYPT_KEY}
```

### 3.2 비대칭 키 설정 예시
```yaml
encrypt:
  keystore:
    location: classpath:config-keystore.p12
    password: ${KEYSTORE_PASS}
    alias: configkey
    secret: ${KEY_PASS}
```

## 4. Config Server 프로덕션 설정 예시
```yaml
server:
  port: 8888
  ssl:
    enabled: true
    key-store: classpath:config-server.p12
    key-store-password: ${KEYSTORE_SSL_PASS}
spring:
  profiles: prod
  cloud:
    config:
      server:
        git:
          uri: git@intranet:gitrepo/config.git
          clone-on-start: true
          refreshRate: 60
encrypt:
  key: ${ENCRYPT_KEY}   # 또는 keystore 설정
management:
  endpoints.web.exposure.include: health,env,encrypt,decrypt
```

## 5. 암호화 · 커밋 · 배포 플로우
1. 운영 키(예: `ENCRYPT_KEY`) 준비 후 Config Server 재기동  
2. 암호화 요청  
   ```bash
   curl -X POST https://config.example.com:8888/encrypt -d 'plainText'
   # → {cipher}AQB3XzU...
   ```  
3. Git 레포에 `{cipher}` 값 커밋  
4. Config Server가 pull 후 클라이언트에 **평문** 전달

## 6. 클라이언트 직접 복호화 모드
```yaml
spring.cloud.config.server.encrypt.enabled: false  # 서버는 암호문 그대로 전달
encrypt:
  key: ${ENCRYPT_KEY}                              # 클라이언트가 복호화
```

## 7. 키 회전 & 다중 키 운영
- `{key:<alias>}{cipher}...` 형식으로 특정 키 alias 지정  
- keystore에 `2025q2`, `2025q3` 등 다중 키 저장 → 단계적 전환 가능

## 8. 비대칭 RSA 키스토어 생성 예시
```bash
keytool -genkeypair -alias configkey -keyalg RSA -keysize 2048         -storetype PKCS12 -keystore config-keystore.p12 -validity 3650
```

## 9. 장애 트러블슈팅

| 증상 | 원인 | 해결 방법 |
|------|------|-----------|
| `invalid.*=<n/a>` 반환 | 키 불일치, 비밀번호 오류 | 환경 변수 재확인, alias 확인 |
| `Cannot decrypt value` 로그 | Base64 손상 | 줄바꿈/공백 제거 후 재커밋 |
| 클라이언트 복호화 실패 | 키 미지정, 라이브러리 버전 불일치 | `encrypt.*` 설정, Boot·Cloud 버전 일치 |

## 10. 보안 체크리스트
- **TLS 필수**: `/encrypt` 요청·응답 평문 노출 방지  
- 관리자 엔드포인트(`/encrypt`, `/decrypt`, `/actuator/env`)는 **관리망만 허용**  
- 키·비밀번호는 **환경 변수·Secrets Manager·Vault**로 주입  
- CI 파이프라인에서 **자동 암호화 스크립트** 실행 → 평문 노출 최소화  
- **정기 키 회전** 정책 수립 및 `{key:alias}` 메커니즘 활용

## 11. 결론
`{cipher}` 기능을 활용하면 운영 환경에서도 민감한 설정 값을 안전하게 관리·배포할 수 있습니다.  
대칭 키는 간편하지만 보안 리스크가 있으므로, 가능하다면 **비대칭 RSA** 및 **Secrets Manager** 연계를 권장합니다.

---

### 참고 자료
- Spring Cloud Config Reference Guide (2025.0.x)
- Spring Boot 3.3.x Reference Guide
- HashiCorp Vault 공식 문서
