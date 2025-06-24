# Spring Cloud Config Server 운영 플레이북

작성일: 2025-06-24

---

## 목차
1. 아키텍처 개요  
2. 준비 사항 및 버전 표준  
3. Config Repository 구조 예시  
4. Config Server 구축  
5. 클라이언트(Spring Boot 서비스) 연결  
6. Docker Compose 예제 (Server + Client)  
7. 운영·보안 베스트프랙티스  
8. 장애 대응 플레이북  

---

## 1. 아키텍처 개요
```
┌──────────────┐    HTTPS     ┌──────────────────┐
│   Git Repo   │◀────────────▶  Config Server    │
└──────────────┘               │  (Spring Boot)  │
                               └───────▲─────────┘
                                       │ HTTPS
                               ┌───────┴─────────┐
                               │  Client App(s)  │
                               │ (Spring Boot)   │
                               └──────────────────┘
```
- **Config Server**가 Git Repo에서 YAML/Properties 파일을 읽어 `/{{application}}/{{profile}}` REST API로 노출  
- 각 **Spring Boot 클라이언트**는 부팅 시 서버로부터 설정을 주입받음  

---

## 2. 준비 사항 및 버전 표준

| 항목 | 권장 버전(2025 Q2 기준) |
|------|------------------------|
| JDK  | 21 (LTS) |
| Spring Boot | 3.3.x |
| Spring Cloud | 2025.0.x (코드명 *Wembley*) |
| Gradle | 8.5+ |

> **네임스페이스**: 최신 Spring Boot 3.2+에서는 *`bootstrap.yml`* 대신 `spring.config.import=configserver:` 구문 사용.

---

## 3. Config Repository 구조 예시

```text
config-repo/
├── application.yml         # 공통 기본값
├── application-dev.yml     # 공통 dev 프로필
├── orders.yml              # 서비스별 기본값
├── orders-prod.yml         # 서비스별 prod
└── inventory.yml
```

Sample `orders.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://db/orders
    username: orders_user
```

---

## 4. Config Server 구축

### 4.1 build.gradle
```groovy
plugins {{
    id 'org.springframework.boot' version '3.3.1'
    id 'io.spring.dependency-management' version '1.1.4'
    id 'java'
}}

group = 'com.example.config'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '21'

repositories {{
    mavenCentral()
}}

ext {{
    springCloudVersion = "2025.0.0"
}}

dependencies {{
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.cloud:spring-cloud-config-server'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}}

dependencyManagement {{
    imports {{
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${{springCloudVersion}}"
    }}
}}

tasks.named('test') {{
    useJUnitPlatform()
}}
```

### 4.2 application.yml (Config Server)
```yaml
server:
  port: 8888
  ssl:
    enabled: true
    key-store: classpath:config-server-keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
    key-alias: config-server

spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/config-repo.git
          default-label: main
          username: git-reader
          password: ${'{'}GIT_TOKEN{'}'}          # 환경변수 주입
        encrypt:
          enabled: true

management:
  endpoints:
    web:
      exposure:
        include: "health,info,env"
```

> **키스토어 생성:**  
> ```bash
> keytool -genkeypair -alias config-server >   -keyalg RSA -keysize 4096 >   -keystore config-server-keystore.p12 >   -storetype PKCS12 -validity 3650 >   -dname "CN=config.example.com, OU=IT, O=Example, L=Seoul, C=KR" >   -storepass changeit -keypass changeit
> ```

### 4.3 실행
```bash
./gradlew bootJar
java -jar build/libs/config-server-0.0.1-SNAPSHOT.jar   --GIT_TOKEN=ghp_xxx
```

---

## 5. 클라이언트(Spring Boot 서비스) 연결

### 5.1 build.gradle (공통)
```groovy
plugins {{
    id 'org.springframework.boot' version '3.3.1'
    id 'io.spring.dependency-management' version '1.1.4'
    id 'java'
}}

group = 'com.example'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '21'

repositories {{ mavenCentral() }}

ext.springCloudVersion = "2025.0.0"

dependencies {{
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.cloud:spring-cloud-starter-config'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}}

dependencyManagement {{
    imports {{
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${{springCloudVersion}}"
    }}
}}
```

### 5.2 `application.yml` (클라이언트)
```yaml
spring:
  application:
    name: orders            # ==> config repo의 orders.yml 매칭
  profiles:
    active: prod
  config:
    import: "configserver:https://config.example.com:8888"

# SSL Trust 설정 (CA가 신뢰될 경우 생략 가능)
server:
  ssl:
    enabled: false
```

> **실행 예시 (JAR)**  
> ```bash
> java -jar orders-service.jar >   --spring.cloud.config.username=config-client >   --spring.cloud.config.password=$TOKEN >   --javax.net.ssl.trustStore=truststore.jks >   --javax.net.ssl.trustStorePassword=changeit
> ```

### 5.3 부트스트랩 확인
```bash
curl -k https://config.example.com:8888/orders/prod
```

---

## 6. Docker Compose 예제

```yaml
version: "3.9"
services:
  config-server:
    image: ghcr.io/your-org/config-server:0.0.1
    container_name: config-server
    environment:
      - GIT_TOKEN=${'{'}GIT_TOKEN{'}'}
    ports:
      - "8888:8888"
    volumes:
      - ./certs/config-server-keystore.p12:/app/config-server-keystore.p12:ro

  orders-service:
    image: ghcr.io/your-org/orders-service:0.0.1
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_CONFIG_IMPORT=configserver:https://config-server:8888
      - SPRING_CLOUD_CONFIG_USERNAME=config-client
      - SPRING_CLOUD_CONFIG_PASSWORD=${'{'}CONFIG_PASSWORD{'}'}
      - JVM_OPTS=-Djavax.net.ssl.trustStore=/app/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit
    depends_on:
      - config-server
    ports:
      - "8080:8080"
```

---

## 7. 운영·보안 베스트프랙티스
1. **최소 권한 Git 토큰**: read 권한만 허용  
2. **/encrypt / /decrypt 활성화 시 IP 화이트리스트**  
3. **TLS**: 자체 CA → 클라이언트 truststore 배포 자동화  
4. **Config Server Replica 2+**, Health Check `/actuator/health`  
5. **Log Masking**: `SensitiveDataProvider` 활용하여 비밀 출력 차단  

---

## 8. 장애 대응 플레이북

| 증상 | 점검 단계 |
|------|-----------|
| Config Server 실행 오류 | `git clone` 실패 로그 → PAT 만료 여부 |
| 클라이언트 설정 미수신 | `spring.config.import` URL 오타·SSL Handshake |
| 설정 새 버전 적용 안 됨 | `/actuator/refresh` POST 호출 or 서비스 재시작 |
| 5xx 반복 | Git Repo 접근 지연 → `spring.cloud.config.server.git.refresh-rate` 조정 |

---

*(끝)*
