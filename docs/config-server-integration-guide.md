# Config 서버 연동 가이드

## 개요
encrypt-file 모듈이 Spring Cloud Config 서버에서 p12-storepass를 가져오는 방법을 설명합니다.

## 설정 순서

### 1. 의존성 추가 (encrypt-file/build.gradle)
```gradle
dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:2023.0.1"
    }
}

dependencies {
    // Spring Cloud Config Client 추가
    implementation 'org.springframework.cloud:spring-cloud-starter-config'
    // 기존 의존성들...
}
```

### 2. application.yml 설정
```yaml
spring:
  application:
    name: encrypt-file
  config:
    import:
      - optional:configserver:http://localhost:9999
  cloud:
    config:
      uri: http://localhost:9999
      name: encrypt-file
      profile: prod
      fail-fast: false
```

### 3. Config 서버 설정 파일 (config-file/encrypt-file-prod.yml)
```yaml
encrypt-file:
  p12-storepass: MySecurePassword123!
```

### 4. Java 코드에서 사용
```java
@Value("${encrypt-file.p12-storepass}")
private String keystorePassword;
```

## 동작 흐름

1. Spring Boot 앱 시작
2. Config 서버에 GET /encrypt-file/prod 요청
3. encrypt-file-prod.yml에서 설정 로드
4. JasyptConfig에서 p12-storepass 값 사용

## 보안 강화

### 암호화된 값 사용
```yaml
encrypt-file:
  p12-storepass: '{cipher}AQA...' # Config 서버에서 암호화된 값
```

### 환경별 설정
- `config-file/encrypt-file-dev.yml` (개발 환경)
- `config-file/encrypt-file-prod.yml` (운영 환경)
- `config-file/encrypt-file-test.yml` (테스트 환경)
