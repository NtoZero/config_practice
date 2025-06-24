# Spring Cloud Config Server 운영 플레이북 – **Git 암호화 저장** 버전

작성일: 2025-06-24

---

## 변경 개요
기존 플레이북은 Git Repo에 **평문 프로퍼티**를 저장하는 전제를 두었습니다. 본 개정판은
**Git Repo에 암호화된 값만 저장**하도록 ‘Spring Cloud Config Server Encryption’ 기능을
접목합니다.

핵심 구조:
```
[Git Repo]  ← 암호화된 {{cipher}} 값
      ▲ 1.clone/pull
      │
[Config Server] ──➔ 2.복호화 ──➔ 3.HTTPS ──➔ [Spring Boot Client]
```
1. **암·복호 키**는 Config Server에만 존재  
2. Git 저장소 유출 시 민감 정보는 노출되지 않음  

---

## 1. 암호화 키 전략

| 모드 | 설명 | 권장 시나리오 |
|------|------|--------------|
| **대칭키 (`encrypt.key`)** | 간단, JCE 비대칭 불필요 | PoC·소규모 |
| **비대칭 RSA 키스토어** | 공개키로 암호화, 개인키로 복호화 | **프로덕션 권장** |

### 1.1 키스토어 생성
```bash
keytool -genkeypair -alias enc-key   -keyalg RSA -keysize 4096   -keystore encryption-keystore.p12   -storetype PKCS12 -validity 3650   -dname "CN=config-encrypt, OU=IT, O=Example"   -storepass changeit -keypass changeit
```

`encryption-keystore.p12`는 Config Server 이미지에만 포함하거나 K8s Secret으로 마운트.

---

## 2. Config Server application.yml (추가)

```yaml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://git.intra.local/infrastructure/config-repo.git
          default-label: main
          username: config-reader
          password: ${'{'}GIT_PAT{'}'}
        encrypt:
          enabled: true
          key-store:
            location: classpath:encryption-keystore.p12
            password: changeit
            alias: enc-key
```

---

## 3. Git Repo 암호화 워크플로

### 3.1 값 암호화
```bash
ENC=$(curl -s -X POST -d 'dbStrongPwd!'   https://config-server.intra.local:8888/encrypt)
echo $ENC   # → {{cipher}}AQB+...
```

`orders-prod.yml`:
```yaml
spring:
  datasource:
    password: "{{cipher}}AQB+..."
```

### 3.2 CLI 자동화 스크립트
```bash
#!/usr/bin/env bash
SERVER=https://config-server.intra.local:8888
while IFS= read -r secret; do
  curl -s -X POST -d "$secret" $SERVER/encrypt
done < secrets.txt
```

---

## 4. 클라이언트 설정 (변경 없음)
클라이언트는 `{cipher}` 값을 받아 자동으로 복호화된 평문을 사용.

---

## 5. 보안 가이드라인
1. 키스토어를 Config Server 전용 Secret으로 관리  
2. `/encrypt` 엔드포인트는 CI/CD Runner만 접근 허용  
3. 키 회전 시나리오: 새 키스토어 배포 → 재암호화 → 구 키 폐기  

---

*(끝)*
