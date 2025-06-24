# Spring Cloud Config Server **Secure 플레이북** v0.4

작성일: 2025-06-24

> **주요 변경점 (v0.4)**
>
> 1. `/actuator/env`, `/encrypt`·`/decrypt` 엔드포인트에 RBAC·IP 화이트리스트 예시 및 OAuth2/JWT 인증 정보 추가
> 2. Admin API 보호 강화를 위한 구체적 권한 설정 예시(RBAC, Client Role)
> 3. PAT 회전 주기 단축: **30일**로 조정 및 GitHub Actions 자동화 스크립트 예시
> 4. TLS 인증서 발급·갱신 프로세스(내부 CA/Let's Encrypt) 및 키 롤링 가이드 추가
> 5. Git 서버 클론 시 `ssl.verify=true` 설정, SSH HostKey 변경 감지 워크플로 예시 추가
> 6. Secret 주입 방식 **파일 기반(ConfigTree)** 으로 완전 통일 (환경변수 직접 주입 제거)
> 7. 이미지 버전 호환성: `ghcr.io/your-org/config-server:1.0.0`는 Spring Cloud 2025.0.x 기반임을 명시
> 8. Spring Boot 3.3+ `bootstrap.yml` vs `application.yml` 부트스트랩 로딩 순서 주의사항 추가
> 9. Kubernetes: Config Server Deployment 예제 보완(keystore·TLS Secret 마운트, hostKey 체크 initContainer)
> 10. CI/CD 파이프라인에서 Secret·PAT 자동 회전 스크립트 예시 추가

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
10. CI/CD 파이프라인 자동화
11. 장애 대응 플레이북

---

## 1. 개요 & 아키텍처

```
┌───────────────┐  SSH/HTTPS  ┌────────────┐  HTTPS  ┌─────────────────┐
│  On-Prem Git  │◀───────────▶│ConfigServer│◀────────▶│Spring Boot Apps │
└───────────────┘             └────────────┘          └─────────────────┘
```

- **On-Prem Git** : 설정 YAML 일원화(`infrastructure/config-repo`)
- **Config Server** : Git Clone 캐시, **Server-Side Encrypt/Decrypt**, TLS(8888, 8443)
- **클라이언트** : `spring.config.import=configserver:,configtree:` 로 원격 설정 수신

---

## 2. 사전 준비

| 항목             | 권장 버전 / 설정                                                  |
| -------------- | ----------------------------------------------------------- |
| JDK            | 17 (LTS)                                                    |
| Spring Boot    | 3.3.x                                                       |
| Spring Cloud   | 2025.0.x (Config Server 이미지도 동일 버전 기반)                      |
| GitLab / Gitea | 최신 LTS                                                      |
| Gradle/Maven   | Gradle 8.5+ / Maven 3.8+                                    |
| 방화벽            | **22·443**(Git) ↔ Config Server, **8888**·**8443** ↔ Client |

---

## 3. Config Repository 설계 & 접근 제어

| 계층   | 예시                           | 설명                  |
| ---- | ---------------------------- | ------------------- |
| Repo | `infrastructure/config-repo` | 설정 전용 단일 Repository |
| 브랜치  | `main`, `release/2025-Q3`    | GitOps 릴리스 관리       |
| 파일   | `orders-prod.yml`            | 서비스·프로필별 설정 단위      |

### 3.1 접근 제어

1. **Deploy Token(PAT)**: `read_repository` 권한만 허용, 회전 주기 **30일**
2. Vault/Docker Secret/K8s Secret → \*\*파일 기반(ConfigTree)\*\*로 주입
3. SSH 진입 시 `/home/spring/.ssh/id_rsa` + `known_hosts` 사전 배포 및 변경 감지
4. **민감 정보 평문 저장 금지** → 4.4절 암호화 워크플로 필수 준수

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
          skipSslVerification: false   # TLS 인증서 검증 강제
          clone-on-start: true
          refresh-rate: 60
        encrypt:
          enabled: true
          key-store:
            location: classpath:config-server-keystore.p12
            password: ${KS_PWD}
            type: PKCS12
            alias: encrypt-key
```

- **버전 호환성**: `ghcr.io/your-org/config-server:1.0.0` 이미지는 Spring Cloud 2025.0.x, JDK17 기반입니다.

### 4.2 실행 예시

```bash
docker run -d --name config-server   \
  --mount type=secret,id=GIT_PAT    \
  --mount type=secret,id=KS_PWD     \
  -p 8888:8888                      \
  -p 8443:8443                      \
  ghcr.io/your-org/config-server:1.0.0
```

### 4.3 Health & Admin Endpoints

| 엔드포인트                  | 설명                                                               |
| ---------------------- | ---------------------------------------------------------------- |
| `/actuator/health`     | 시스템 상태                                                           |
| `/actuator/env`        | 환경 변수 구조만 노출 (Secret 값은 마스킹)                                     |
| `/encrypt`, `/decrypt` | **Admin-Only**: OAuth2/JWT + RBAC(Client Role) + IP 화이트리스트 적용 예시 |

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,env
  endpoint:
    env:
      enabled: true
  security:
    roles:
      - ROLE_CONFIG_ADMIN
spring:
  security:
    oauth2:
      resource:
        jwt:
          key-uri: https://auth.intra.local/.well-known/jwks.json
```

### 4.4 **암호화 워크플로**

| 단계 | 수행 주체                      | 명령                                                                                                | 결과              |
| -- | -------------------------- | ------------------------------------------------------------------------------------------------- | --------------- |
| 1  | DevOps                     | `curl -X POST https://config-server:8443/encrypt -H 'Authorization: Bearer $TOKEN' -d 'superPwd'` | `{cipher}AQB3…` |
| 2  | DevOps CI (GitHub Actions) | PAT 자동 저장 스크립트 실행 → `{cipher}` 커밋 → Pull Request 자동 생성                                            | 평문 노출 방지        |
| 3  | Runtime                    | Config Server가 복호화 후 클라이언트 전달                                                                     | 안전한 런타임 설정 적용   |

---

## 5. Secret 관리 전략 (**ConfigTree 통일**)

```yaml
secrets:
  spring.cloud.config.password: { file: ./secrets/config-pwd }
  GIT_PAT:                    { file: ./secrets/git-pat }
  KS_PWD:                     { file: ./secrets/keystore-pwd }
```

- 모든 서비스에서 **파일 기반** 마운트만 사용하며 환경변수 직접 주입은 제거하였습니다.

---

## 6. 클라이언트(Spring Boot) 통합

```yaml
# bootstrap.yml (Spring Boot 3.3+ 부트스트랩 우선 로딩)
spring:
  application.name: orders
  profiles:
    active: prod
  config:
    import:
      - configserver:https://config-server:8888
      - configtree:/run/secrets/
```

- `bootstrap.yml` 로 원격 설정이 반드시 최초 로딩될 수 있도록 합니다.

---

## 7. Docker Compose 전체 예제

```yaml
version: "3.9"
secrets:
  spring.cloud.config.password: { file: ./secrets/config-pwd }
  GIT_PAT:                    { file: ./secrets/git-pat }
  KS_PWD:                     { file: ./secrets/keystore-pwd }

services:
  gitlab:
    image: gitlab/gitlab-ce:16.10.0-ce.0
    hostname: git.intra.local
    ports: ["80:80","443:443","22:22"]
    volumes:
      - gitlab_config:/etc/gitlab
      - gitlab_logs:/var/log/gitlab
      - gitlab_data:/var/opt/gitlab

  config-server:
    image: ghcr.io/your-org/config-server:1.0.0
    secrets: [ GIT_PAT, KS_PWD ]
    ports: ["8888:8888","8443:8443"]
    volumes:
      - ./certs/config-server-keystore.p12:/app/config-server-keystore.p12:ro

  orders-service:
    image: ghcr.io/your-org/orders:1.0.0
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_CONFIG_IMPORT: configserver:https://config-server:8888,configtree:/run/secrets/
    secrets: [ spring.cloud.config.password ]
    ports: ["8080:8080"]
    depends_on: [ config-server ]

volumes:
  gitlab_config: {}
  gitlab_logs: {}
  gitlab_data: {}
```

---

## 8. Kubernetes 배포 예제

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: config-server
spec:
  replicas: 2
  template:
    metadata:
      labels:
        app: config-server
    spec:
      initContainers:
      - name: ssh-known-hosts
        image: alpine:latest
        command:
          - sh
          - -c
          - |
            mkdir -p /home/spring/.ssh && \
            ssh-keyscan git.intra.local >> /home/spring/.ssh/known_hosts
        volumeMounts:
          - name: ssh
            mountPath: /home/spring/.ssh
      containers:
      - name: config-server
        image: ghcr.io/your-org/config-server:1.0.0
        ports:
          - containerPort: 8888
          - containerPort: 8443
        env:
          - name: GIT_PAT
            valueFrom:
              secretKeyRef:
                name: config-secrets
                key: GIT_PAT
          - name: KS_PWD
            valueFrom:
              secretKeyRef:
                name: config-secrets
                key: KS_PWD
        volumeMounts:
          - name: keystore
            mountPath: /app/config-server-keystore.p12
            subPath: config-server-keystore.p12
          - name: ssh
            mountPath: /home/spring/.ssh
      volumes:
        - name: keystore
          secret:
            secretName: config-tls
            items:
              - key: config-server-keystore.p12
                path: config-server-keystore.p12
        - name: ssh
          emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: config-server
spec:
  type: LoadBalancer
  ports:
    - port: 8888
    - port: 8443
  selector:
    app: config-server
```

---

## 9. 운영·보안 베스트프랙티스

| 영역                 | 체크리스트                                                                    |
| ------------------ | ------------------------------------------------------------------------ |
| **Encryption API** | TLS + OAuth2/JWT + RBAC(Client Role) + IP 화이트리스트 적용                      |
| **PAT 회전**         | 주기: **30일**. GitHub Actions → 자동 회전 + PR 생성 예시 제공                        |
| **TLS 인증서 관리**     | 내부 CA 또는 Let's Encrypt 자동 갱신, 키 롤링 스크립트 예시                               |
| **Secret 권한**      | `0400` 또는 `0440` (읽기 전용) + `tmpfs` 마운트                                   |
| **고가용성**           | Config Server Replica **2+**, GitLab Geo / HAProxy 등                     |
| **모니터링**           | Actuator → Prometheus → Grafana, `/actuator/auditevents` 감사 로그 수집        |
| **Git SSL 검증**     | `skipSslVerification=false` (HTTPS) + SSH HostKey 변경 감지 InitContainer 적용 |

---

## 10. CI/CD 파이프라인 자동화

```yaml
# .github/workflows/rotate-pat.yml
name: Rotate PAT and Encrypt Secrets
on:
  schedule:
    - cron: '0 0 */30 * *'  # 매 30일
jobs:
  rotate:
    runs-on: ubuntu-latest
    steps:
      - name: Generate new PAT
        run: |
          export NEW_PAT=$(curl -X POST \
            -H "Authorization: Bearer ${{ secrets.GH_TOKEN }}" \
            https://api.github.com/repos/your-org/config-repo/deployments | jq -r .token)
      - name: Encrypt new PAT
        run: |
          curl -X POST https://config-server:8443/encrypt \
            -H "Authorization: Bearer ${{ secrets.CONFIG_TOKEN }}" \
            -d "$NEW_PAT" > cipher.txt
      - name: Commit and push
        run: |
          git checkout main
          sed -i "s/{cipher}.*/$(cat cipher.txt)/" src/main/resources/orders-prod.yml
          git commit -am "chore: rotate PAT and update encrypted secret"
          git push origin main
```

---

## 11. 장애 대응 플레이북

| 증상                             | 원인                  | 1차 조치                                          |
| ------------------------------ | ------------------- | ---------------------------------------------- |
| `401 Unauthorized` (Git clone) | PAT 만료              | 새로운 PAT 생성 → Secret 재주입 → Config Server 재시작    |
| `403 Forbidden` (Admin API)    | JWT 토큰 권한 부족        | 역할(Role) 설정 확인 → OAuth2 서버 정책 재검토              |
| `Host key verification failed` | SSH HostKey 변경      | InitContainer에서 `ssh-keyscan` 재실행 → Pod 롤링 재배포 |
| `/actuator/env` 접속 시 403       | RBAC 설정 미적용         | `management.security` 설정 확인 → RoleBinding 적용   |
| Config Server `503`            | Git 접근 불가 / JVM OOM | 네트워크 확인 → 메모리 할당 확대 → Pod 재시작                  |
| 클라이언트 `Failed to connect`      | TLS 인증서 불일치         | TrustStore 갱신 → Config Server 인증서 재배포          |

*(끝)*

