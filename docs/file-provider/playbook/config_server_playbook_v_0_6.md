# Spring Cloud Config Server **Secure 플레이북** v0.6

작성일: 2025-06-24

> **v0.6 주요 변경점**
>
> 1. 클라이언트 Basic Auth 파일명 변경: `CONFIG_USER`/`CONFIG_PASS` → `spring.cloud.config.username` / `spring.cloud.config.password`
> 2. CI/CD 스크립트: GitHub API 호출 시 GitHub token (`${{ secrets.GITHUB_TOKEN }}`) 사용하도록 수정
> 3. `application.yml` `spring.security` 블록 통합: 중복 키 제거
> 4. **4.3 암호화 워크플로**: curl 명령어 표 안 코드 블록 포매팅 수정
> 5. Docker Compose `SPRING_CONFIG_IMPORT` 구분자 세미콜론(`;`) → 콤마(`,`)로 변경

---

## 목차

1. 개요 & 아키텍처
2. 사전 준비(버전·방화벽)
3. Config Repository(Git) 설계 & 접근 제어
4. Config Server 구축 & 암호화·인증 활성화
5. Secret 관리 전략 (ConfigTree 통일)
6. 클라이언트(Spring Boot) 인증 & 통합
7. Docker Compose 예제
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

- **On-Prem Git**: 설정 YAML 저장소(`infrastructure/config-repo`)
- **Config Server**: Git 캐시, Server-Side Encrypt/Decrypt, TLS(8888/8443), Basic Auth + OAuth2/JWT
- **클라이언트**: `spring.config.import=configserver:,configtree:` 로 원격 설정 수신 및 Basic Auth 사용

---

## 2. 사전 준비

| 항목             | 권장 버전 / 설정                                 |
| -------------- | ------------------------------------------ |
| JDK            | 17 (LTS)                                   |
| Spring Boot    | 3.3.x                                      |
| Spring Cloud   | 2025.0.x (Config Server 이미지와 동일)           |
| GitLab / Gitea | 최신 LTS                                     |
| Gradle/Maven   | Gradle 8.5+ / Maven 3.8+                   |
| 방화벽            | 22·443 ↔ Config Server, 8888·8443 ↔ Client |

---

## 3. Config Repository 설계 & 접근 제어

| 계층   | 예시                           | 설명                  |
| ---- | ---------------------------- | ------------------- |
| Repo | `infrastructure/config-repo` | 단일 설정 전용 Repository |
| 브랜치  | `main`, `release/2025-Q3`    | 릴리스 및 GitOps 관리     |
| 파일   | `orders-prod.yml`            | 서비스·프로필별 설정 파일      |

### 3.1 접근 제어

1. **Deploy Token(PAT)**: `read_repository` 권한, **30일** 회전 주기
2. **SSH Key & HostKey**: `/home/spring/.ssh/id_rsa` + `known_hosts` 사전 배포, 변경 감지 워크플로
3. **민감 정보 평문 금지**: encrypt API 워크플로(4.3절) 준수

---

## 4. Config Server 구축 & 암호화·인증 활성화

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
  security:
    basic:
      enabled: true
      username: ${CONFIG_USER}
      password: ${CONFIG_PASS}
    oauth2:
      resource:
        jwt:
          key-uri: https://auth.intra.local/.well-known/jwks.json
  cloud:
    config:
      server:
        git:
          uri: https://git.intra.local/infrastructure/config-repo.git
          default-label: main
          username: config-reader
          password: ${GIT_PAT}
          skipSslVerification: false
          clone-on-start: true
          refresh-rate: 60
        encrypt:
          enabled: true
          key-store:
            location: classpath:config-server-keystore.p12
            password: ${KS_PWD}
            type: PKCS12
            alias: encrypt-key
management:
  endpoints:
    web:
      exposure: health,info,env,encrypt,decrypt
  security:
    roles:
      - ROLE_CONFIG_ADMIN
