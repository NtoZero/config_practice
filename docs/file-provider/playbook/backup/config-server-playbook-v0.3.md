# Spring Cloud Config Server **Secure 플레이북** v0.3
작성일: 2025-06-24

> **주요 변경점 (v0.3)**
> 1. **Git 저장소 평문 비밀번호 노출** 위험 해결 → **Server-Side Encryption**(`/encrypt`·`/decrypt`) 재도입  
> 2. **Secret 소비 방식** → *ConfigTree 자동 매핑* 한 방식으로 **완전 통일**  
> 3. 예제 간 중복·비일관성 제거, 문서 흐름 재정렬  

---

## 목차
1. 개요 & 아키텍처  
2. 사전 준비(버전·방화벽)  
3. Config Repository(Git) 설계 & 접근 제어  
4. Config Server 구축(**암호화 지원**)  
5. Secret 관리 전략(**ConfigTree 통일**)  
6. 클라이언트(Spring Boot) 통합  
7. Docker Compose 전체 예제  
8. Kubernetes 배포 예제  
9. 운영·보안 베스트프랙티스  
10. 장애 대응 플레이북  

---

## 1. 개요 & 아키텍처

```
┌───────────────┐  SSH/HTTPS  ┌────────────┐  HTTPS  ┌─────────────────┐
│  On-Prem Git  │◀───────────▶│ConfigServer│◀────────▶│Spring Boot Apps │
└───────────────┘             └────────────┘          └─────────────────┘
```

* **On-Prem Git** : 환경설정 YAML 일원화(`infrastructure/config-repo`)  
* **Config Server** : Git Clone 캐시, **Server-Side Encrypt/Decrypt**, TLS(8888)  
* **클라이언트** : `config.import=configserver:` 로 원격 설정 수신  

---

## 2. 사전 준비

| 항목 | 권장 버전                                              |
|------|----------------------------------------------------|
| JDK | 17 (LTS)                                           |
| Spring Boot | 3.3.x                                              |
| Spring Cloud | 2025.0.x                                           |
| GitLab CE / Gitea | 최신 LTS                                             |
| Gradle | 8.5+                                               |
| 방화벽 | **22·443**(Git) ↔ Config Server, **8888** ↔ Client |

---

## 3. Config Repository 설계

| 계층 | 예시 | 설명 |
|------|------|------|
| Repo | `infrastructure/config-repo` | 설정 전용 단일 Repo |
| 브랜치 | `main`, `release/2025-Q3` | GitOps 릴리스 |
| 파일 | `orders-prod.yml` | 서비스·프로필 단위 |

### 3.1 접근 제어

1. **Deploy Token(PAT)** : `read_repository` 권한만  
2. Vault / Docker Secret / K8s Secret → 컨테이너 내부 주입  
3. SSH 사용 시 `/home/spring/.ssh` 비공개키 + `known_hosts` 사전 배포  
4. **민감 정보 평문 저장 금지** → 4.4절 *암호화 워크플로* 필수 준수  

---

## 4. Config Server 구축 & **Encryption 활성화**

### 4.1 `application.yml`

```yaml
server:
  port: 8888
  ssl:
    enabled: true
    key-store: classpath:config-server-keystore.p12
    key-store-password: ${KS_PWD}
    key-store-type: PKCS12

spring:
  application.name: config-server

  cloud:
    config:
      server:
        git:
          uri: https://git.intra.local/infrastructure/config-repo.git
          default-label: main
          username: config-reader
          password: ${GIT_PAT}
          clone-on-start: true
          refresh-rate: 60     # 초 단위 캐시 갱신
        encrypt:               # ▼▼ Server-Side Encrypt ▼▼
          enabled: true
          key-store:
            location: classpath:config-server-keystore.p12
            password: ${KS_PWD}
            type: PKCS12
            alias: encrypt-key
```

`KS_PWD`·`GIT_PAT` 모두 **ConfigTree**(Docker/K8s Secret) 로 주입한다.

### 4.2 실행 예시

```bash
docker run -d --name config-server   -e GIT_PAT=$GIT_PAT   -e KS_PWD=$KS_PWD   -p 8888:8888 ghcr.io/your-org/config-server:1.0.0
```

### 4.3 Health & Admin Endpoints

| 엔드포인트 | 설명 |
|------------|------|
| `/actuator/health` | 시스템 상태 |
| `/actuator/env`    | 환경 변수(민감 값 마스킹) |
| `/encrypt` / `/decrypt` | **Admin-Only** 암·복호화 API |

### 4.4 **암호화 워크플로**

| 단계 | 수행 주체 | 명령 | 결과 |
|------|-----------|------|------|
| 1 | DevOps | `curl -X POST https://config-server:8888/encrypt -d 'superPwd'` | `{cipher}AQB3…` |
| 2 | DevOps | Git 커밋 `orders-prod.yml` 에 `{cipher}…` 값 저장 | 평문 노출 ❌ |
| 3 | Runtime | Config Server가 복호화 → 클라이언트 전달 | 안전한 실행 |

---

## 5. Secret 관리 전략 (**ConfigTree 통일**)

### 5.1 Docker Secret 정의

