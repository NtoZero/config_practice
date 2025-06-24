# Spring Cloud Config Server 플레이북 – ConfigTree(K8s/Docker Secret) 버전

작성일: 2025-06-24

---

## 개요
Config Server 접속 비밀번호를 **커맨드라인 인자나 환경 변수에 노출하지 않고** K8s/Docker Secret을
`ConfigTree` 방식으로 마운트해 안전하게 전달합니다.

---

## 1. 핵심 아키텍처

```
[Secret File] (/run/secrets/config-pwd)
        │
        ▼
Spring Boot App ──▶  HTTPS ──▶ Config Server
```

---

## 2. Secret 생성 & 마운트

### 2.1 Docker Compose 예시

```yaml
version: "3.9"
secrets:
  config_pwd:
    file: ./secrets/config-pwd

services:
  orders-service:
    image: ghcr.io/your-org/orders:1.0.0
    environment:
      - SPRING_CONFIG_IMPORT=configserver:https://config-server:8888,configtree:/run/secrets/
      - SPRING_CLOUD_CONFIG_USERNAME=config-client
    secrets:
      - config_pwd
    depends_on:
      - config-server
    ports:
      - "8080:8080"
```

### 2.2 Kubernetes 매니페스트 예시

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: config-pwd
type: Opaque
stringData:
  config-pwd: "VeryStrongPwd!"

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
        image: ghcr.io/your-org/orders:1.0.0
        env:
        - name: SPRING_CONFIG_IMPORT
          value: "configserver:https://config-server:8888,configtree:/run/secrets/"
        - name: SPRING_CLOUD_CONFIG_USERNAME
          value: "config-client"
        volumeMounts:
        - name: config-pwd
          mountPath: /run/secrets
          readOnly: true
      volumes:
      - name: config-pwd
        secret:
          secretName: config-pwd
          defaultMode: 0440
```

---

## 3. Spring Boot 클라이언트 설정

`application.yml`

```yaml
spring:
  cloud:
    config:
      username: ${"SPRING_CLOUD_CONFIG_USERNAME"}
      password: ${file:/run/secrets/config-pwd}
```

- `SPRING_CONFIG_IMPORT` 에 `configtree:/run/secrets/` 를 포함해야 함.

---

## 4. 운영 체크리스트

| 항목 | 설명 |
|------|------|
| Secret 퍼미션 | K8s/Docker 모두 0440 유지 |
| 로그 | 비밀번호 출력되지 않도록 마스킹 필터 |
| 롤링 업데이트 | Secret 변경 후 Pod 재시작 |

---

*(끝)*