```

- `spring.security` 블록을 하나로 통합하여 중복 키 제거
- **Basic Auth**: 클라이언트 인증용 (`CONFIG_USER`/`CONFIG_PASS`)
- **OAuth2/JWT + RBAC**: `/encrypt`, `/decrypt` 엔드포인트 보호

### 4.2 실행 예시

```bash
docker run -d --name config-server \
  --mount type=secret,id=GIT_PAT \
  --mount type=secret,id=KS_PWD \
  --mount type=secret,id=CONFIG_USER \
  --mount type=secret,id=CONFIG_PASS \
  -p 8888:8888 -p 8443:8443 \
  ghcr.io/your-org/config-server:1.0.0
```

### 4.3 암호화 워크플로

| 단계 | 수행 주체               | 명령                                                                                           | 설명             |
| -- | ------------------- | -------------------------------------------------------------------------------------------- | -------------- |
| 1  | DevOps              | `curl -u $CONFIG_USER:$CONFIG_PASS -X POST https://config-server:8443/encrypt -d "superPwd"` | `{cipher}…` 반환 |
| 2  | CI (GitHub Actions) | `{cipher}`를 설정 파일에 자동 커밋 & PR 생성                                                             | 평문 방지          |
| 3  | Runtime             | Config Server가 `{cipher}` 복호화 후 클라이언트 전달                                                     | 안전한 런타임 적용     |

---

## 5. Secret 관리 전략 (ConfigTree 통일)

```yaml
secrets:
  GIT_PAT:                         { file: ./secrets/git-pat }
  KS_PWD:                          { file: ./secrets/keystore-pwd }
  CONFIG_USER:                     { file: ./secrets/config-user }
  CONFIG_PASS:                     { file: ./secrets/config-pass }
  spring.cloud.config.username:    { file: ./secrets/spring.cloud.config.username }
  spring.cloud.config.password:    { file: ./secrets/spring.cloud.config.password }
```

- **파일 기반 마운트**만 허용, 환경변수 직접 주입 금지

---

## 6. 클라이언트(Spring Boot) 인증 & 통합

```yaml
# bootstrap.yml
spring:
  application.name: orders
  profiles:
    active: prod
  config:
    import:
      - configserver:https://config-server:8888
      - configtree:/run/secrets/

# /run/secrets/spring.cloud.config.username, spring.cloud.config.password 파일로 Basic Auth 자동 설정
```

- Secret 파일명이 `spring.cloud.config.username` / `spring.cloud.config.password`로 변경되어, 별도 추가 설정 없이 자동 인증

---

## 7. Docker Compose 예제

```yaml
version: '3.9'
secrets:
  GIT_PAT:                         { file: ./secrets/git-pat }
  KS_PWD:                          { file: ./secrets/keystore-pwd }
  CONFIG_USER:                     { file: ./secrets/config-user }
  CONFIG_PASS:                     { file: ./secrets/config-pass }
  spring.cloud.config.username:    { file: ./secrets/spring.cloud.config.username }
  spring.cloud.config.password:    { file: ./secrets/spring.cloud.config.password }

services:
  config-server:
    image: ghcr.io/your-org/config-server:1.0.0
    secrets: [ GIT_PAT, KS_PWD, CONFIG_USER, CONFIG_PASS ]
    ports: [ '8888:8888', '8443:8443' ]
    volumes:
      - ./certs/config-server-keystore.p12:/app/config-server-keystore.p12:ro

  orders-service:
    image: ghcr.io/your-org/orders:1.0.0
    secrets: [ spring.cloud.config.username, spring.cloud.config.password ]
    ports: [ '8080:8080' ]
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_CONFIG_IMPORT: configserver:https://config-server:8888,configtree:/run/secrets/
    depends_on: [ config-server ]
```

---

## 8. Kubernetes 배포 예제

### Config Server

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
        volumeMounts:
          - name: keystore
            mountPath: /app/config-server-keystore.p12
            subPath: config-server-keystore.p12
          - name: secrets
            mountPath: /run/secrets
      volumes:
        - name: keystore
          secret:
            secretName: config-tls
            items:
              - key: config-server-keystore.p12
                path: config-server-keystore.p12
        - name: ssh
          emptyDir: {}
        - name: secrets
          secret:
            secretName: config-secrets  # contains GIT_PAT, KS_PWD, CONFIG_USER, CONFIG_PASS