```yaml
secrets:
  spring.cloud.config.password:
    file: ./secrets/config-pwd      # 파일명 = 프로퍼티명
  GIT_PAT:
    file: ./secrets/git-pat
  KS_PWD:
    file: ./secrets/keystore-pwd
```

### 5.2 Compose 서비스 예

```yaml
orders-service:
  image: ghcr.io/your-org/orders:1.0.0
  environment:
    SPRING_CONFIG_IMPORT: >
      configserver:https://config-server:8888,
      configtree:/run/secrets/
    SPRING_PROFILES_ACTIVE: prod
  secrets:
    - spring.cloud.config.password
  depends_on: [config-server]
```

`/run/secrets/` 안 **파일명**이 자동으로 프로퍼티 Key가 되어 _추가 설정이 필요 없다._

### 5.3 Kubernetes Secret → Volume

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: orders-secrets
type: Opaque
stringData:
  spring.cloud.config.password: "clientPwd!"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: orders
spec:
  template:
    spec:
      containers:
      - name: orders
        env:
          - name: SPRING_CONFIG_IMPORT
            value: "configserver:https://config-server:8888,configtree:/run/secrets/"
        volumeMounts:
          - name: orders-secrets
            mountPath: /run/secrets
            readOnly: true
      volumes:
        - name: orders-secrets
          secret:
            secretName: orders-secrets
            defaultMode: 0440
```

---

## 6. 클라이언트(Spring Boot) 통합

```yaml
# bootstrap.yml
spring:
  application.name: orders
  profiles.active: prod
```

* `SPRING_CONFIG_IMPORT`(Compose/K8s env) 로 `configserver:` + `configtree:` 경로만 지정  
* `spring.cloud.config.password` 등은 Secret 파일로 주입 → **JVM 인자/파일 참조 필요 없음**  
* Fail-Fast, Retry 등은 필요 시 `application.yml` 또는 `--spring.cloud.config.*` 인자로 추가  

---

## 7. Docker Compose 전체 예제

```yaml
version: "3.9"

secrets:
  spring.cloud.config.password: {file: ./secrets/config-pwd}
  GIT_PAT: {file: ./secrets/git-pat}
  KS_PWD: {file: ./secrets/keystore-pwd}

services:
  gitlab:
    image: gitlab/gitlab-ce:16.10.0-ce.0
    hostname: git.intra.local
    ports: ["80:80", "443:443", "22:22"]
    volumes:
      gitlab_config: /etc/gitlab
      gitlab_logs: /var/log/gitlab
      gitlab_data: /var/opt/gitlab

  config-server:
    image: ghcr.io/your-org/config-server:1.0.0
    environment:
      KS_PWD: /run/secrets/KS_PWD
      GIT_PAT: /run/secrets/GIT_PAT
    secrets: [GIT_PAT, KS_PWD]
    depends_on: [gitlab]
    ports: ["8888:8888"]
    volumes:
      - ./certs/config-server-keystore.p12:/app/config-server-keystore.p12:ro

  orders-service:
    image: ghcr.io/your-org/orders:1.0.0
    environment:
      SPRING_CONFIG_IMPORT: configserver:https://config-server:8888,configtree:/run/secrets/
      SPRING_PROFILES_ACTIVE: prod
    secrets: [spring.cloud.config.password]
    depends_on: [config-server]
    ports: ["8080:8080"]

volumes:
  gitlab_config: {}
  gitlab_logs: {}
  gitlab_data: {}
```

---

## 8. Kubernetes 배포 예제

* Config Server → `Deployment` + `Service(ClusterIP/LoadBalancer)`  
* 앱 Pod 환경 변수: `SPRING_CONFIG_IMPORT=configserver:https://config-server:8888,configtree:/run/secrets/`  
* Secret Volume 마운트는 5.3 절 예시 참조  

---

## 9. 운영·보안 베스트프랙티스

| 영역 | 체크리스트 |
|------|-----------|
| **Encryption API** | `/encrypt` 호출 시 TLS + AuthZ(JWT 등) 필수 |
| **PAT 회전** | 90 일 주기. Vault Auto-Rotate → Secret 업데이트 |
| **Secret 파일 권한** | `0440` (읽기 전용) + 가능시 `tmpfs` 마운트 |
| **고가용성** | Config Server **Replica 2+**, GitLab Geo |
| **모니터링** | Actuator + Prometheus → Grafana 대시보드 |
| **감사** | Git Audit, Spring Cloud Config `/actuator/auditevents` |

---

## 10. 장애 대응 플레이북

| 증상 | 원인 | 1차 조치 |
|------|------|----------|
| `401 Unauthorized`(clone) | PAT 만료 | 토큰 교체 → Secret 재주입 → `/actuator/refresh` |
| `Host key verification failed` | Git SSH HostKey 변경 | `known_hosts` 업데이트 후 Pod 재시작 |
| Config Server `503` | Git Repo 불가 / JVM OOM | Git 접근성 확인 → Pod 재시작·메모리 확대 |
| 클라이언트 `Failed to connect` | TLS 불일치 | 클러스터 CA TrustStore 재배포 |

---

*(끝)*
