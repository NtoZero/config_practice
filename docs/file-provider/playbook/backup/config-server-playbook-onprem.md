# Spring Cloud Config Server 운영 플레이북 (On‑Premise Git 서버 포함)

작성일: 2025-06-24

---

## 목차
1. 아키텍처 개요  
2. 준비 사항 및 버전 표준  
3. Config Repository & On‑Premise Git 설계  
4. Config Server 구축  
5. 클라이언트(Spring Boot 서비스) 연결  
6. Docker Compose 예제 (Git → Config Server → Client)  
7. 운영·보안 베스트프랙티스  
8. 장애 대응 플레이북  

---

## 1. 아키텍처 개요
```
┌─────────────────────┐  SSH/HTTPS  ┌──────────────────┐
│  On‑Prem Git Server │◀───────────▶  Config Server    │
└─────────────────────┘             │  (Spring Boot)  │
                                    └──────▲──────────┘
                                           │ HTTPS
                                    ┌──────┴─────────┐
                                    │  Client App(s) │
                                    │ (Spring Boot)  │
                                    └────────────────┘
```
- 모든 설정 파일은 사내 망의 **Git 서버**(예: GitLab, Gitea, Bitbucket Data Center)에 보관  
- **Config Server**는 주기적으로 `git pull` 하여 변경 사항을 캐시하고, HTTPS(SSL)로 클라이언트에 제공  

---

## 2. 준비 사항 및 버전 표준

| 항목 | 권장 버전 |
|------|----------|
| JDK | 21 (LTS) |
| Spring Boot | 3.3.x |
| Spring Cloud | 2025.0.x (Wembley) |
| Gradle | 8.5+ |
| Git 서버 | GitLab CE 16.x / Gitea 1.22+ / Bitbucket 8.x |

> **방화벽 포트**: Git (22 SSH, 443 HTTPS) ←→ Config Server, 8888 (Config Server) ←→ Client.

---

## 3. Config Repository & On‑Premise Git 설계

### 3.1 Git 그룹·프로젝트 구조
| 계층 | 예시 네이밍 | 설명 |
|------|-------------|------|
| 그룹 | `infrastructure/config-repo` | Config 전용 단일 Repo |
| 브랜치 | `main`, `release/2025-Q3` | GitOps Tag/Release 사용 |
| 파일 | `orders-prod.yml`, `inventory-dev.yml` | 서비스·프로필 단위 YAML |

### 3.2 접근 제어
- **Deploy Token** 또는 **Read‑only Service Account** 생성 → PAT 저장  
- 토큰은 **`read_repository`** 권한만 부여  
- PAT는 HashiCorp Vault / K8s Secret / Docker Secret으로 Config Server 컨테이너에 주입  

### 3.3 SSH vs HTTPS 인증
| 방식 | 장점 | 단점 | application.yml 설정 |
|------|------|------|-----------------------|
| HTTPS + PAT | 간단, 방화벽 허용 쉬움 | 토큰 만료 주기 관리 | `uri: https://git.intra.local/config-repo.git` |
| SSH 키 | 토큰 만료 없음, Repo Trace 명확 | 22 포트 열어야 함 | `uri: ssh://git@git.intra.local:22/config-repo.git`<br/>`ignore-local-ssh-settings: true` |

> **SSH 키 등록**: Config Server 컨테이너 이미지를 빌드할 때 `/home/spring/.ssh` 에 키·`known_hosts` 복사.

---

## 4. Config Server 구축 (On‑Prem Git 연동)

### 4.1 build.gradle (변동 없음)  

### 4.2 application.yml (HTTPS + PAT 예시)
```yaml
server:
  port: 8888
  ssl:
    enabled: true
    key-store: classpath:config-server-keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12

spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://git.intra.local/infrastructure/config-repo.git
          default-label: main
          username: config-reader
          password: ${'{'}GIT_PAT{'}'}
          clone-on-start: true
          refresh-rate: 60               # 초 단위 폴링 (선택)
          skip-ssh-host-key-check: false # HTTPS 사용 시 무시
        encrypt:
          enabled: true

logging.level.org.eclipse.jgit: INFO
```