```

### Orders-Service

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: orders-secrets
stringData:
  spring.cloud.config.username: orders-user
  spring.cloud.config.password: orders-pass
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: orders-service
spec:
  replicas: 1
  template:
    metadata:
      labels:
        app: orders-service
    spec:
      containers:
      - name: orders-service
        image: ghcr.io/your-org/orders:1.0.0
        ports:
          - containerPort: 8080
        env:
          - name: SPRING_PROFILES_ACTIVE
            value: prod
          - name: SPRING_CONFIG_IMPORT
            value: configserver:https://config-server:8888,configtree:/run/secrets/
        volumeMounts:
          - name: secrets
            mountPath: /run/secrets
      volumes:
        - name: secrets
          secret:
            secretName: orders-secrets
```

---

## 9. 운영·보안 베스트프랙티스

| 영역               | 체크리스트                                                        |
| ---------------- | ------------------------------------------------------------ |
| **Admin API 보호** | Basic Auth + OAuth2/JWT + RBAC(Client Role) + IP 화이트리스트 적용   |
| **PAT 회전**       | 30일 주기, GitHub Actions 자동화                                   |
| **TLS 인증서 관리**   | 내부 CA·Let's Encrypt 자동 갱신, 키 롤링 스크립트 제공                      |
| **Secret 권한**    | `0400`/`0440` + `tmpfs` 마운트                                  |
| **Git 보안**       | `skipSslVerification=false`, SSH HostKey 변경 감지 initContainer |
| **고가용성 & 모니터링**  | Replica 2+, Actuator→Prometheus→Grafana→Audit 로그             |

---

## 10. CI/CD 파이프라인 자동화

```yaml
name: Rotate PAT and Encrypt Secrets
on:
  schedule:
    - cron: '0 0 */30 * *'
jobs:
  rotate:
    runs-on: ubuntu-latest
    steps:
      - name: Generate new PAT
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          export NEW_PAT=$(curl -H "Authorization: token $GITHUB_TOKEN" \
            -X POST https://api.github.com/repos/your-org/config-repo/deployments \
            | jq -r .token)
      - name: Encrypt new PAT
        env:
          CONFIG_USER: ${{ secrets.CONFIG_USER }}
          CONFIG_PASS: ${{ secrets.CONFIG_PASS }}
        run: |
          curl -u $CONFIG_USER:$CONFIG_PASS \
            -X POST https://config-server:8443/encrypt \
            -d "$NEW_PAT" > cipher.txt
      - name: Commit & Push
        run: |
          git checkout main
          sed -i "s/{cipher}.*/$(cat cipher.txt)/" src/main/resources/orders-prod.yml
          git commit -am "chore: rotate PAT and update encrypted secret"
          git push
```

---

## 11. 장애 대응 플레이북

| 증상                             | 원인                  | 1차 조치                                        |
| ------------------------------ | ------------------- | -------------------------------------------- |
| `401 Unauthorized` (Git clone) | PAT 만료              | PAT 재생성 → Secret 업데이트 → Config Server 재시작    |
| `403 Forbidden` (Admin API)    | Role 권한 부족          | RBAC 설정 확인 → OAuth2 서버 RoleBinding 검토        |
| HostKey verification 실패        | SSH HostKey 변경      | InitContainer 재실행 → Pod 재배포                  |
| `/actuator/env` 403 오류         | RBAC/Basic Auth 미설정 | `management.security` 설정 확인 → RoleBinding 적용 |
| Config Server `503`            | Git 접근 불가 / OOM     | 네트워크 점검 → 메모리 확대 → Pod 재시작                   |
| 클라이언트 연결 실패                    | Basic Auth 불일치      | `/run/secrets` 자격증명 확인 → Deployment 재배포      |

*(끝)*

