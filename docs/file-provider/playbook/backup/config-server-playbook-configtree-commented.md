# Spring Cloud Config Server 플레이북 – ConfigTree 버전 (주석 추가)

작성일: 2025-06-24

---

## Docker Compose (주석 버전)

```yaml
version: "3.9"                # 최신 Compose 스펙
secrets:
  config_pwd:
    file: ./secrets/config-pwd # 비밀번호가 들어있는 로컬 파일 (git 커밋 금지)

services:
  # ▼ Config Server
  config-server:
    image: ghcr.io/your-org/config-server:1.0.0   # 사전 빌드된 이미지
    environment:
      - GIT_PAT_FILE=/run/secrets/git-pat         # Git PAT을 파일로 주입
    secrets:
      - git-pat
    ports:
      - "8888:8888"
    volumes:
      - ./keystore/config-server.p12:/app/config-server.p12:ro  # TLS 키스토어

  # ▼ 비즈니스 서비스
  orders-service:
    image: ghcr.io/your-org/orders:1.0.0
    environment:
      - SPRING_CONFIG_IMPORT=configserver:https://config-server:8888,configtree:/run/secrets/
      - SPRING_CLOUD_CONFIG_USERNAME=config-client # Basic Auth ID
    secrets:
      - config_pwd
    depends_on:
      - config-server
    ports:
      - "8080:8080"
```

---

## Kubernetes Secret & Deployment (주석 버전)

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: config-pwd                   # Secret 이름 = 파일 이름
type: Opaque
stringData:
  config-pwd: "VeryStrongPwd!"       # 파일의 내용

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
          mountPath: /run/secrets            # ConfigTree 루트
          readOnly: true
      volumes:
      - name: config-pwd
        secret:
          secretName: config-pwd
          defaultMode: 0440                  # 읽기 전용(루트만)
```

---

## application.yml (주석 버전)

```yaml
spring:
  cloud:
    config:
      username: ${SPRING_CLOUD_CONFIG_USERNAME}   # 환경 변수로 주입
      password: ${file:/run/secrets/config-pwd}   # Secret 파일(0440)
```

---

*(끝)*