#### 4.2.1 SSH 접속용 추가 프로퍼티 (선택)
```yaml
spring:
  cloud:
    config:
      server:
        git:
          uri: ssh://git@git.intra.local:22/infrastructure/config-repo.git
          private-key: |
            -----BEGIN OPENSSH PRIVATE KEY-----
            ...
            -----END OPENSSH PRIVATE KEY-----
          host-key: |
            git.intra.local ecdsa-sha2-nistp256 AAAAE2V...
```

> **보안**: `private-key` 본문을 그대로 적지 말고 외부 Secret 파일(/run/secrets/git-key) 매핑.

### 4.3 실행 (Docker)
```bash
docker run -d --name config-server   -e GIT_PAT=$GIT_PAT   -e JAVA_OPTS="-Xms256m -Xmx512m"   -p 8888:8888   -v $(pwd)/config-server-keystore.p12:/app/config-server-keystore.p12:ro   ghcr.io/your-org/config-server:0.0.1
```

---

## 5. 클라이언트(Spring Boot) 연결 (변동점)

`application.yml` 예)
```yaml
spring:
  application.name: orders
  profiles.active: prod
  config.import: "configserver:https://config-server.intra.local:8888"
```

JAR 구동:
```bash
java -jar orders.jar   --spring.cloud.config.username=config-client   --spring.cloud.config.password=$TOKEN   --spring.cloud.config.fail-fast=true   --spring.cloud.config.retry.maxAttempts=5
```

---

## 6. Docker Compose 예제 (GitLab + Config Server + Client)

```yaml
version: "3.9"
services:
  gitlab:
    image: gitlab/gitlab-ce:16.10.0-ce.0
    hostname: git.intra.local
    container_name: gitlab
    ports:
      - "80:80"
      - "443:443"
      - "22:22"
    volumes:
      - gitlab_config:/etc/gitlab
      - gitlab_logs:/var/log/gitlab
      - gitlab_data:/var/opt/gitlab

  config-server:
    image: ghcr.io/your-org/config-server:0.0.1
    environment:
      - GIT_PAT=${'{'}GIT_PAT{'}'}
    depends_on:
      - gitlab
    ports:
      - "8888:8888"
    networks:
      - backplane
    volumes:
      - ./certs/config-server-keystore.p12:/app/config-server-keystore.p12:ro

  orders-service:
    image: ghcr.io/your-org/orders-service:0.0.1
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_CONFIG_IMPORT=configserver:https://config-server:8888
      - SPRING_CLOUD_CONFIG_USERNAME=config-client
      - SPRING_CLOUD_CONFIG_PASSWORD=${'{'}CONFIG_PASSWORD{'}'}
    depends_on:
      - config-server
    ports:
      - "8080:8080"
    networks:
      - backplane

networks:
  backplane:

volumes:
  gitlab_config:
  gitlab_logs:
  gitlab_data:
```

---

## 7. 운영·보안 베스트프랙티스 (Git 서버)

| 영역 | 체크포인트 |
|------|-----------|
| 인증 | PAT 만료 정책 ≤ 180일, SSH 키 16k 옵션으로 제한 |
| 백업 | Git 서버 Repo 스냅샷 + GitLab Rake 백업 |
| 고가용성 | GitLab Geo Replica / Gitea Cluster |
| 감사로그 | Git audit.log → ELK 스택 |
| 네트워크 | Config Server IP만 Git Repo 방화벽 화이트리스트 |
| 토큰 회전 | Vault → KV v2 secret + Config Server `refresh-rate` 60 초 |

---

## 8. 장애 대응 플레이북 (추가)

| 증상 | 원인 후보 | 대응 |
|------|----------|------|
| `Cannot clone` (status=401) | PAT 만료 | 1) 새 토큰 발급 2) Config Server 컨테이너 재시작 |
| `Host key verification failed` | SSH HostKey 변경 | 1) `known_hosts` 업데이트 2) Config Server `host-key` 교체 |
| `Could not resolve git.intra.local` | DNS 실패 | 내/외부 DNS A 레코드 점검 or `/etc/hosts` 임시 등록 |

---

*(끝)*
