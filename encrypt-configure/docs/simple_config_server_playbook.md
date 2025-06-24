# Simple Spring Config Server 플레이북 v1.0

작성일: 2025-06-25

## 목차
1. 개요
2. 프로젝트 구조
3. Config Server 구성
4. Config Repository 설정
5. 클라이언트 연동
6. 실행 및 테스트
7. Docker 배포

---

## 1. 개요

```
┌─────────────┐  HTTPS  ┌────────────┐  HTTP   ┌─────────────────┐
│   GitHub    │◀────────▶│ConfigServer│◀────────▶│Spring Boot Apps │
│ Config Repo │         └────────────┘          └─────────────────┘
└─────────────┘              :8888
```

- **GitHub Repository**: 설정 YAML 파일 저장소
- **Config Server**: GitHub에서 설정을 가져와 HTTP로 제공 (포트: 8888)
- **클라이언트**: Config Server에서 설정을 가져와 사용

---

## 2. 프로젝트 구조

### 현재 encrypt-configure 모듈 활용
```
encrypt-configure/
├── src/main/java/demo/encryptconfigure/
│   └── EncryptConfigureApplication.java
├── src/main/resources/
│   └── application.yml
├── src/test/resources/
│   ├── application-test.yml
│   └── config-repo/           # 로컬 테스트용 설정 파일
└── build.gradle
```

---

## 3. Config Server 구성

### 3.1 build.gradle 의존성

현재 의존성이 적절하므로 그대로 사용:

```gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-config-server'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

### 3.2 application.yml 설정

```yaml
server:
  port: 8888

spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://github.com/{your-username}/{config-repo-name}
          default-label: main
          clone-on-start: true
          timeout: 10
        health:
          repositories:
            config-repo:
              label: main

management:
  endpoints:
    web:
      exposure:
        include: health,info,env,configprops
  endpoint:
    health:
      show-details: always

logging:
  level:
    org.springframework.cloud.config: INFO
```

### 3.3 메인 애플리케이션 클래스

현재 `EncryptConfigureApplication.java`가 이미 적절하게 구성되어 있습니다:

```java
@EnableConfigServer
@SpringBootApplication
public class EncryptConfigureApplication {
    public static void main(String[] args) {
        SpringApplication.run(EncryptConfigureApplication.class, args);
    }
}
```

---

## 4. Config Repository 설정

### 4.1 GitHub Repository 생성

1. GitHub에서 새 리포지토리 생성 (예: `config-repo`)
2. 다음과 같은 구조로 설정 파일 작성:

```
config-repo/
├── application.yml              # 모든 서비스 공통 설정
├── application-dev.yml          # 개발 환경 공통 설정
├── application-prod.yml         # 운영 환경 공통 설정
├── orders-service.yml           # orders-service 기본 설정
├── orders-service-dev.yml       # orders-service 개발 환경
├── orders-service-prod.yml      # orders-service 운영 환경
└── user-service.yml            # user-service 기본 설정
```

### 4.2 설정 파일 예시

**application.yml** (공통 설정)
```yaml
# 모든 애플리케이션 공통 설정
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
  level:
    root: INFO
```

**orders-service-dev.yml** (서비스별 개발 환경)
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

app:
  config:
    message: "Orders Service - Development Environment"
    debug: true
```

**orders-service-prod.yml** (서비스별 운영 환경)
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://prod-db:3306/orders
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: orders_user
    password: secure_password
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

app:
  config:
    message: "Orders Service - Production Environment"
    debug: false
```

---

## 5. 클라이언트 연동

### 5.1 클라이언트 의존성

클라이언트 애플리케이션의 `build.gradle`에 추가:

```gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-config'
    implementation 'org.springframework.cloud:spring-cloud-starter-bootstrap'
}
```

### 5.2 클라이언트 설정

**bootstrap.yml** 또는 **application.yml**:

```yaml
spring:
  application:
    name: orders-service
  profiles:
    active: dev
  config:
    import: "configserver:http://localhost:8888"
  cloud:
    config:
      uri: http://localhost:8888
      fail-fast: true
      retry:
        initial-interval: 1000
        max-attempts: 6
```

### 5.3 설정값 사용 예시

```java
@RestController
@RefreshScope  // 설정 갱신 지원
public class ConfigController {
    
    @Value("${app.config.message:Default Message}")
    private String message;
    
    @Value("${app.config.debug:false}")
    private boolean debug;
    
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("message", message);
        config.put("debug", debug);
        return config;
    }
}
```

---

## 6. 실행 및 테스트

### 6.1 Config Server 실행

```bash
cd encrypt-configure
./gradlew bootRun
```

### 6.2 설정 확인

Config Server가 실행되면 다음 URL로 설정을 확인할 수 있습니다:

| URL | 설명 |
|-----|------|
| `http://localhost:8888/orders-service/dev` | orders-service의 dev 프로필 설정 |
| `http://localhost:8888/orders-service/prod` | orders-service의 prod 프로필 설정 |
| `http://localhost:8888/application/dev` | 모든 서비스의 dev 공통 설정 |
| `http://localhost:8888/actuator/health` | Config Server 헬스 체크 |

### 6.3 응답 예시

`GET http://localhost:8888/orders-service/dev` 응답:
```json
{
  "name": "orders-service",
  "profiles": ["dev"],
  "label": null,
  "version": "commit-hash",
  "state": null,
  "propertySources": [
    {
      "name": "https://github.com/username/config-repo/orders-service-dev.yml",
      "source": {
        "server.port": 8080,
        "spring.datasource.url": "jdbc:h2:mem:testdb",
        "app.config.message": "Orders Service - Development Environment"
      }
    }
  ]
}
```

---

## 7. Docker 배포

### 7.1 Dockerfile

```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY build/libs/encrypt-configure-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 7.2 Docker Compose

```yaml
version: '3.8'

services:
  config-server:
    build: ./encrypt-configure
    ports:
      - "8888:8888"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8888/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  orders-service:
    build: ./orders-service  # 클라이언트 서비스
    ports:
      - "8080:8080"
    depends_on:
      - config-server
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_CLOUD_CONFIG_URI=http://config-server:8888
```

### 7.3 빌드 및 실행

```bash
# JAR 파일 빌드
./gradlew bootJar

# Docker 이미지 빌드
docker build -t config-server .

# Docker Compose로 실행
docker-compose up -d
```

---

## 8. 트러블슈팅

### 8.1 일반적인 문제

| 문제 | 원인 | 해결방법 |
|------|------|----------|
| Config Server 시작 실패 | Git 리포지토리 접근 불가 | GitHub 리포지토리 URL 및 접근 권한 확인 |
| 클라이언트가 설정을 가져오지 못함 | Config Server 연결 실패 | Config Server가 실행 중인지 확인, URI 설정 확인 |
| 설정이 반영되지 않음 | 캐시 또는 프로필 문제 | `/actuator/refresh` 엔드포인트 호출 또는 재시작 |

### 8.2 헬스 체크

```bash
# Config Server 상태 확인
curl http://localhost:8888/actuator/health

# 특정 서비스 설정 확인
curl http://localhost:8888/orders-service/dev
```

---

## 9. 다음 단계

이 기본 설정이 안정적으로 동작하면 다음 기능들을 단계적으로 추가할 수 있습니다:

1. **보안 강화**: Basic Authentication 추가
2. **암호화**: 민감한 정보 암호화 기능
3. **웹훅**: Git 변경 시 자동 갱신
4. **모니터링**: Actuator 엔드포인트 확장
5. **고가용성**: 여러 인스턴스 운영

---

*끝*